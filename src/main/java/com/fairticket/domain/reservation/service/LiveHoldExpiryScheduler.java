package com.fairticket.domain.reservation.service;

import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationSeat;
import com.fairticket.domain.reservation.entity.ReservationSeatStatus;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.constants.ReservationConstants;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.reservation.repository.ReservationSeatRepository;
import com.fairticket.domain.seat.service.SeatHoldService;
import com.fairticket.domain.seat.service.SeatPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

// 라이브 트랙 좌석 홀드 만료 처리: 좌석 선택 후 최대 HOLD_MINUTES(10분) 경과 시 좌석 반환
// 결제 완료 시에는 LiveTrackService.onPaymentCompleted로 즉시 홀드 해제 + 상태 전이
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveHoldExpiryScheduler {

    private final ReservationSeatRepository reservationSeatRepository;
    private final ReservationRepository reservationRepository;
    private final SeatHoldService seatHoldService;
    private final SeatPoolService seatPoolService;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void releaseExpiredHolds() {
        RLock lock = redissonClient.getLock("scheduler:live-hold-expiry");
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 55, TimeUnit.SECONDS);
            if (!acquired) return;

            LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(ReservationConstants.HOLD_MINUTES);
            reservationSeatRepository.findByStatusAndCreatedAtBefore(
                            ReservationSeatStatus.PENDING.name(), expiryThreshold)
                    .flatMap(rs -> reservationRepository.findById(rs.getReservationId())
                            .filter(r -> "LIVE".equals(r.getTrackType()))
                            .flatMap(reservation -> releaseOne(reservation, rs)))
                    .count()
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            log.info("라이브 홀드 만료 처리: {}건 좌석 풀 반환", count);
                        }
                    })
                    .block(Duration.ofSeconds(30));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("라이브 홀드 만료 스케줄러 오류", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Mono<Long> releaseOne(Reservation reservation, ReservationSeat seat) {
        Long scheduleId = reservation.getScheduleId();
        String zone = seat.getZone();
        String seatNumber = seat.getSeatNumber();
        return seatHoldService.releaseHold(scheduleId, zone, seatNumber)
                .then(seatPoolService.returnSeat(scheduleId, zone, seatNumber))
                .then(Mono.defer(() -> {
                    seat.setStatus(ReservationSeatStatus.CANCELLED.name());
                    return reservationSeatRepository.save(seat);
                }))
                .then(Mono.defer(() -> {
                    int newQty = Math.max(0, reservation.getQuantity() - 1);
                    reservation.setQuantity(newQty);
                    reservation.setUpdatedAt(LocalDateTime.now());
                    if (newQty == 0) {
                        reservation.setStatus(ReservationStatus.CANCELLED.name());
                    }
                    return reservationRepository.save(reservation).thenReturn(1L);
                }))
                .doOnSuccess(v -> log.debug("홀드 만료 반환: scheduleId={}, zone={}, seat={}",
                        scheduleId, zone, seatNumber));
    }
}
