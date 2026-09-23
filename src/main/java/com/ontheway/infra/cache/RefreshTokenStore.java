package com.ontheway.infra.cache;

public interface RefreshTokenStore {
    void save(String accountId, String refreshToken); //재발급
    void saveOnLogin(String accountId, String refreshToken); //로그인 -> 생성
    TokenValidationResult validate(String accountId, String refreshToken);
    void delete(String accountId);
}
