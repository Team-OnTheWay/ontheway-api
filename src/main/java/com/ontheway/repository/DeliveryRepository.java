package com.ontheway.repository;

import com.ontheway.entity.Delivery;
import com.ontheway.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /** 최근 게시물 불러오기. 직전에 쓴 글 한 건을 등록 양식에 채워준다. 삭제글은 뺀다. */
    Optional<Delivery> findTopByAuthorIdAndDeletedAtIsNullOrderByIdDesc(Long authorId);

    @Query(value = "SELECT d FROM Delivery d " +
            "WHERE (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
            "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
            "AND (:hopePrice IS NULL OR d.desiredPrice <= :hopePrice) " +
            "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.order.request.delivery.author = d.author) >= :rating) " +
            "AND d.deletedAt IS NULL",
            countQuery = "SELECT COUNT(d) FROM Delivery d " +
                    "WHERE (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
                    "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
                    "AND (:hopePrice IS NULL OR d.desiredPrice <= :hopePrice) " +
                    "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.order.request.delivery.author = d.author) >= :rating) " +
                    "AND d.deletedAt IS NULL")
    Page<Delivery> findAllBySearch(@Param("startAddress") String startAddress, @Param("endAddress") String endAddress, @Param("rating") Double rating, @Param("hopePrice") Integer hopePrice, Pageable pageable);

    @Query(value = "SELECT d FROM Delivery d " +
            "WHERE d.author = :author " +
            "AND (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
            "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
            "AND (:hopePrice IS NULL OR d.desiredPrice <= :hopePrice) " +
            "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.order.request.delivery.author = d.author) >= :rating) " +
            "AND d.deletedAt IS NULL",
            countQuery = "SELECT COUNT(d) FROM Delivery d " +
                    "WHERE d.author = :author " +
                    "AND (:startAddress IS NULL OR d.departure.address LIKE %:startAddress%) " +
                    "AND (:endAddress IS NULL OR d.destination.address LIKE %:endAddress%) " +
                    "AND (:hopePrice IS NULL OR d.desiredPrice <= :hopePrice) " +
                    "AND (:rating IS NULL OR (SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.order.request.delivery.author = d.author) >= :rating) " +
                    "AND d.deletedAt IS NULL")
    List<Delivery> findAllBySearchAuthorAndDeletedAt(@Param("author") User author, @Param("startAddress") String startAddress, @Param("endAddress") String endAddress, @Param("rating") Double rating, @Param("hopePrice") Integer hopePrice, Pageable pageable);
}
