package com.ontheway.controller;

import com.ontheway.dto.request.HistoryListRequestDto;
import com.ontheway.dto.response.HistoryListResponseDto;
import com.ontheway.global.response.ApiResponse;
import com.ontheway.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/history")
@Tag(name = "이용 내역")
@RequiredArgsConstructor
public class HistoryController {
    private final HistoryService historyService;

    @GetMapping("/list")
    @Operation(summary = "이용 내역 전체 목록 조회")
    public ApiResponse<HistoryListResponseDto> list(HistoryListRequestDto historyListRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(historyService.list(userId, historyListRequestDto));
    }

    @GetMapping("/delivery/list")
    @Operation(summary = "이용 내역 이동 경로 목록 조회")
    public ApiResponse<HistoryListResponseDto> deliveryList(HistoryListRequestDto historyListRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(historyService.deliveryList(userId, historyListRequestDto));
    }

    @GetMapping("/request/list")
    @Operation(summary = "이용 내역 의뢰 요청 목록 조회")
    public ApiResponse<HistoryListResponseDto> requestList(HistoryListRequestDto historyListRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(historyService.requestList(userId, historyListRequestDto));
    }

    @GetMapping("/cancel/list")
    @Operation(summary = "취소/실패 목록 조회")
    public ApiResponse<HistoryListResponseDto> cancelList(HistoryListRequestDto historyListRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(historyService.cancelList(userId, historyListRequestDto));
    }
}
