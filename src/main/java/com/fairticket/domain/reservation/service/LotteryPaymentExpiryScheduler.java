package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.domain.reservation.constants.ReservationConstants;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

// 추첨 결제 마감(티켓 오픈 15분 전) 경과 시, 미결제(PENDING) 추첨 예약을 자동 취소한다
@Slf4j
@Component
@RequiredArgsConstructor
public class LotteryPaymentExpiryScheduler {

    private final ScheduleRepository scheduleRepository;
    private final ReservationRepository reservationRepository;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void cancelUnpaidLotteryReservations() {
        LocalDateTime now = LocalDateTime.now();
        // 추첨 결제 마감(티켓 오픈 15분 전)이 지난 회차만 조회
        LocalDateTime threshold = now.plusMinutes(ReservationConstants.LOTTERY_PAYMENT_CLOSE_MINUTES);
        scheduleRepository.findByTicketOpenAtLessThanEqual(threshold)
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
                .doOnSubscribe(s -> log.debug("추첨 결제 마감 스캔 시작"))
                .onErrorResume(e -> {
                    log.warn("추첨 결제 마감 스케줄러 오류: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }
}
