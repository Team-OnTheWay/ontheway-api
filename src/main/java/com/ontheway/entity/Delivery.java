package com.ontheway.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 전달자 이동 경로 게시글.
 *
 * 상태 컬럼이 없다. 공개가 끝났는지는 DeliveryOrder 가 있는지로, 출발 시각이 지났는지는
 * 날짜 비교로, 수정할 수 있는지는 Request 가 있는지로 그때그때 판단한다.
 *
 * 날짜와 시간을 따로 받으므로 시간 쪽은 {@code LocalTime} 이다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "delivery", indexes = {
        // 경로 목록의 정렬과 필터
        @Index(name = "idx_delivery_feed", columnList = "delivery_date, id"),
        // 최근 게시물 불러오기, 내 게시글 목록, 이용내역의 진입점
        @Index(name = "idx_delivery_author", columnList = "author_id, deleted_at, id")
})
public class Delivery extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 전달자. 요청의 전달자도 이 값을 가져다 쓴다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_delivery_author"))
    private User author;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "address", column = @Column(name = "departure_address", nullable = false, length = 255)),
            @AttributeOverride(name = "latitude", column = @Column(name = "departure_latitude", nullable = false, precision = 10, scale = 7)),
            @AttributeOverride(name = "longitude", column = @Column(name = "departure_longitude", nullable = false, precision = 10, scale = 7))
    })
    private Location departure;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "address", column = @Column(name = "destination_address", nullable = false, length = 255)),
            @AttributeOverride(name = "latitude", column = @Column(name = "destination_latitude", nullable = false, precision = 10, scale = 7)),
            @AttributeOverride(name = "longitude", column = @Column(name = "destination_longitude", nullable = false, precision = 10, scale = 7))
    })
    private Location destination;

    @Column(nullable = false)
    private LocalDate deliveryDate;

    @Column(nullable = false)
    private LocalTime plannedStartTime;

    @Column(nullable = false)
    private LocalTime plannedEndTime;

    /** 전달자가 받고 싶은 금액. 목록 필터는 이 값이 입력값 이하인 경로만 남긴다. */
    @Column(nullable = false)
    private Integer desiredPrice;

    @Column(length = 500)
    private String additionalInfo;

    private LocalDateTime deletedAt;

    /**
     * 등록과 수정이 함께 쓰는 본문.
     *
     * 같은 타입이 두 개씩 있어서({@code Location}, {@code LocalTime}) 위치 인자로 받으면
     * 출발지와 도착지를 바꿔 넘겨도 컴파일이 통과한다. 이름을 붙여 받으려고 record 로 묶었다.
     */
    @Builder
    public record Content(
            Location departure, Location destination,
            LocalDate deliveryDate, LocalTime plannedStartTime, LocalTime plannedEndTime,
            Integer desiredPrice, String additionalInfo) {
    }

    @Builder
    public Delivery(User author, Content content) {
        this.author = author;
        apply(content);
    }

    /** 수정. 수정할 수 있는 상태인지는 서비스가 먼저 확인한다. */
    public void update(Content content) {
        apply(content);
    }

    private void apply(Content content) {
        this.departure = content.departure();
        this.destination = content.destination();
        this.deliveryDate = content.deliveryDate();
        this.plannedStartTime = content.plannedStartTime();
        this.plannedEndTime = content.plannedEndTime();
        this.desiredPrice = content.desiredPrice();
        this.additionalInfo = content.additionalInfo();
    }

    /** 삭제. 이용내역이 계속 참조하므로 소프트 삭제다. */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    /**
     * 배송 예정 시각이 이미 지났는지.
     * {@link com.ontheway.repository.DeliveryOrderRepository#advanceDueToDelivering} 의 조건과 같아야
     * 한다. 둘이 어긋나지 않는지는 OrderSchedulerIntegrationTest 가 경계값으로 맞춰 본다.
     */
    public boolean isDue(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        return deliveryDate.isBefore(today)
                || (deliveryDate.isEqual(today) && !plannedStartTime.isAfter(now.toLocalTime()));
    }
}
