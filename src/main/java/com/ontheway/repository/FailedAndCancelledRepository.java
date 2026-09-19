package com.ontheway.repository;

import com.ontheway.entity.Delivery;
import com.ontheway.entity.DeliveryOrder;
import com.ontheway.entity.FailedAndCancelled;
import com.ontheway.entity.User;
import com.ontheway.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 취소와 실패 기록. 거래당 한 건이다.
 *
 * 저장은 상태 전이와 같은 트랜잭션에서 한다. {@code order.cancel()} 다음에
 * {@code FailedAndCancelled.canceledBy(...)} 를 저장하는 식이다.
 *
 * 사유가 거래 당사자 양쪽에 보여서 조회 메서드를 둔다.
 */
public interface FailedAndCancelledRepository extends JpaRepository<FailedAndCancelled, Long> {

    Optional<FailedAndCancelled> findByOrderId(Long orderId);

    Page<FailedAndCancelled> findByOrder_Request_Product_AuthorAndOrder_StatusIn(User user, List<DeliveryStatus> failed, PageRequest of);

    List<FailedAndCancelled> findByOrder_Request_Delivery(Delivery orderRequestDelivery);
}
