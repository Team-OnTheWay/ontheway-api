package com.ontheway.service;

import com.ontheway.dto.request.ProcessRequestDto;
import com.ontheway.dto.response.ProcessResponseDto;
import com.ontheway.entity.Delivery;
import com.ontheway.entity.DeliveryOrder;
import com.ontheway.entity.FailedAndCancelled;
import com.ontheway.entity.Image;
import com.ontheway.entity.Product;
import com.ontheway.entity.Request;
import com.ontheway.entity.User;
import com.ontheway.enums.DeliveryStatus;
import com.ontheway.global.exception.BusinessException;
import com.ontheway.global.exception.ErrorCode;
import com.ontheway.infra.storage.R2FileUploader;
import com.ontheway.infra.storage.R2FileUploader.StoredFile;
import com.ontheway.repository.DeliveryOrderRepository;
import com.ontheway.repository.DeliveryRepository;
import com.ontheway.repository.FailedAndCancelledRepository;
import com.ontheway.repository.ImageRepository;
import com.ontheway.repository.ProductRepository;
import com.ontheway.repository.RequestRepository;
import com.ontheway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.ontheway.global.exception.Preconditions.require;

/**
 * 배송 진행 처리. {@code POST /order} 하나가 수락부터 완료 확인까지 여섯 가지 전이를 받는다.
 *
 * 요청 DTO 에 동작을 가리키는 필드가 없어서, 무엇을 하려는지는 입력에서 추론한다.
 *
 *   requestId 있음      -> 수락
 *   cancelReason 있음   -> 취소
 *   failReason 있음     -> 배송 실패
 *   image 있음          -> 배송완료 확인요청
 *   아무것도 없음        -> 현재 상태가 정한다(픽업중 = 픽업 완료, 확인요청 = 완료 확인)
 *
 * 수락에만 requestId 를 쓰는 게 이 규칙의 핵심이다. 수락 요청이 재전송(더블클릭, 재시도)되면 상태는
 * 이미 픽업중인데, requestId 로 수락을 못 박아 두지 않으면 같은 본문이 '픽업 완료'로 읽혀서 취소할 수
 * 있는 시점을 넘겨 버린다. 이 규칙이면 재전송은 항상 409 로 끝난다.
 *
 * "있음"은 null 여부로 본다. 사유를 빈 문자열로 보낸 취소가 '입력 없음'으로 읽혀 '픽업 완료'가 되는
 * 일을 막으려는 것이고, 빈 사유는 값 검증에서 400 이 된다.
 *
 * 증빙 사진은 트랜잭션 밖에서 먼저 올린다. 업로드 동안 경로 잠금과 DB 커넥션을 쥐고 있지 않으려는 것이다.
 * 그래서 권한과 상태는 업로드 뒤에 확인하고, 확인이나 저장이 실패하면 올린 파일을 지운다.
 *
 * 전이해도 되는 상태인지, 권한이 있는지, 증빙 사진이 있는지는 엔티티가 아니라 여기서 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /**
     * 증빙 사진으로 받는 형식과 저장할 확장자.
     * 임의로 정한 가정: 
     * 모든 브라우저가 표시할 수 있는 형식만 둔다(HEIC 는 크롬에서 안 보인다). SVG 는 스크립트를 담을 수 있어 빠짐.
     */
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    /** 수락은 requestId 로 갈리므로 여기 없다. */
    private enum Intent {
        PICK_UP, CANCEL, FAIL, REQUEST_COMPLETION, CONFIRM
    }

    /** 트랜잭션 밖에서 올려 둔 증빙 사진. 트랜잭션 안에서 배송이 정해지면 {@link Image} 로 만든다. */
    private record Proof(StoredFile stored, String originalName, long size, String extension) {

        Image toImage(DeliveryOrder order) {
            return Image.builder()
                    .order(order)
                    .fileUrl(stored.url())
                    .originalName(originalName)
                    .storedName(stored.key())
                    .fileSize(size)
                    .extension(extension)
                    .build();
        }
    }

    private final UserRepository userRepository;
    private final DeliveryRepository deliveryRepository;
    private final ProductRepository productRepository;
    private final RequestRepository requestRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final FailedAndCancelledRepository failedAndCancelledRepository;
    private final ImageRepository imageRepository;
    private final R2FileUploader r2FileUploader;
    private final TransactionTemplate transactionTemplate;

    public ProcessResponseDto process(Long userId, ProcessRequestDto dto, MultipartFile image) {
        require(dto != null && dto.getDeliveryId() != null, ErrorCode.INVALID_INPUT);
        // 종류가 다른 입력이 섞이면(취소와 증빙을 같이 보내는 등) 무엇을 하려는 건지 알 수 없다.
        // requestId 도 입력 하나로 세므로 수락은 다른 입력과 함께 올 수 없다
        long inputs = Stream.of(dto.getRequestId(), dto.getCancelReason(), dto.getFailReason(),
                        hasImage(image) ? image : null)
                .filter(Objects::nonNull)
                .count();
        require(inputs <= 1, ErrorCode.INVALID_INPUT);

        if (!hasImage(image)) {
            return transactionTemplate.execute(status -> processLocked(userId, dto, null));
        }

        Proof proof = upload(image, dto.getDeliveryId());
        try {
            return transactionTemplate.execute(status -> processLocked(userId, dto, proof));
        } catch (RuntimeException e) {
            // 권한·상태 확인이나 저장이 실패했다. 올린 파일이 R2 에 고아로 남지 않게 지운다
            deleteQuietly(proof.stored());
            throw e;
        }
    }

    /** 경로를 잠근 채 한 트랜잭션으로 처리한다. */
    private ProcessResponseDto processLocked(Long userId, ProcessRequestDto dto, Proof proof) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 동시 수락, 동시 취소처럼 같은 경로에 겹쳐 들어온 요청을 경로 단위로 한 줄로 세운다(비관적 락)
        Delivery delivery = deliveryRepository.findByIdForUpdate(dto.getDeliveryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));

        // 이 처리의 기준 시각. 상태별 시각과 연쇄 종료 시각이 모두 같은 값을 쓴다
        LocalDateTime now = LocalDateTime.now();
        DeliveryStatus result = dto.getRequestId() != null
                ? accept(user, delivery, dto.getRequestId(), now)
                : transition(user, delivery, dto, proof, now);

        return ProcessResponseDto.builder()
                .createdAt(now)
                .deliveryStatus(result)
                .build();
    }

    // --- 수락 ---

    private DeliveryStatus accept(User user, Delivery delivery, Long requestId, LocalDateTime now) {
        require(user.checkIsOwner(delivery.getAuthor().getId()), ErrorCode.FORBIDDEN);

        Request request = requestRepository.findByIdWithOrder(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        Long deliveryId = delivery.getId();
        Long productId = request.getProduct().getId();
        // 다른 경로에 들어온 요청을 이 경로 ID 로 수락하려는 경우
        require(deliveryId.equals(request.getDelivery().getId()), ErrorCode.INVALID_INPUT);
        require(request.getRejectedAt() == null, ErrorCode.REQUEST_CLOSED);

        // 잠금 순서는 항상 경로 -> 물품이다(데드락 방지). 경로는 processLocked 에서 이미 잠갔다
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_CLOSED));
        // 요청을 읽은 뒤 물품 잠금을 얻기까지 사이에 물품 삭제가 커밋돼 요청이 닫혔을 수 있다.
        // 읽어 둔 request 는 그 변경을 모르므로 SQL 로 다시 묻는다
        require(requestRepository.existsByIdAndRejectedAtIsNull(requestId), ErrorCode.REQUEST_CLOSED);
        require(delivery.getDeletedAt() == null && product.getDeletedAt() == null, ErrorCode.REQUEST_CLOSED);

        // 경로당 1건, 물품당 1건. DB 제약이 없어서 잠금을 쥔 채 직접 확인
        // 이미 수락된 요청의 재전송도 경로 쪽 확인에서 409 로 끝난다
        require(!deliveryOrderRepository.existsByRequest_Delivery_Id(deliveryId), ErrorCode.DELIVERY_ALREADY_MATCHED);
        require(!deliveryOrderRepository.existsByRequest_Product_Id(productId), ErrorCode.PRODUCT_ALREADY_MATCHED);

        // 배송 주문 생성 및 저장, 상태는 픽업 중으로 변경됨
        DeliveryOrder order = DeliveryOrder.accept(request);
        deliveryOrderRepository.save(order);
        DeliveryStatus status = order.getStatus();

        // 경쟁 요청들 일괄 거절
        // 수락 발생 시 같은 물품 게시글에 대해 다른 경로에 들어간 물품 요청을 자동으로 거절시킴
        requestRepository.rejectSiblingsByDelivery(deliveryId, requestId, now);
        requestRepository.rejectSiblingsByProduct(productId, requestId, now);
        return status;
    }

    // --- 수락 이후의 전이 ---

    private DeliveryStatus transition(User user, Delivery delivery, ProcessRequestDto dto,
                                      Proof proof, LocalDateTime now) {
        // 수락된 배송이 없으면 전이할 대상도 없다
        DeliveryOrder order = deliveryOrderRepository.findByDeliveryIdWithParties(delivery.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ORDER_STATE));

        Request request = order.getRequest();
        boolean isDeliverer = user.checkIsOwner(request.getDeliverer().getId());
        boolean isRequester = user.checkIsOwner(request.getRequester().getId());
        require(isDeliverer || isRequester, ErrorCode.FORBIDDEN);

        switch (resolveIntent(dto, proof, order.getStatus())) {
            case PICK_UP -> pickUp(order, isDeliverer, now);
            case CANCEL -> cancel(order, user, dto.getCancelReason());
            case FAIL -> fail(order, user, isDeliverer, dto.getFailReason());
            case REQUEST_COMPLETION -> requestCompletion(order, isDeliverer, proof, now);
            case CONFIRM -> confirm(order, isRequester, now);
        }
        return order.getStatus();
    }

    /** 보낸 입력과 현재 상태로 무엇을 하려는지 정한다. */
    private static Intent resolveIntent(ProcessRequestDto dto, Proof proof, DeliveryStatus status) {
        if (dto.getCancelReason() != null) {
            return Intent.CANCEL;
        }
        if (dto.getFailReason() != null) {
            return Intent.FAIL;
        }
        if (proof != null) {
            return Intent.REQUEST_COMPLETION;
        }
        // 입력이 없으면 상태가 동작을 정한다
        return switch (status) {
            case PICKING_UP -> Intent.PICK_UP;
            // 사진이 없는 확인요청이다. 사진 검사에서 PROOF_IMAGE_REQUIRED 로 막힌다
            case DELIVERING -> Intent.REQUEST_COMPLETION;
            case COMPLETION_REQUESTED -> Intent.CONFIRM;
            // 배송대기중은 예정 시각에 자동으로 넘어갈 뿐이고, 나머지는 끝난 거래다
            default -> throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        };
    }

    /**
     * 전달자가 물건을 받았다. 이 뒤로는 취소할 수 없다.
     * 픽업중일 때만 이 동작으로 읽히므로({@link #resolveIntent}) 상태는 따로 보지 않는다.
     */
    private void pickUp(DeliveryOrder order, boolean isDeliverer, LocalDateTime now) {
        require(isDeliverer, ErrorCode.FORBIDDEN);
        order.pickUp(now);
        // 픽업이 예정 시각을 이미 지나 이뤄졌다면 배송대기중에 머물지 않고 곧장 배송중으로 넘긴다.
        // 그 전에 픽업이 끝나 대기 중인 건은 스케줄러(OrderScheduler)가 나중에 넘긴다.
        if (order.getDelivery().isDue(now)) {
            order.startDelivery(now);
        }
    }

    /** 취소는 당사자 양쪽이 할 수 있다. 당사자인지는 호출 전에 확인했다. */
    private void cancel(DeliveryOrder order, User user, String reason) {
        requireStatus(order, DeliveryStatus.PICKING_UP);
        String validReason = requireReason(reason);
        order.cancel();
        failedAndCancelledRepository.save(FailedAndCancelled.canceledBy(order, user, validReason));
    }

    /** 실패는 전달자만, 픽업 완료 뒤(취소 불가)부터 확인요청 전까지만 할 수 있다. */
    private void fail(DeliveryOrder order, User user, boolean isDeliverer, String reason) {
        require(isDeliverer, ErrorCode.FORBIDDEN);
        requireStatus(order, DeliveryStatus.DELIVERY_WAITING, DeliveryStatus.DELIVERING);
        String validReason = requireReason(reason);
        order.fail();
        failedAndCancelledRepository.save(FailedAndCancelled.failedBy(order, user, validReason));
    }

    private void requestCompletion(DeliveryOrder order, boolean isDeliverer, Proof proof, LocalDateTime now) {
        require(isDeliverer, ErrorCode.FORBIDDEN);
        requireStatus(order, DeliveryStatus.DELIVERING);
        // 증빙 사진이 없으면 확인요청으로 넘어가지 않는다
        require(proof != null, ErrorCode.PROOF_IMAGE_REQUIRED);

        imageRepository.save(proof.toImage(order));
        order.requestCompletion(now);
    }

    /**
     * 의뢰자가 받았다고 확인한다. 72시간이 지나면 스케줄러가 대신 넘긴다.
     * 확인요청 상태일 때만 이 동작으로 읽히므로({@link #resolveIntent}) 상태는 따로 보지 않는다.
     */
    private void confirm(DeliveryOrder order, boolean isRequester, LocalDateTime now) {
        require(isRequester, ErrorCode.FORBIDDEN);
        order.complete(now);
    }

    // --- 증빙 사진 ---

    /** 형식을 확인하고 R2 에 올린다. 트랜잭션 밖에서 부른다. */
    private Proof upload(MultipartFile image, Long deliveryId) {
        // 용량 상한은 멀티파트 파싱(spring.servlet.multipart.max-file-size)이 컨트롤러 앞에서 막는다
        String extension = IMAGE_EXTENSIONS.get(mediaTypeOf(image));
        require(extension != null, ErrorCode.INVALID_IMAGE);

        StoredFile stored;
        try {
            stored = r2FileUploader.upload(image, extension);
        } catch (IOException | SdkException e) {
            // BusinessException 로그에는 원인이 안 남아서 여기서 남긴다
            log.error("증빙 사진 업로드 실패. deliveryId={}", deliveryId, e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return new Proof(stored, originalNameOf(image), image.getSize(), extension);
    }

    /** 트랜잭션이 실패했을 때 올려 둔 파일을 지운다. 지우지 못해도 원래 실패를 가리지 않도록 로그만 남긴다. */
    private void deleteQuietly(StoredFile stored) {
        try {
            r2FileUploader.delete(stored.key());
        } catch (RuntimeException e) {
            log.warn("증빙 사진 삭제 실패. R2 에 고아 파일로 남는다. key={}", stored.key(), e);
        }
    }

    /** 파라미터(; q=1 등)를 뗀 소문자 media type. */
    private static String mediaTypeOf(MultipartFile image) {
        String contentType = image.getContentType();
        return contentType == null ? "" : contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
    }

    /** 사용자가 올린 파일명. 경로가 섞여 오면 이름만 남기고, 비어 있으면 임의의 이름을 붙인다. */
    private static String originalNameOf(MultipartFile image) {
        String name = image.getOriginalFilename();
        if (name != null) {
            // 브라우저에 따라 경로가 통째로 올 때가 있다
            name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        }
        if (name == null || name.isBlank()) {
            return "proof";
        }
        return name.length() <= Image.ORIGINAL_NAME_MAX_LENGTH ? name : name.substring(0, Image.ORIGINAL_NAME_MAX_LENGTH);
    }

    // --- 공통 ---

    /** 파일을 고르지 않고 보낸 빈 파트는 사진이 아니다. */
    private static boolean hasImage(MultipartFile image) {
        return image != null && !image.isEmpty();
    }

    /** 취소·실패 사유. 앞뒤 공백을 뗀 값을 돌려주고, 비었거나 컬럼 길이를 넘으면 400. */
    private static String requireReason(String reason) {
        String stripped = reason == null ? "" : reason.strip();
        require(!stripped.isEmpty() && stripped.length() <= FailedAndCancelled.REASON_MAX_LENGTH,
                ErrorCode.INVALID_INPUT);
        return stripped;
    }

    private static void requireStatus(DeliveryOrder order, DeliveryStatus... allowed) {
        require(List.of(allowed).contains(order.getStatus()), ErrorCode.INVALID_ORDER_STATE);
    }
}
