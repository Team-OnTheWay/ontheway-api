package com.ontheway.dto.response;

import com.ontheway.enums.BoardType;
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
@AllArgsConstructor
@NoArgsConstructor
public class HistoryListResponseDto {
    private List<HistoryList> historyList;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HistoryList {
        @Schema(description = "배송 ID")
        private Long deliveryId;
        @Schema(description = "배송 상태")
        private DeliveryStatus deliveryStatus;
        @Schema(description = "게시글 타입")
        private BoardType boardType;
        @Schema(description = "출발지 주소")
        private String startAddress;
        @Schema(description = "도착지 주소")
        private String endAddress;
        @Schema(description = "배송 일시")
        private LocalDateTime deliveryDate;
        @Schema(description = "배송금액")
        private Integer deliveryFee;
    }
}
