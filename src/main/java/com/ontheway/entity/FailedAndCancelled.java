package com.ontheway.entity;

import com.ontheway.enums.ResultType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 취소와 실패 기록. 성사된 거래에만 생기고 거래당 한 건이다. 둘 다 종료 상태이고 시점도
 * 겹치지 않아서(취소는 픽업 완료 전, 실패는 후) 한 테이블에 같이 넣었다.
 *
 * {@code actor} 는 취소를 의뢰자와 전달자 양쪽이 할 수 있어서 둔다. 실패는 전달자로
 * 고정이지만 컬럼은 같이 쓴다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "failed_and_cancelled",
        uniqueConstraints = @UniqueConstraint(name = "uk_fnc_order", columnNames = "order_id"))
public class FailedAndCancelled extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_fnc_order"))
    private DeliveryOrder order;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ResultType resultType;

    /** 취소나 실패를 실행한 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_fnc_actor"))
    private User actor;

    /** 사유. 거래 당사자 양쪽에 보인다. */
    @Column(length = 500)
    private String reason;

    private FailedAndCancelled(DeliveryOrder order, ResultType resultType, User actor, String reason) {
        this.order = order;
        this.resultType = resultType;
        this.actor = actor;
        this.reason = reason;
    }

    // 아래 둘은 "이렇게 끝났다"는 기록만 만들고 거래 상태는 건드리지 않는다.
    // 상태 전이는 서비스가 order.cancel() / order.fail() 로 따로 부른다.
    //
    //     order.cancel();
    //     repository.save(FailedAndCancelled.canceledBy(order, actor, reason));

    /** 취소. 의뢰자와 전달자 모두 가능하고 픽업 완료 뒤에는 불가(서비스가 검사). */
    public static FailedAndCancelled canceledBy(DeliveryOrder order, User actor, String reason) {
        return new FailedAndCancelled(order, ResultType.CANCELED, actor, reason);
    }

    /** 배송 실패. 전달자만 가능하고 배송대기중, 배송중에서만 부른다. */
    public static FailedAndCancelled failedBy(DeliveryOrder order, User actor, String reason) {
        return new FailedAndCancelled(order, ResultType.FAILED, actor, reason);
    }

    public Delivery getDelivery() {
        return order.getDelivery();
    }
}
