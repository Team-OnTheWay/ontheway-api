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
    // 인증
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용중인 아이디 입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "토큰이 만료되었습니다. 다시 로그인 해주세요."),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "보안 문제가 감지되어 재로그인이 필요합니다."),

    // 이메일 인증
    CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 시간이 만료되었습니다. 다시 요청해주세요."),
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "인증 메일 발송에 실패했습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해주세요."),

    DELIVERY_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "이동 경로 게시글을 찾지 못하였습니다."),
    IMAGE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 찾지 못하였습니다."),
    DELIVERY_BOARD_NOT_OWNED(HttpStatus.INTERNAL_SERVER_ERROR, "작성자가 아닌 사용자는 이동 경로 게시글을 수정할수 없습니다."),

    // 배송 처리
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "의뢰 요청을 찾지 못하였습니다."),
    REQUEST_CLOSED(HttpStatus.CONFLICT, "이미 종료되었거나 삭제된 의뢰 요청입니다."),
    DELIVERY_ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 배송이 성사된 이동 경로 게시글입니다."),
    PRODUCT_ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 다른 배송에 수락된 물품입니다."),
    INVALID_ORDER_STATE(HttpStatus.CONFLICT, "현재 배송 상태에서는 처리할 수 없습니다."),
    PROOF_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "배송 완료 사진을 등록해주세요."),
    // 용량 숫자는 application.properties(spring.servlet.multipart.max-file-size) 한 곳에서만 정하므로 메시지에 적지 않는다
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "이미지 파일만, 허용 용량 이내로 등록할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),

    // 물품 게시글. HTTP 상태는 명세에 없어 임의로 정한 값이다
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "물품 게시글을 찾지 못하였습니다."),
    PRODUCT_LOCKED(HttpStatus.CONFLICT, "이미 수락된 거래에 쓰인 물품은 수정하거나 삭제할 수 없습니다. 진행 중인 배송은 배송 취소를 이용해주세요."),
    PRODUCT_NOT_CHANGED(HttpStatus.BAD_REQUEST, "변경된 내용이 없어 수정할 수 없습니다."),

    //의뢰 요청
    DELIVERY_CLOSED(HttpStatus.NOT_FOUND, "거래가 매칭된 게시글입니다."),
    DUPLICATE_REQUEST(HttpStatus.NOT_FOUND, "이미 의뢰 요청한 게시글입니다."),
    
    //후기
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "이미 후기가 등록된 매칭 게시글입니다."),
    DELIVERY_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "배송완료 이후 후기 작성이 가능합니다."),
    ORDER_NOT_FOUND(HttpStatus.BAD_REQUEST, "게시글 조회를 실패하였습니다."),
    INVALID_RATING(HttpStatus.BAD_REQUEST, "평점은 0점에서 5점 사이 0.5점 단위로 입력해주세요."),

    // 신고
    REPORT_CATEGORY_REQUIRED(HttpStatus.BAD_REQUEST, "신고 유형을 1개 이상 선택해주세요."),
    REPORT_CATEGORY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "신고 유형은 최대 3개까지 선택할 수 있습니다."),
    REPORT_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "신고 내용을 입력해주세요.")
    ;

    private final HttpStatus status;
    private final String message;
}
