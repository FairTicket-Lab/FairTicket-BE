package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.entity.ScheduleStatus;
import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.domain.reservation.constants.ReservationConstants;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

// 추첨 결제 마감(티켓 오픈 LOTTERY_PAYMENT_CLOSE_MINUTES분 전) 경과 시,
// 미결제(PENDING) 추첨 예약을 자동 취소한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class LotteryPaymentExpiryScheduler {

    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void cancelUnpaidLotteryReservations() {
        RLock lock = redissonClient.getLock("scheduler:lottery-payment-expiry");
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 55, TimeUnit.SECONDS);
            if (!acquired) return;

            LocalDateTime now = LocalDateTime.now();
            // 추첨 결제 마감 = ticketOpenAt - LOTTERY_PAYMENT_CLOSE_MINUTES
            // 마감이 지난 회차: ticketOpenAt - 15분 <= now → ticketOpenAt <= now + 15분
            // 단, COMPLETED 제외
            LocalDateTime threshold = now.plusMinutes(ReservationConstants.LOTTERY_PAYMENT_CLOSE_MINUTES);
            scheduleRepository.findByTicketOpenAtLessThanEqualAndStatusNot(
                            threshold, ScheduleStatus.COMPLETED.name())
                    .filter(schedule -> {
                        // ticketOpenAt - 15분이 현재 시각 이전인 회차만 (결제 마감 시각이 지난 회차)
                        LocalDateTime paymentCloseAt = schedule.getTicketOpenAt()
                                .minusMinutes(ReservationConstants.LOTTERY_PAYMENT_CLOSE_MINUTES);
                        return !now.isBefore(paymentCloseAt);
                    })
                    .flatMap(schedule -> reservationRepository
                            .findByScheduleIdAndTrackTypeAndStatus(
                                    schedule.getId(), TrackType.LOTTERY.name(), ReservationStatus.PENDING.name())
                            .flatMap(reservation -> {
                                reservation.setStatus(ReservationStatus.CANCELLED.name());
                                reservation.setUpdatedAt(now);
                                return reservationRepository.save(reservation)
                                        .doOnSuccess(r -> log.info("추첨 미결제 자동 취소: reservationId={}, scheduleId={}, userId={}",
                                                r.getId(), r.getScheduleId(), r.getUserId()));
                            }))
                    .collectList()
                    .block(Duration.ofSeconds(30));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("추첨 결제 마감 스케줄러 오류", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
