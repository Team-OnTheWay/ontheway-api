package com.ontheway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자.
 *
 * 의뢰자와 전달자를 나누는 역할 컬럼이 없다. 어떤 글을 썼는지로 그때그때 갈린다.
 * 탈퇴도 {@code deletedAt} 하나로 표현하고, 탈퇴 사유는 {@link UserWithdrawal} 에 쌓인다.
 *
 * 주의: 테이블명 {@code user} 는 PostgreSQL 과 H2 양쪽에서 예약어라 백틱으로 감쌌다.
 * Hibernate 가 방언에 맞는 인용부호로 바꿔준다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "`user`", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_account_id", columnNames = "account_id"),
        @UniqueConstraint(name = "uk_user_email", columnNames = "email")
})
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디. 가입 후 변경할 수 없다. */
    @Column(nullable = false, unique = true, updatable = false, length = 30)
    private String accountId;

    /**
     * BCrypt 해시가 들어간다. 평문 길이 규칙은 DTO 에서 본다.
     *
     * 해시는 60자지만 {@code DelegatingPasswordEncoder} 가 {@code {bcrypt}} 접두사를 붙여
     * 68자를 낸다. 알고리즘을 바꿔도 안 걸리게 100 으로 잡았다.
     */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 255)
    private String email;

    /** 글자수 제한은 DTO 에서 본다. 여기는 컬럼 상한만 잡는다. */
    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false, length = 50)
    private String name;

    /** 프로필 이미지 URL. 안 올렸으면 {@code null} 이고, 기본 이미지는 프론트가 정한다. */
    @Column(length = 500)
    private String profileImageUrl;

    @Column(nullable = false)
    private LocalDate birthDate;

    /**
     * 약관 동의 시각. 전 약관이 필수라 동의 여부는 따로 담지 않는다.
     * 선택 약관이 생기면 항목당 {@code xxxAgreedAt}(nullable) 을 한 칸씩 늘린다.
     */
    @Column(nullable = false)
    private LocalDateTime termsAgreedAt;

    /** 탈퇴 시각. 전역 필터를 걸지 않으므로 조회마다 직접 걸러야 한다. */
    private LocalDateTime deletedAt;

    @Builder
    public User(String accountId, String password, String email,
                String nickname, String name, LocalDate birthDate, LocalDateTime termsAgreedAt) {
        this.accountId = accountId;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.name = name;
        this.birthDate = birthDate;
        this.termsAgreedAt = termsAgreedAt;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    /** 회원 탈퇴. 사유 {@link UserWithdrawal} 저장을 같은 트랜잭션에서 같이 한다. */
    public void withdraw(LocalDateTime now) {
        this.deletedAt = now;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    /** 프로필 이미지 변경. {@code null} 을 넣으면 기본 이미지로 돌아간다. */
    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
