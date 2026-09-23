package com.ontheway.infra.cache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CaffeineRefreshTokenStore implements RefreshTokenStore {
    private static final long GRACE_PERIOD_MILLIS = 5_000;
    private final Cache<String, TokenRecord> refreshTokenCache;
    private record TokenRecord(String currentToken, String previousToken, long rotatedAtMillis) {}

    public CaffeineRefreshTokenStore(@Value("${jwt.refresh-token-validity}") long refreshTokenValidity) {
        this.refreshTokenCache = Caffeine.newBuilder()
                .expireAfterWrite(refreshTokenValidity, TimeUnit.MILLISECONDS)
                .maximumSize(100_000)
                .build();
    }

    @Override
    public void save(String accountId, String refreshToken) {
        TokenRecord existing = refreshTokenCache.getIfPresent(accountId);
        String previous = (existing != null) ? existing.currentToken() : null;
        refreshTokenCache.put(accountId, new TokenRecord(refreshToken, previous, System.currentTimeMillis()));
    }

    @Override
    public void saveOnLogin(String accountId, String refreshToken) {
        refreshTokenCache.put(accountId, new TokenRecord(refreshToken, null, System.currentTimeMillis()));
    }

    @Override
    public TokenValidationResult validate(String accountId, String refreshToken) {
        TokenRecord record = refreshTokenCache.getIfPresent(accountId);

        if (record == null) {
            return TokenValidationResult.NOT_FOUND;
        }
        if (refreshToken.equals(record.currentToken())) {
            return TokenValidationResult.VALID;
        }

        boolean isPreviousToken = refreshToken.equals(record.previousToken());
        boolean withinGracePeriod = (System.currentTimeMillis() - record.rotatedAtMillis()) <= GRACE_PERIOD_MILLIS;

        if (isPreviousToken && withinGracePeriod) {
            return TokenValidationResult.VALID_GRACE;
        }

        return TokenValidationResult.REUSED; //탈취 의심
    }

    @Override
    public void delete(String accountId) {
        refreshTokenCache.invalidate(accountId);
    }
}
