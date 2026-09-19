package com.ontheway.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class CaffeineVerificationCodeStore implements VerificationCodeStore{

    //Caffeine 설정
    private final Cache<String, String> codeCache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final Cache<String, Boolean> verifiedCache = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final Cache<String, Integer> attemptCache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)   // codeCache와 동일 TTL
            .maximumSize(10_000)
            .build();

    @Override
    public void save(String key, String code) {
        codeCache.put(key, code);
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(codeCache.getIfPresent(key));
    }

    @Override
    public void delete(String key) {
        codeCache.invalidate(key);
    }

    @Override
    public void markVerified(String key) {
        verifiedCache.put(key, true);
    }

    @Override
    public boolean isVerified(String key) {
        return verifiedCache.getIfPresent(key) != null;
    }

    @Override
    public void removeVerified(String key) {
        verifiedCache.invalidate(key);
    }

    @Override
    public int increaseAttempt(String key) {
        return attemptCache.asMap().merge(key, 1, Integer::sum);
    }

    @Override
    public void resetAttempt(String key) {
        attemptCache.invalidate(key);
    }
}
