package com.ontheway.infra.cache;

public enum TokenValidationResult {
    VALID,        // 현재 저장된 토큰과 일치
    VALID_GRACE,  // 방금 회전되기 직전 토큰 (동시 요청 유예 허용)
    REUSED,       // 이미 폐기된 토큰의 재사용 감지 (탈취 의심)
    NOT_FOUND     // 저장된 토큰이 없음 (로그아웃 등)
}
