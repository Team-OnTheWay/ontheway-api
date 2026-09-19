package com.ontheway.service;

import com.ontheway.dto.request.*;
import com.ontheway.dto.response.*;
import com.ontheway.entity.*;
import com.ontheway.enums.DeliveryStatus;
import com.ontheway.global.exception.BusinessException;
import com.ontheway.global.exception.ErrorCode;
import com.ontheway.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final UserRepository userRepository;
    private final DeliveryRepository deliveryRepository;
    private final RequestRepository requestRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final FailedAndCancelledRepository failedAndCancelledRepository;
    private final ImageRepository imageRepository;

    public DeliveryListResponseDto list(DeliveryListRequestDto deliveryListRequestDto) {
        Page<Delivery> deliveries = deliveryRepository.findAllBySearch(deliveryListRequestDto.getStartAddress(), deliveryListRequestDto.getEndAddress(), deliveryListRequestDto.getRating(), deliveryListRequestDto.getHopePrice(), PageRequest.of(deliveryListRequestDto.getPage(), deliveryListRequestDto.getSize()));

        List<DeliveryListResponseDto.DeliveryList> deliveryList =
                deliveries.map(delivery -> DeliveryListResponseDto.DeliveryList.builder()
                        .deliveryId(delivery.getId())
                        .startAddress(delivery.getDeparture().getAddress())
                        .endAddress(delivery.getDestination().getAddress())
                        .deliveryDate(delivery.getDeliveryDate().atTime(delivery.getPlannedStartTime()))
                        .hopePrice(delivery.getDesiredPrice())
                        .requestCount(requestRepository.countByDelivery(delivery))
                        .build()).toList();

        return DeliveryListResponseDto.builder().deliveryList(deliveryList).build();
    }

    public DeliveryDetailResponseDto detail(Long userId, DeliveryDetailRequestDto deliveryDetailRequestDto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        Delivery delivery = deliveryRepository.findById(deliveryDetailRequestDto.getDeliveryId()).orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        List<DeliveryOrder> deliveryOrders = deliveryOrderRepository.findByRequest_Delivery(delivery);
        FailedAndCancelled failedAndCancelled = failedAndCancelledRepository.findByOrder_Request_Delivery(delivery).getFirst();
        Image image = imageRepository.findByOrderId(deliveryOrders.getFirst().getId()).orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        return DeliveryDetailResponseDto.builder()
                .deliveryId(delivery.getId())
                .startAddress(delivery.getDeparture().getAddress())
                .endAddress(delivery.getDestination().getAddress())
                .deliveryDate(delivery.getDeliveryDate().atTime(delivery.getPlannedStartTime()))
                .hopePrice(delivery.getDesiredPrice())
                .deliveryDate(delivery.getDeliveryDate().atTime(delivery.getPlannedStartTime()))
                .addInfo(delivery.getAdditionalInfo())
                .createdAt(delivery.getCreatedAt())
                .estimatedDeliveryTime(LocalDateTime.of(delivery.getDeliveryDate(), delivery.getPlannedEndTime()) )
                .currentDeliveryStatus(deliveryOrders.isEmpty() ? DeliveryStatus.DELIVERY_WAITING : deliveryOrders.getFirst().getStatus())
                .userImage(delivery.getAuthor().getProfileImageUrl())
                .userName(delivery.getAuthor().getName())
                .requesterInfo(DeliveryDetailResponseDto.RequesterInfo.builder()
                        .paymentType(deliveryOrders.getFirst().getProduct().getPaymentType())
                        .desiredDeliveryTime(deliveryOrders.getFirst().getProduct().getDesiredArrivalTime())
                        .userImage(deliveryOrders.getFirst().getProduct().getAuthor().getProfileImageUrl())
                        .userName(deliveryOrders.getFirst().getProduct().getAuthor().getName())
                        .productDeliveryAddress(deliveryOrders.getFirst().getProduct().getPickup().getAddress())
                        .deliveryDestination(deliveryOrders.getFirst().getProduct().getDestination().getAddress())
                        .productInfo(deliveryOrders.getFirst().getProduct().getItemInfo())
                        .deliveryFee(deliveryOrders.getFirst().getDeliveryFee())
                        .receivingTime(deliveryOrders.getFirst().getProduct().getPickupTime())
                        .build())
                .deliveryFail(failedAndCancelled.getOrder().getStatus() == DeliveryStatus.FAILED ? DeliveryDetailResponseDto.DeliveryFail.builder().failReason(failedAndCancelled.getReason()).build() : null)
                .deliveryCancel(failedAndCancelled.getOrder().getStatus() == DeliveryStatus.CANCELED ? DeliveryDetailResponseDto.DeliveryCancel.builder().cancelReason(failedAndCancelled.getReason()).build() : null)
                .deliverySuccessCheck(DeliveryDetailResponseDto.DeliverySuccessCheck.builder().deliverySuccessCheckImage(image.getFileUrl()).build())
                .deliveryStatusHistory(deliveryOrders.stream().map(
                        item -> DeliveryDetailResponseDto.DeliveryStatusHistory.builder()
                                .deliveryStatus(item.getStatus()).deliveryDate(item.getCreatedAt()).build())
                        .toList())
                .build();
    }

    @Transactional
    public DeliverySaveResponseDto create(Long userId, DeliverySaveRequestDto deliverySaveRequestDto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        deliveryRepository.save(Delivery.builder().author(user)
                .content(Delivery.Content.builder()
                        .deliveryDate(deliverySaveRequestDto.getDeliveryDate().toLocalDate())
                        .destination(Location.builder().address(deliverySaveRequestDto.getEndAddress()).build())
                        .additionalInfo(deliverySaveRequestDto.getAddInfo())
                        .departure(Location.builder().address(deliverySaveRequestDto.getStartAddress()).build())
                        .plannedEndTime(deliverySaveRequestDto.getDeliveryDate().toLocalTime())
                        .plannedStartTime(deliverySaveRequestDto.getDeliveryDate().toLocalTime())
                        .desiredPrice(deliverySaveRequestDto.getHopePrice())
                        .build())
                .build());

        return DeliverySaveResponseDto.builder().build();
    }

    @Transactional
    public DeliveryUpdateResponseDto update(Long userId, DeliveryUpdateRequestDto deliveryUpdateRequestDto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Delivery delivery = deliveryRepository.findById(deliveryUpdateRequestDto.getDeliveryId()).orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        if(!user.checkIsOwner(delivery.getAuthor().getId())) throw new BusinessException(ErrorCode.DELIVERY_BOARD_NOT_OWNED);

        delivery.update(Delivery.Content.builder()
                .deliveryDate(deliveryUpdateRequestDto.getDeliveryDate().toLocalDate())
                .destination(Location.builder().address(deliveryUpdateRequestDto.getEndAddress()).build())
                .additionalInfo(deliveryUpdateRequestDto.getAddInfo())
                .departure(Location.builder().address(deliveryUpdateRequestDto.getStartAddress()).build())
                .plannedEndTime(deliveryUpdateRequestDto.getDeliveryDate().toLocalTime())
                .plannedStartTime(deliveryUpdateRequestDto.getDeliveryDate().toLocalTime())
                .desiredPrice(deliveryUpdateRequestDto.getHopePrice())
                .build());

        return DeliveryUpdateResponseDto.builder().build();
    }

    @Transactional
    public DeliveryDeleteResponseDto delete(Long userId, Long id) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Delivery delivery = deliveryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        if(!user.checkIsOwner(delivery.getAuthor().getId())) throw new BusinessException(ErrorCode.DELIVERY_BOARD_NOT_OWNED);
        delivery.delete(LocalDateTime.now());
        return DeliveryDeleteResponseDto.builder().updatedAt(LocalDateTime.now()).build();
    }

    public MyBoardDeliveryListResponseDto myList(Long userId, MyBoardDeliveryListRequestDto myBoardDeliveryListRequestDto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<MyBoardDeliveryListResponseDto.DeliveryList> deliveryList = deliveryRepository.findAllBySearchAuthorAndDeletedAt(user
                        , myBoardDeliveryListRequestDto.getStartAddress(), myBoardDeliveryListRequestDto.getEndAddress()
                        , myBoardDeliveryListRequestDto.getRating(), myBoardDeliveryListRequestDto.getHopePrice()
                        , PageRequest.of(myBoardDeliveryListRequestDto.getPage(), myBoardDeliveryListRequestDto.getSize()))
                .stream().map(item -> MyBoardDeliveryListResponseDto.DeliveryList.builder()
                        .deliveryId(item.getId())
                        .startAddress(item.getDeparture().getAddress())
                        .endAddress(item.getDestination().getAddress())
                        .hopePrice(item.getDesiredPrice())
                        .requestCount(requestRepository.countByDelivery(item))
                        .deliveryDate(LocalDateTime.of(item.getDeliveryDate(), item.getPlannedStartTime())).build()).toList();
        return MyBoardDeliveryListResponseDto.builder().deliveryList(deliveryList).build();
    }
}
