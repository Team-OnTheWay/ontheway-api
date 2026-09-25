package com.ontheway.entity;

import com.ontheway.enums.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 성사된 배송 거래. {@link Request} 가 수락됐을 때만 생긴다.
 *
 * 경로, 물품, 금액을 따로 복사해 두지 않고 전부 {@code request} 를 타고 읽는다. 수락되면
 * 물품 수정이 막혀서 값이 더 바뀌지 않기 때문이다. 수락 시각도 컬럼이 없다. 이 행이 수락할 때
 * 생기므로 {@code created_at} 이 곧 수락 시각이다.
 *
 * 주의: {@code uk_order_request} 가 막아주는 건 같은 요청을 두 번 수락하는 것(더블 클릭,
 * 재시도)뿐이다. "경로당 1건", "물품당 1건"에는 DB 제약이 없으므로 수락 전에 서비스가
 * {@code existsByRequest_Delivery_Id}/{@code existsByRequest_Product_Id} 로 직접 확인해야 한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "delivery_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_request", columnNames = "request_id"),
        // 확인요청 후 72시간 경과 건을 스케줄러가 훑는다
        indexes = @Index(name = "idx_order_completion_req",
                columnList = "status, completion_requested_at"))
public class DeliveryOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_order_request"))
    private Request request;

    /** 픽업중부터의 상태만 들어온다. 매칭대기중과 거절은 Request 가 만든다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // H2가 네이티브 enum 타입을 쓰지 않도록 고정
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveryStartedAt;
    private LocalDateTime completionRequestedAt;
    private LocalDateTime completedAt;

    private DeliveryOrder(Request request) {
        this.request = request;
        this.status = DeliveryStatus.PICKING_UP;   // 수락 직후 상태는 픽업중
    }

    /** 전달자 수락. 경로/물품 중복은 서비스가 먼저 확인한다. */
    public static DeliveryOrder accept(Request request) {
        return new DeliveryOrder(request);
    }

    /** 수락 시각. 이 행은 수락할 때 생기므로 생성 시각과 같다. */
    public LocalDateTime getMatchedAt() {
        return getCreatedAt();
    }

    // --- 상태 전이 ---
    // 전이해도 되는 상태인지, 권한이 있는지, 증빙 사진이 있는지는 서비스가 본다.

    /** 픽업 완료 -> 배송대기중. 이 뒤로는 취소할 수 없다. */
    public void pickUp(LocalDateTime now) {
        this.status = DeliveryStatus.DELIVERY_WAITING;
        this.pickedUpAt = now;
    }

    /**
     * 예정 시각 도달 -> 배송중. 픽업이 예정 시각 뒤에 이뤄졌을 때 OrderService 가 부른다.
     * 그 전에 픽업된 건은 스케줄러가 같은 전이를 UPDATE 쿼리로 처리하니(advanceDueToDelivering),
     * 바꾸는 필드를 고치면 그 쿼리도 같이 고칠 것.
     */
    public void startDelivery(LocalDateTime now) {
        this.status = DeliveryStatus.DELIVERING;
        this.deliveryStartedAt = now;
    }

    /** 증빙 사진을 올린 뒤 확인요청. 사진이 없으면 서비스가 막는다. */
    public void requestCompletion(LocalDateTime now) {
        this.status = DeliveryStatus.COMPLETION_REQUESTED;
        this.completionRequestedAt = now;
    }

    /**
     * 의뢰자 확인 또는 72시간 경과 -> 배송완료. 후기는 이 상태에서만 쓸 수 있다.
     * 72시간 경과는 스케줄러가 UPDATE 쿼리로 처리하니(completeOverdue), 바꾸는 필드를 고치면 그 쿼리도 같이 고칠 것.
     */
    public void complete(LocalDateTime now) {
        this.status = DeliveryStatus.COMPLETED;
        this.completedAt = now;
    }

    /** 배송 실패. 사유는 FailedAndCancelled 에 남는다. */
    public void fail() {
        this.status = DeliveryStatus.FAILED;
    }

    /** 배송 취소. 픽업 완료 뒤에는 부르면 안 된다(서비스가 검사). */
    public void cancel() {
        this.status = DeliveryStatus.CANCELED;
    }

    public boolean isCompleted() {
        return status == DeliveryStatus.COMPLETED;
    }

    // --- request 경유 접근 ---
    // 아래 셋은 request 가 LAZY 프록시라 부를 때마다 SELECT 가 1~2회 더 나간다.
    // 목록에서 N건을 돌면 N배가 되니 조회 쿼리에 join fetch 를 걸어두고 쓸 것.

    public Delivery getDelivery() {
        return request.getDelivery();
    }

    public Product getProduct() {
        return request.getProduct();
    }

    /** 거래 금액. 따로 복사해 두지 않고 물품 게시글 값을 그대로 읽는다. */
    public Integer getDeliveryFee() {
        return request.getProduct().getDeliveryFee();
    }
}
