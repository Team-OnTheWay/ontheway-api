package com.ontheway.repository;

import com.ontheway.entity.Delivery;
import com.ontheway.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 전달자 경로 게시글.
 *
 * 목록 두 개(전체 피드, 내 게시글의 전달 탭)는 동적 조건이 붙어
 * {@link DeliveryRepositoryCustom} 에 따로 있다.
 *
 * 여기 메서드에는 게시 상태 조건이 없다. Delivery 에 상태 컬럼이 없어서 공개가 끝났는지는
 * DeliveryOrder 가 있는지로 본다.
 */
public interface DeliveryRepository extends JpaRepository<Delivery, Long>, DeliveryRepositoryCustom {

    Optional<Delivery> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 배송 처리(수락, 상태 전이)의 진입 잠금. 삭제 여부는 거르지 않는다. 판단은 서비스가 한다.
     *
     * 경로 하나에는 배송이 한 건뿐이라 이 행을 잠그면 그 배송의 상태 전이와, 같은 경로에 들어온
     * 요청들의 동시 수락이 한 줄로 서게 된다. {@code existsByRequest_Delivery_Id} 확인과 INSERT
     * 사이에 다른 수락이 끼어드는 걸 DB 제약 없이 막는 유일한 수단이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);

    /** 최근 게시물 불러오기. 직전에 쓴 글 한 건을 등록 양식에 채워준다. 삭제글은 뺀다. */
    Optional<Delivery> findTopByAuthorIdAndDeletedAtIsNullOrderByIdDesc(Long authorId);

    @Query(value = "SELECT d FROM Delivery d " +
            "WHERE (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
            "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
            "AND (:minHopePrice IS NULL OR d.desiredPrice >= :minHopePrice) " +
            "AND (:maxHopePrice IS NULL OR d.desiredPrice <= :maxHopePrice) " +
            "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE (r.order.request.delivery.author = d.author or r.order.request.product.author = d.author) and r.reviewer != d.author) >= :rating) " +
            "AND d.deletedAt IS NULL " +
            "AND d.deliveryDate >= :now " +
            "ORDER BY d.deliveryDate ASC",
            countQuery = "SELECT COUNT(d) FROM Delivery d " +
                    "WHERE (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
                    "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
                    "AND (:minHopePrice IS NULL OR d.desiredPrice >= :minHopePrice) " +
                    "AND (:maxHopePrice IS NULL OR d.desiredPrice <= :maxHopePrice) " +
                    "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.order.request.delivery.author = d.author) >= :rating) " +
                    "AND d.deletedAt IS NULL " +
                    "AND d.deliveryDate >= :now ")
    Page<Delivery> findAllBySearch(@Param("startAddress") String startAddress, @Param("endAddress") String endAddress, @Param("rating") Double rating, @Param("minHopePrice") Integer minHopePrice, @Param("maxHopePrice") Integer maxHopePrice, @Param("now") LocalDate now, Pageable pageable);

    @Query(value = "SELECT d FROM Delivery d " +
            "WHERE d.author = :author " +
            "AND (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
            "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
            "AND (:minHopePrice IS NULL OR d.desiredPrice >= :minHopePrice) " +
            "AND (:maxHopePrice IS NULL OR d.desiredPrice <= :maxHopePrice) " +
            "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.order.request.delivery.author = d.author) >= :rating) " +
            "AND d.deletedAt IS NULL",
            countQuery = "SELECT COUNT(d) FROM Delivery d " +
                    "WHERE d.author = :author " +
                    "AND (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
                    "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
                    "AND (:minHopePrice IS NULL OR d.desiredPrice >= :minHopePrice) " +
                    "AND (:maxHopePrice IS NULL OR d.desiredPrice <= :maxHopePrice) " +
                    "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.order.request.delivery.author = d.author) >= :rating) " +
                    "AND d.deletedAt IS NULL")
    List<Delivery> findAllBySearchAuthorAndDeletedAt(@Param("author") User author, @Param("startAddress") String startAddress, @Param("endAddress") String endAddress, @Param("rating") Double rating, @Param("minHopePrice") Integer minHopePrice, @Param("maxHopePrice") Integer maxHopePrice, Pageable pageable);

    List<Delivery> findByAuthor(User user);
}
