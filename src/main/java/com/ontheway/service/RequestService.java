package com.ontheway.service;

import com.ontheway.dto.request.RequestSaveRequestDto;
import com.ontheway.dto.response.RequestDeliveryListResponseDto;
import com.ontheway.dto.response.RequestRejectResponseDto;
import com.ontheway.dto.response.RequestSaveResponseDto;
import com.ontheway.entity.Delivery;
import com.ontheway.entity.Product;
import com.ontheway.entity.Request;
import com.ontheway.entity.User;
import com.ontheway.global.exception.BusinessException;
import com.ontheway.global.exception.ErrorCode;
import com.ontheway.repository.DeliveryOrderRepository;
import com.ontheway.repository.DeliveryRepository;
import com.ontheway.repository.ProductRepository;
import com.ontheway.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RequestRepository requestRepository;
    private final DeliveryRepository deliveryRepository;
    private final ProductRepository productRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;

    @Transactional
    public RequestSaveResponseDto register(Long requesterId, RequestSaveRequestDto dto) {
        Delivery delivery = deliveryRepository.findByIdAndDeletedAtIsNull(dto.getDeliveryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));

        if (deliveryOrderRepository.existsByRequest_Delivery_Id(dto.getDeliveryId())) {
            throw new BusinessException(ErrorCode.DELIVERY_CLOSED);
        }

        Product product = productRepository.findByIdAndDeletedAtIsNull(dto.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getAuthor().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (deliveryOrderRepository.existsByRequest_Product_Id(product.getId())) {
            throw new BusinessException(ErrorCode.PRODUCT_ALREADY_MATCHED);
        }

        if (requestRepository.existsByDeliveryIdAndProductId(dto.getDeliveryId(), product.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }

        Request request = Request.builder()
                .delivery(delivery)
                .product(product)
                .build();
        requestRepository.save(request);

        return RequestSaveResponseDto.builder()
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public RequestRejectResponseDto reject(Long delivererId, Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));

        Delivery delivery = deliveryRepository.findByIdForUpdate(request.getDelivery().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));

        if (!delivery.getAuthor().getId().equals(delivererId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!requestRepository.existsByIdAndRejectedAtIsNull(requestId)) {
            throw new BusinessException(ErrorCode.REQUEST_CLOSED);
        }
        if (deliveryOrderRepository.findByRequestId(requestId).isPresent()) {
            throw new BusinessException(ErrorCode.REQUEST_CLOSED);
        }

        request.reject(LocalDateTime.now());

        return RequestRejectResponseDto.builder()
                .rejectDate(LocalDateTime.now())
                .build();
    }

    public RequestDeliveryListResponseDto getDeliveryRequestList(Long deliveryId, Long viewerId) {
        Delivery delivery = deliveryRepository.findByIdAndDeletedAtIsNull(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));

        List<Request> requests;
        if (delivery.getAuthor().getId().equals(viewerId)) {
            requests = requestRepository.findAllByDeliveryIdWithProductAndAuthor(deliveryId);
        } else {
            requests = requestRepository.findMyRequestsOnDelivery(deliveryId, viewerId);
            if (requests.isEmpty()) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        List<RequestDeliveryListResponseDto.RequestDelivery> items = requests.stream()
                .map(this::toRequestDeliveryItem)
                .toList();

        return RequestDeliveryListResponseDto.builder()
                .requestDeliveryList(items)
                .build();
    }

    private RequestDeliveryListResponseDto.RequestDelivery toRequestDeliveryItem(Request request) {
        Product product = request.getProduct();
        User requester = product.getAuthor();

        return RequestDeliveryListResponseDto.RequestDelivery.builder()
                .requestId(request.getId())
                .requesterName(requester.getName())
                .requesterNickname(requester.getNickname())
                .requesterImage(requester.getProfileImageUrl())
                .productDeliveryAddress(product.getPickup().getAddress())
                .deliveryDestination(product.getDestination().getAddress())
                .itemName(product.getItemName())
                .itemInfo(product.getItemInfo())
                .deliveryFee(product.getDeliveryFee())
                .receivingTime(product.getPickupTime().format(DATE_TIME_FORMATTER))
                .desiredDeliveryTime(product.getDesiredArrivalTime().format(DATE_TIME_FORMATTER))
                .paymentType(product.getPaymentType().name())
                .createdAt(request.getCreatedAt())
                .deliveryStatus(request.getStatus())
                .build();
    }

}
