package com.ontheway.controller;

import com.ontheway.dto.request.*;
import com.ontheway.dto.response.*;
import com.ontheway.global.response.ApiResponse;
import com.ontheway.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/delivery")
@Tag(name = "이동 경로 게시글")
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService deliveryService;

    @GetMapping("/list")
    @Operation(summary = "이동 경로 게시글 목록 조회")
    public ApiResponse<DeliveryListResponseDto> list(DeliveryListRequestDto deliveryListRequestDto) {
        return ApiResponse.success(deliveryService.list(deliveryListRequestDto));
    }

    @GetMapping
    @Operation(summary = "이동 경로 게시글 상세 조회")
    public ApiResponse<DeliveryDetailResponseDto> detail(DeliveryDetailRequestDto deliveryDetailRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(deliveryService.detail(userId, deliveryDetailRequestDto));
    }

    @PostMapping
    @Operation(summary = "이동 경로 게시글 등록")
    public ApiResponse<DeliverySaveResponseDto> create(@RequestBody DeliverySaveRequestDto deliverySaveRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(deliveryService.create(userId, deliverySaveRequestDto));
    }

    @PatchMapping
    @Operation(summary = "이동 경로 게시글 수정")
    public ApiResponse<DeliveryUpdateResponseDto> update(@RequestBody DeliveryUpdateRequestDto deliveryUpdateRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(deliveryService.update(userId, deliveryUpdateRequestDto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "이동 경로 게시글 삭제")
    public ApiResponse<DeliveryDeleteResponseDto> delete(@PathVariable Long id) {
        Long userId = 0L;
        return ApiResponse.success(deliveryService.delete(userId, id));
    }

    @GetMapping("/me/list")
    @Operation(summary = "내 게시글 이동 경로 목록 조회")
    public ApiResponse<MyBoardDeliveryListResponseDto> myList(MyBoardDeliveryListRequestDto myBoardDeliveryListRequestDto) {
        Long userId = 0L;
        return ApiResponse.success(deliveryService.myList(userId, myBoardDeliveryListRequestDto));
    }
}
