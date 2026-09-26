package com.ontheway.scheduler;

import com.ontheway.repository.DeliveryOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 배송대기중 -> 배송중, 확인요청 -> 배송완료(72시간) 자동 전이.
 * 두 주기 모두 application.properties 의 scheduler.order.* 로 조정한다 — 임의로 정한 값이라 외부화해뒀다.
 *
 * 엔티티를 읽어 바꾸지 않고 상태 조건이 걸린 UPDATE 로 처리한다. 이 스케줄러는 OrderService 가 쥐는
 * 경로 락을 쥐지 않아서, 읽은 뒤 커밋 전에 같은 건이 실패 처리되면 그 결과를 덮어쓰기 때문이다.
 *
 * 시각은 JVM 기본 타임존을 쓴다. OnthewayApplication.main() 이 Asia/Seoul 로 고정한다.
 * {@code @Scheduled} 의 zone 은 크론이 발화하는 시각만 정할 뿐 아래 now 에는 영향이 없다.
 */
@Slf4j
@Component
public class OrderScheduler {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final Duration completionAutoConfirmAfter;

    public OrderScheduler(DeliveryOrderRepository deliveryOrderRepository,
                           @Value("${scheduler.order.completion-auto-confirm-hours}") long completionAutoConfirmHours) {
        this.deliveryOrderRepository = deliveryOrderRepository;
        this.completionAutoConfirmAfter = Duration.ofHours(completionAutoConfirmHours);
    }

    /** 예정 시각이 지난 배송대기중 건을 배송중으로 넘긴다. */
    @Transactional
    @Scheduled(cron = "${scheduler.order.advance-delivering-cron}", zone = "Asia/Seoul")
    public void advanceDueDeliveries() {
        LocalDateTime now = LocalDateTime.now();
        int advanced = deliveryOrderRepository.advanceDueToDelivering(now, now.toLocalDate(), now.toLocalTime());
        if (advanced > 0) {
            log.info("배송중으로 자동 전이됨. {}건", advanced);
        }
    }

    /** 확인요청 후 72시간이 지난 건을 배송완료로 넘긴다. */
    @Transactional
    @Scheduled(cron = "${scheduler.order.auto-complete-cron}", zone = "Asia/Seoul")
    public void autoCompleteOverdue() {
        LocalDateTime now = LocalDateTime.now();
        int completed = deliveryOrderRepository.completeOverdue(now, now.minus(completionAutoConfirmAfter));
        if (completed > 0) {
            log.info("배송완료로 자동 전이됨(확인요청 후 {}시간 경과). {}건", completionAutoConfirmAfter.toHours(), completed);
        }
    }
}
