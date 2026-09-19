package com.ontheway.repository;

import com.ontheway.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 조회. 메서드가 두 부류로 나뉘고, 탈퇴 계정을 거르는지가 다르다.
 *
 * 중복 검사는 탈퇴 계정까지 센다. UNIQUE 제약이 탈퇴한 행에도 그대로 걸려 있어서, 여기서
 * {@code deletedAt} 을 빼고 세면 "중복 아님"으로 통과시켜 놓고 INSERT 에서 터진다.
 * 결과적으로 탈퇴한 계정의 아이디와 이메일은 다시 못 쓴다.
 *
 * 사람을 찾는 조회는 반대로 전부 {@code deletedAt is null} 이 붙는다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAccountId(String accountId);

    // --- 중복 검사 (탈퇴 계정 포함) ---

    /** 아이디 중복 확인. */
    boolean existsByAccountId(String accountId);

    boolean existsByEmail(String email);

    // --- 사람 찾기 (탈퇴 계정 제외) ---

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /** 로그인. */
    Optional<User> findByAccountIdAndDeletedAtIsNull(String accountId);

    /** 아이디 찾기. 이메일 인증을 통과한 뒤에 부른다. */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /** 비밀번호 찾기. 아이디와 이메일이 같은 사람 것인지까지 본다. */
    Optional<User> findByAccountIdAndEmailAndDeletedAtIsNull(String accountId, String email);
}
