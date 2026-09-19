package com.ontheway.repository;

import com.ontheway.entity.Delivery;
import com.ontheway.entity.Request;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 의뢰 요청. 이용내역 조회도 여기서 시작한다. 수락 여부와 상관없이 요청 하나당 한 행이
 * 남아서다. {@code DeliveryOrder} 에서 시작하면 매칭대기중 건이 빠져 UNION 을 써야 한다.
 *
 * 상태로 거르는 메서드는 {@link #findCancelledHistory} 하나뿐이다. 상태는 컬럼이 아니라
 * {@code Request.getStatus()} 가 만들어내는 값이라서다. 대신 조회 메서드들이
 * {@code left join fetch r.order} 로 그 계산에 필요한 행을 미리 붙여준다.
 */
public interface RequestRepository extends JpaRepository<Request, Long> {

    /** 같은 물품을 같은 경로에 두 번 거는 걸 막는다. {@code uk_request_delivery_product} 의 사전 검사다. */
    boolean existsByDeliveryIdAndProductId(Long deliveryId, Long productId);

    /**
     * 경로를 수정할 수 있는지. 요청이 한 건이라도 들어왔으면 못 고친다.
     *
     * 주의: {@code rejectedAt} 을 보지 않는다. 그래서 전달자가 요청을 전부 거절해도 그 경로는
     * 계속 수정 불가다. 조건을 바꾸려면 새 경로를 등록해야 한다. 삭제는 요청이 있든 없든 된다.
     */
    boolean existsByDeliveryId(Long deliveryId);

    // --- 경로 상세 ---
    // 보는 사람이 게시자냐 의뢰자냐 제3자냐에 따라 볼 수 있는 요청이 다르다.
    // 제3자는 요청을 아예 못 보므로 조회 메서드가 없다. 경로 본문만 읽으면 된다.

    /**
     * 게시자(전달자)가 보는 목록. 이 경로에 들어온 모든 요청이고, 거절된 건은 뺀다.
     *
     * {@code product.author} 는 의뢰자를 찍으려고, {@code order} 는 각 행의 상태를 찍으려고
     * 같이 끌어온다. 빼면 요청 N건마다 SELECT 가 더 나간다.
     */
    @Query("""
            select r from Request r
              join fetch r.product p
              join fetch p.author
              left join fetch r.order
             where r.delivery.id = :deliveryId
               and r.rejectedAt is null
             order by r.id asc
            """)
    List<Request> findAllByDeliveryIdWithProductAndAuthor(@Param("deliveryId") Long deliveryId);

    /**
     * 의뢰자가 보는 목록. 이 경로에 걸린 내 요청만 나온다. 화면이 요청 상태에 따라 갈려서
     * 그 판정에 필요한 만큼만 가져온다. 거절된 요청도 그대로 주고, 거를지는 서비스가 정한다.
     *
     * 위 메서드로 대신하면 안 된다. 그쪽은 남의 요청까지 읽어오는데, 경로에 들어온 의뢰자
     * 목록은 게시자만 볼 수 있다. 서비스에서 걸러내는 건 이미 읽은 뒤라 늦다.
     *
     * {@code List} 인 건 한 사람이 다른 물품 여러 개를 같은 경로에 걸 수 있어서다.
     * {@code uk_request_delivery_product} 는 같은 물품의 중복만 막는다.
     */
    @Query("""
            select r from Request r
              join fetch r.product p
              left join fetch r.order
             where r.delivery.id = :deliveryId
               and p.author.id = :requesterId
             order by r.id asc
            """)
    List<Request> findMyRequestsOnDelivery(@Param("deliveryId") Long deliveryId,
                                           @Param("requesterId") Long requesterId);

    /**
     * 경로 목록의 {@code requestCount}. 여러 경로의 요청 수를 한 번에 센다. 목록을 돌며
     * 경로마다 세면 20건짜리 화면에 SELECT 가 20번 더 나간다.
     *
     * 주의: 요청이 0건인 경로는 결과에 아예 없다. 서비스가 맵으로 묶고 없는 키는 0 으로 본다.
     */
    @Query("""
            select r.delivery.id as deliveryId, count(r) as requestCount
              from Request r
             where r.delivery.id in :deliveryIds
               and r.rejectedAt is null
             group by r.delivery.id
            """)
    List<DeliveryRequestCount> countActiveByDeliveryIds(
            @Param("deliveryIds") Collection<Long> deliveryIds);

    // --- 이용내역 ---
    // 네 화면이 where 절 한 줄만 다르고, 공통 조각은 RequestQueries 에 있다.
    // 삭제된 게시글도 그대로 보여준다.
    //
    // Pageable 은 PageRequest.of(page, size) 로 넘긴다. 정렬이 쿼리에 박혀 있으니 Sort 는
    // 담지 않는다. 반환이 Slice 라 count 쿼리도 안 나간다.

    /**
     * 전체 이용내역. 의뢰와 전달을 한 목록에 섞는다.
     *
     * 의뢰용, 전달용 쿼리를 따로 돌려 메모리에서 합치지 않는다. 각각 N건씩 받아 합친 뒤
     * 자르면 잘린 쪽의 다음 위치를 알 수 없어 페이지가 겹치거나 빠진다. Request 한 테이블에서
     * 시작하니 한 쿼리로 끝난다.
     *
     * 행마다 {@code BoardType} 은 {@code product.author} 가 나인지로 정한다.
     * 나면 REQUEST(의뢰), 아니면 DELIVERY(전달).
     */
    @Query(RequestQueries.HISTORY_SELECT
            + " where " + RequestQueries.IS_PARTY
            + RequestQueries.ORDER_LATEST)
    Slice<Request> findHistory(@Param("userId") Long userId, Pageable pageable);

    /** 의뢰내역. 내가 물품을 올려서 보낸 요청들이다. */
    @Query(RequestQueries.HISTORY_SELECT
            + " where pa.id = :userId"
            + RequestQueries.ORDER_LATEST)
    Slice<Request> findRequestedHistory(@Param("userId") Long userId, Pageable pageable);

    /** 전달내역. 내 경로에 들어온 요청들이다. */
    @Query(RequestQueries.HISTORY_SELECT
            + " where da.id = :userId"
            + RequestQueries.ORDER_LATEST)
    Slice<Request> findDeliveredHistory(@Param("userId") Long userId, Pageable pageable);

    /**
     * 취소/실패 목록.
     *
     * 사유는 여기서 안 읽는다. 목록 DTO 에 사유 필드가 없어서다. 상세에서 필요해지면
     * {@code FailedAndCancelledRepository#findByOrderId} 를 쓴다.
     *
     * {@code left join fetch} 한 {@code o} 를 where 에서 거르므로 사실상 inner join 이 된다.
     * 거래가 있는 행만 필요하니 의도한 대로다.
     */
    @Query(RequestQueries.HISTORY_SELECT
            + " where " + RequestQueries.IS_PARTY
            + " and o.status in (com.ontheway.enums.DeliveryStatus.CANCELED,"
            + " com.ontheway.enums.DeliveryStatus.FAILED)"
            + RequestQueries.ORDER_LATEST)
    Slice<Request> findCancelledHistory(@Param("userId") Long userId, Pageable pageable);

    // --- 수락 시 연쇄 종료 ---
    // 요청을 하나씩 불러와 바꾸면 UPDATE 가 N번 나가므로 벌크로 처리한다.
    // 벌크는 영속성 컨텍스트를 건너뛰어서 두 플래그가 필요하다.
    //   flushAutomatically: 아직 안 나간 변경을 먼저 내보낸다
    //   clearAutomatically: 낡아버린 1차 캐시를 비운다
    // 호출 순서는 DeliveryOrder INSERT 가 먼저, 벌크가 나중이다.
    //
    // 주의: clearAutomatically 가 컨텍스트를 통째로 비우므로, 이 호출 뒤에 들고 있던 엔티티는
    //    준영속이 돼서 필드를 바꿔도 반영되지 않는다. 벌크는 트랜잭션 맨 마지막에 둘 것.

    /** 같은 경로에 들어온 나머지 요청을 종료한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Request r
               set r.rejectedAt = :now
             where r.delivery.id = :deliveryId
               and r.id <> :acceptedRequestId
               and r.rejectedAt is null
            """)
    int rejectSiblingsByDelivery(@Param("deliveryId") Long deliveryId,
                                 @Param("acceptedRequestId") Long acceptedRequestId,
                                 @Param("now") LocalDateTime now);

    /**
     * 같은 물품이 다른 경로에 걸어둔 요청들을 종료한다.
     *
     * 물품 하나가 두 경로에서 동시에 배송될 수는 없으니, 한 곳에서 수락되면 나머지를 닫는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Request r
               set r.rejectedAt = :now
             where r.product.id = :productId
               and r.id <> :acceptedRequestId
               and r.rejectedAt is null
            """)
    int rejectSiblingsByProduct(@Param("productId") Long productId,
                                @Param("acceptedRequestId") Long acceptedRequestId,
                                @Param("now") LocalDateTime now);

    int countByDelivery(Delivery delivery);
}
