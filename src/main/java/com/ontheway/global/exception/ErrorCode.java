package com.ontheway.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 도메인별 에러코드는 여기에 추가
    DELIVERY_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "이동 경로 게시글을 찾지 못하였습니다."),
    IMAGE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 찾지 못하였습니다."),
    DELIVERY_BOARD_NOT_OWNED(HttpStatus.INTERNAL_SERVER_ERROR, "작성자가 아닌 사용자는 이동 경로 게시글을 수정할수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
