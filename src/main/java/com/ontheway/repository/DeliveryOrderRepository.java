package com.ontheway.repository;

import com.ontheway.entity.Delivery;
import com.ontheway.entity.DeliveryOrder;
import com.ontheway.entity.User;
import com.ontheway.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 성사된 배송 거래.
 *
 * 주의: 아래 두 {@code exists} 가 "경로당 1건", "물품당 1건"을 지키는 유일한 수단이다.
 * DB 제약이 없으니 수락 전에 반드시 불러야 한다.
 *
 * 둘 다 상태를 보지 않는다. 한 번 수락된 경로와 물품은 그 거래가 취소나 실패로 끝나도
 * 행이 남아 계속 true 다. 다시 쓰려면 새로 등록해야 하고, 의도한 동작이다.
 */
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    /**
     * 이 경로에 이미 성사된 배송이 있는지. 경로 하나에 배송은 한 건뿐이다.
     *
     * 이름의 밑줄은 오타가 아니라 연관관계 경로({@code request.delivery.id})다.
     * {@code DeliveryOrder} 에 {@code delivery} 필드가 없어서 이름만으로는 어디를 타는지
     * 알 수 없으니 경로를 그대로 드러냈다.
     */
    boolean existsByRequest_Delivery_Id(Long deliveryId);

    /**
     * 이 물품이 이미 수락된 거래에 들어갔는지.
     *
     * 요청을 보낸 것만으로는 true 가 아니다. 거절당한 물품은 그대로 다시 걸 수 있다.
     * 반대로 한 번 수락되면 그 거래가 취소나 실패로 끝나도 계속 true 다.
     *
     * 수락 전 중복 확인과 물품 수정/삭제 가능 여부가 전부 이 하나를 쓴다.
     * "쓰였으니 수정 불가"라는 결론은 서비스가 낸다.
     */
    boolean existsByRequest_Product_Id(Long productId);

    Optional<DeliveryOrder> findByRequestId(Long requestId);

    /**
     * 배송 상세와 상태 변경에 쓴다. 당사자 둘까지 한 번에 올려둔다.
     *
     * {@code getDeliveryFee()}, {@code getRequester()} 같은 getter 가 프록시를 여러 단계
     * 타므로 이걸 안 쓰면 화면 한 장에 SELECT 가 서너 번 더 나간다.
     */
    @Query("""
            select o from DeliveryOrder o
              join fetch o.request rq
              join fetch rq.product p
              join fetch p.author
              join fetch rq.delivery d
              join fetch d.author
             where o.id = :id
            """)
    Optional<DeliveryOrder> findByIdWithParties(@Param("id") Long id);

    /**
     * 수락 이후의 상태를 전이 시키는 데에 쓴다. 경로 하나에는 배송이 한 건뿐이라 경로 ID 만으로 찾는다.
     * 올려두는 범위는 {@link #findByIdWithParties} 와 같다.
     */
    @Query("""
            select o from DeliveryOrder o
              join fetch o.request rq
              join fetch rq.product p
              join fetch p.author
              join fetch rq.delivery d
              join fetch d.author
             where d.id = :deliveryId
            """)
    Optional<DeliveryOrder> findByDeliveryIdWithParties(@Param("deliveryId") Long deliveryId);

    // --- 스케줄러 ---
    // 둘 다 엔티티를 읽어 바꾸지 않고 상태 조건을 건 UPDATE 하나로 처리한다. 스케줄러는 OrderService 가
    // 쥐는 경로 락을 쥐지 않는다. 읽고 나서 커밋하기까지 사이에 같은 건이 실패 처리되면 엔티티 방식은
    // 그 결과를 덮어쓰는데, WHERE 에 상태를 걸면 이미 바뀐 건은 대상에서 빠진다.
    // 대상 상태는 쿼리에 박아뒀다. 파라미터로 받으면 다른 상태로도 부를 수 있는 것처럼 보이는데,
    // 이 스케줄러는 이 상태 말고는 볼 일이 없다.
    // 벌크 UPDATE 는 감사(@LastModifiedDate)를 타지 않아서 updatedAt 을 직접 set 한다.

    /**
     * 예정 시각이 지난 건을 배송중으로 넘긴다. 넘긴 건수를 돌려준다.
     * 경로의 날짜와 시각이 나뉘어 있어 비교가 두 단계다.
     * {@link com.ontheway.entity.Delivery#isDue} 의 조건과 같아야 한다.
     * 둘이 어긋나지 않는지는 OrderSchedulerIntegrationTest 가 경계값으로 맞춰 본다.
     * 바꾸는 필드는 {@link DeliveryOrder#startDelivery} 와 같아야 한다.
     */
    @Modifying
    @Query("""
            update DeliveryOrder o
               set o.status = com.ontheway.enums.DeliveryStatus.DELIVERING,
                   o.deliveryStartedAt = :now,
                   o.updatedAt = :now
             where o.status = com.ontheway.enums.DeliveryStatus.DELIVERY_WAITING
               and o.request.id in (
                   select r.id from Request r
                     join r.delivery d
                    where d.deliveryDate < :today
                       or (d.deliveryDate = :today and d.plannedStartTime <= :nowTime))
            """)
    int advanceDueToDelivering(@Param("now") LocalDateTime now,
                               @Param("today") LocalDate today,
                               @Param("nowTime") LocalTime nowTime);

    /**
     * 확인요청 후 72시간이 지난 건을 배송완료로 넘긴다. 넘긴 건수를 돌려준다.
     * {@code idx_order_completion_req(status, completion_requested_at)} 를 탄다.
     * 바꾸는 필드는 {@link DeliveryOrder#complete} 와 같아야 한다.
     */
    @Modifying
    @Query("""
            update DeliveryOrder o
               set o.status = com.ontheway.enums.DeliveryStatus.COMPLETED,
                   o.completedAt = :now,
                   o.updatedAt = :now
             where o.status = com.ontheway.enums.DeliveryStatus.COMPLETION_REQUESTED
               and o.completionRequestedAt < :deadline
            """)
    int completeOverdue(@Param("now") LocalDateTime now,
                        @Param("deadline") LocalDateTime deadline);

    Page<DeliveryOrder> findByRequest_Delivery_AuthorAndStatusIn(User requestDeliveryAuthor, Collection<DeliveryStatus> statuses, Pageable pageable);

    Page<DeliveryOrder> findByRequest_Product_AuthorAndStatusIn(User requestProductAuthor, Collection<DeliveryStatus> statuses, Pageable pageable);

    List<DeliveryOrder> findByRequest_Delivery(Delivery requestDelivery);

    @Query("SELECT do FROM DeliveryOrder do WHERE do.request.delivery IN :deliveries")
    List<DeliveryOrder> findByRequest_DeliveryIn(@Param("deliveries") List<Delivery> deliveries);
}
