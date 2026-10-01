package com.ontheway.dto.response;

import com.ontheway.enums.DeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestDeliveryListResponseDto {
    @Schema(description = "요청 목록")
    private List<RequestDelivery> requestDeliveryList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestDelivery {
        @Schema(description = "요청 ID")
        private Long requestId;
        @Schema(description = "요청자 이름")
        private String requesterName;
        @Schema(description = "요청자 닉네임")
        private String requesterNickname;
        @Schema(description = "요청자 이미지")
        private String requesterImage;
        @Schema(description = "물품 수령지(주소)")
        private String productDeliveryAddress;
        @Schema(description = "배송 목적지")
        private String deliveryDestination;
        @Schema(description = "물품명")
        private String itemName;
        @Schema(description = "물품 정보")
        private String itemInfo;
        @Schema(description = "배송료")
        private Integer deliveryFee;
        @Schema(description = "물건 수령 시간")
        private String receivingTime;
        @Schema(description = "희망배도착시간")
        private String desiredDeliveryTime;
        @Schema(description = "결제 방식")
        private String paymentType;
        @Schema(description = "게시 등록일")
        private LocalDateTime createdAt;
        @Schema(description = "의뢰 상태")
        private DeliveryStatus deliveryStatus;
    }
}
