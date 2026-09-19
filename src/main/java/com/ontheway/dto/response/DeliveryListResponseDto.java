package com.ontheway.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryListResponseDto {
    @Schema(description = "의뢰 요청 목록")
    private List<DeliveryList> deliveryList;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeliveryList {
        @Schema(description = "배송 ID")
        private Long deliveryId;
        @Schema(description = "출발지")
        private String startAddress;
        @Schema(description = "배송목적지")
        private String endAddress;
        @Schema(description = "희망금액")
        private int hopePrice;
        @Schema(description = "배송 의뢰 요청글 수")
        private int requestCount;
        @Schema(description = "배송예정시간")
        private LocalDateTime deliveryDate;
    }
}
