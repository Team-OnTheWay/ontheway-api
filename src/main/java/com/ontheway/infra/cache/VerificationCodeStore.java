package com.ontheway.infra.cache;

import java.util.Optional;

public interface VerificationCodeStore {
    void save(String key, String code);
    Optional<String> find(String key);
    void delete(String key);

    void markVerified(String key);
    boolean isVerified(String key);
    void removeVerified(String key);

    int increaseAttempt(String key);
    void resetAttempt(String key);
}
