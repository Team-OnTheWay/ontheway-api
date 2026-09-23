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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        Page<Delivery> deliveries = deliveryRepository.findAllBySearch(
                deliveryListRequestDto.getStartAddress(),
                deliveryListRequestDto.getEndAddress(),
                deliveryListRequestDto.getRating(),
                deliveryListRequestDto.getHopePrice(),
                PageRequest.of(deliveryListRequestDto.getPage(), deliveryListRequestDto.getSize())
        );

        List<Delivery> deliveryList = deliveries.getContent();

        if (deliveryList.isEmpty()) {
            return DeliveryListResponseDto.builder().deliveryList(Collections.emptyList()).build();
        }

        List<DeliveryOrder> existingOrders = deliveryOrderRepository.findByRequest_DeliveryIn(deliveryList);
        Set<Long> excludedDeliveryIds = existingOrders.stream()
                .map(order -> order.getRequest().getDelivery().getId()).collect(Collectors.toSet());

        List<Delivery> filteredDeliveries = deliveryList.stream()
                .filter(delivery -> !excludedDeliveryIds.contains(delivery.getId())).toList();

        List<DeliveryListResponseDto.DeliveryList> responseList = filteredDeliveries.stream()
                .map(delivery -> DeliveryListResponseDto.DeliveryList.builder()
                        .deliveryId(delivery.getId())
                        .startAddress(delivery.getDeparture().getAddress())
                        .endAddress(delivery.getDestination().getAddress())
                        .deliveryDate(delivery.getDeliveryDate().atTime(delivery.getPlannedStartTime()))
                        .hopePrice(delivery.getDesiredPrice())
                        .requestCount(requestRepository.countByDelivery(delivery))
                        .build())
                .toList();

        return DeliveryListResponseDto.builder().deliveryList(responseList).build();
    }

    public DeliveryDetailResponseDto detail(Long userId, DeliveryDetailRequestDto deliveryDetailRequestDto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

        Delivery delivery = deliveryRepository.findById(deliveryDetailRequestDto.getDeliveryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        List<DeliveryOrder> deliveryOrders = deliveryOrderRepository.findByRequest_Delivery(delivery);
        List<FailedAndCancelled> list = failedAndCancelledRepository.findByOrder_Request_Delivery(delivery);
        FailedAndCancelled failedAndCancelled = list.isEmpty() ? null : list.getFirst();
        boolean hasOrders = !deliveryOrders.isEmpty();
        DeliveryOrder firstOrder = hasOrders ? deliveryOrders.getFirst() : null;
        Image image = null;
        if (hasOrders) {
            image = imageRepository.findByOrderId(firstOrder.getId()).orElse(null);
        }

        return DeliveryDetailResponseDto.builder()
                .deliveryId(delivery.getId())
                .startAddress(delivery.getDeparture().getAddress())
                .endAddress(delivery.getDestination().getAddress())
                .deliveryDate(delivery.getDeliveryDate().atTime(delivery.getPlannedStartTime()))
                .hopePrice(delivery.getDesiredPrice())
                .addInfo(delivery.getAdditionalInfo())
                .createdAt(delivery.getCreatedAt())
                .estimatedDeliveryTime(LocalDateTime.of(delivery.getDeliveryDate(), delivery.getPlannedEndTime()))
                .currentDeliveryStatus(!hasOrders ? DeliveryStatus.DELIVERY_WAITING : firstOrder.getStatus())
                .userImage(delivery.getAuthor() != null ? delivery.getAuthor().getProfileImageUrl() : null)
                .userName(delivery.getAuthor() != null ? delivery.getAuthor().getName() : null)
                .requesterInfo(hasOrders ? DeliveryDetailResponseDto.RequesterInfo.builder()
                                           .paymentType(firstOrder.getProduct().getPaymentType())
                                           .desiredDeliveryTime(firstOrder.getProduct().getDesiredArrivalTime())
                                           .userImage(firstOrder.getProduct().getAuthor().getProfileImageUrl())
                                           .userName(firstOrder.getProduct().getAuthor().getName())
                                           .productDeliveryAddress(firstOrder.getProduct().getPickup().getAddress())
                                           .deliveryDestination(firstOrder.getProduct().getDestination().getAddress())
                                           .productInfo(firstOrder.getProduct().getItemInfo())
                                           .deliveryFee(firstOrder.getDeliveryFee())
                                           .receivingTime(firstOrder.getProduct().getPickupTime())
                                           .build() : null)
                .deliveryFail(failedAndCancelled != null && failedAndCancelled.getOrder().getStatus() == DeliveryStatus.FAILED ? DeliveryDetailResponseDto.DeliveryFail.builder().failReason(failedAndCancelled.getReason()).build() : null)
                .deliveryCancel(failedAndCancelled != null && failedAndCancelled.getOrder().getStatus() == DeliveryStatus.CANCELED ? DeliveryDetailResponseDto.DeliveryCancel.builder().cancelReason(failedAndCancelled.getReason()).build() : null)
                .deliverySuccessCheck(image != null ? DeliveryDetailResponseDto.DeliverySuccessCheck.builder().deliverySuccessCheckImage(image.getFileUrl()).build() : null)
                .deliveryStatusHistory(deliveryOrders.stream().map(
                                item -> DeliveryDetailResponseDto.DeliveryStatusHistory.builder()
                                        .deliveryStatus(item.getStatus())
                                        .deliveryDate(item.getCreatedAt())
                                        .build()).toList()).build();
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
