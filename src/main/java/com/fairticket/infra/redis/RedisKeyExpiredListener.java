package com.fairticket.infra.redis;

import com.fairticket.domain.payment.entity.PaymentStatus;
import com.fairticket.domain.payment.repository.PaymentRepository;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.reservation.repository.ReservationSeatRepository;
import com.fairticket.domain.seat.service.SeatPoolService;
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisKeyExpiredListener {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatPoolService seatPoolService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // Redis Key 만료 이벤트 리스너
    @EventListener
    public void onKeyExpired(RedisKeyExpiredEvent<String> event) {
        String key = new String(event.getSource());
        log.info("Redis Key 만료 이벤트: {}", key);

        if (key.startsWith("payment-timer:")) {
            handlePaymentTimeout(key);
        } else if (key.startsWith("hold:")) {
            handleHoldExpired(key);
        }
    }

    // 결제 타임아웃 처리
    private void handlePaymentTimeout(String key) {
        try {
            // payment-timer:{reservationId} → reservationId 추출
            Long reservationId = Long.parseLong(key.split(":")[1]);
            log.warn("결제 타임아웃 발생: reservationId={}", reservationId);

            reservationRepository.findById(reservationId)
                    .flatMap(reservation -> {
                        // PENDING 이외의 상태는 이미 처리된 예약 → 건너뜀
                        // (PAID / PAID_PENDING_SEAT / ASSIGNED: 결제 완료됨, CANCELLED / REFUNDED: 이미 취소됨)
                        String status = reservation.getStatus();
                        if (!ReservationStatus.PENDING.name().equals(status)) {
                            log.info("타임아웃 스킵(이미 처리된 예약): reservationId={}, status={}", reservationId, status);
                            return Mono.empty();
                        }

                        // 1) Reservation 취소
                        reservation.setStatus(ReservationStatus.CANCELLED.name());
                        reservation.setUpdatedAt(LocalDateTime.now());

                        return reservationRepository.save(reservation)
                                // 2) Payment 취소
                                .flatMap(savedReservation ->
                                        paymentRepository.findByReservationId(reservationId)
                                                .flatMap(payment -> {
                                                    if (PaymentStatus.PENDING.name().equals(payment.getStatus())) {
                                                        payment.setStatus(PaymentStatus.CANCELLED.name());
                                                        payment.setUpdatedAt(LocalDateTime.now());
                                                        return paymentRepository.save(payment);
                                                    }
                                                    return Mono.just(payment);
                                                })
                                                .then(Mono.just(savedReservation))
                                )
                                // 3) 좌석 반환 (ReservationSeat 기반)
                                .flatMap(savedReservation -> restoreSeat(savedReservation))
                                // 4) 재고 카운터 복구 (stock:{scheduleId}:{grade} INCR)
                                .flatMap(v -> restoreStockCounter(reservation));
                    })
                    .doOnSuccess(v -> log.info("결제 타임아웃 처리 완료: reservationId={}", reservationId))
                    .doOnError(error -> log.error("결제 타임아웃 처리 실패: reservationId={}", reservationId, error))
                    .subscribe();

        } catch (Exception e) {
            log.error("결제 타임아웃 처리 중 오류: key={}", key, e);
        }
    }

    // 좌석 홀드 만료 처리 (라이브 트랙용)
    // hold key 형식: hold:{scheduleId}:{zone}:{seatNo}
    private void handleHoldExpired(String key) {
        try {
            String[] parts = key.split(":");
            Long scheduleId = Long.parseLong(parts[1]);
            String zone = parts[2];
            String seatNumber = parts[3];

            log.warn("좌석 홀드 만료: schedule={}, zone={}, seat={}", scheduleId, zone, seatNumber);

            seatPoolService.returnSeat(scheduleId, zone, seatNumber)
                    .doOnSuccess(result -> log.info("좌석 반환 완료: scheduleId={}, zone={}, seat={}", scheduleId, zone, seatNumber))
                    .doOnError(error -> log.error("좌석 반환 실패: scheduleId={}, zone={}, seat={}", scheduleId, zone, seatNumber, error))
                    .subscribe();

        } catch (Exception e) {
            log.error("좌석 홀드 만료 처리 중 오류: key={}", key, e);
        }
    }

    // ReservationSeat 목록 기반 좌석 풀 반환.
    private Mono<Void> restoreSeat(Reservation reservation) {
        Long reservationId = reservation.getId();
        Long scheduleId = reservation.getScheduleId();
        String trackType = reservation.getTrackType();

        if (!"LIVE".equals(trackType)) {
            // 추첨 트랙: PENDING 상태에서 타임아웃 → 좌석 미배정이므로 반환 불필요
            log.debug("추첨 트랙 타임아웃 좌석 반환 스킵: reservationId={}", reservationId);
            return Mono.empty();
        }

        // 라이브 트랙: ReservationSeat에서 PENDING 상태인 좌석만 풀에 반환
        return reservationSeatRepository.findByReservationId(reservationId)
                .filter(rs -> rs.getZone() != null && rs.getSeatNumber() != null)
                .flatMap(rs -> seatPoolService.returnSeat(scheduleId, rs.getZone(), rs.getSeatNumber())
                        .doOnSuccess(success -> log.info(
                                "타임아웃 좌석 반환: reservationId={}, zone={}, seat={}, success={}",
                                reservationId, rs.getZone(), rs.getSeatNumber(), success))
                        .doOnError(e -> log.error(
                                "타임아웃 좌석 반환 실패: reservationId={}, zone={}, seat={}",
                                reservationId, rs.getZone(), rs.getSeatNumber(), e))
                        .onErrorResume(e -> Mono.just(false)))
                .then();
    }

    // 재고 카운터 복구 (타임아웃 취소 시)
    // stock:{scheduleId}:{grade} INCR — 결제 완료 시 차감한 카운터를 되돌림.
    // 추첨/라이브 공통으로 등급 단위로 관리
    private Mono<Void> restoreStockCounter(Reservation reservation) {
        if (reservation.getGrade() == null) {
            return Mono.empty();
        }
        String stockKey = RedisKeyGenerator.stockKey(reservation.getScheduleId(), reservation.getGrade());
        int quantity = reservation.getQuantity() != null ? reservation.getQuantity() : 1;

        return Flux.range(0, quantity)
                .flatMap(i -> redisTemplate.opsForValue().increment(stockKey))
                .then()
                .doOnSuccess(v -> log.info("재고 카운터 복구: scheduleId={}, grade={}, qty=+{}",
                        reservation.getScheduleId(), reservation.getGrade(), quantity));
    }
}