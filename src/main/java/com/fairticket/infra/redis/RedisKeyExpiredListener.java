package com.fairticket.infra.redis;

import com.fairticket.domain.payment.entity.PaymentStatus;
import com.fairticket.domain.payment.repository.PaymentRepository;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.seat.service.SeatPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisKeyExpiredListener {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final SeatPoolService seatPoolService;

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
            // payment-timer:123 → 123 추출
            Long reservationId = Long.parseLong(key.split(":")[1]);

            log.warn("결제 타임아웃 발생: reservationId={}", reservationId);

            reservationRepository.findById(reservationId)
                    .flatMap(reservation -> {
                        // Reservation 취소
                        reservation.setStatus(ReservationStatus.CANCELLED.name());
                        reservation.setUpdatedAt(LocalDateTime.now());

                        return reservationRepository.save(reservation)
                                .flatMap(savedReservation ->
                                        // Payment도 취소
                                        paymentRepository.findByReservationId(reservationId)
                                                .flatMap(payment -> {
                                                    if (PaymentStatus.PENDING.name().equals(payment.getStatus())) {
                                                        payment.setStatus(PaymentStatus.CANCELLED.name());
                                                        payment.setUpdatedAt(LocalDateTime.now());
                                                        return paymentRepository.save(payment);
                                                    }
                                                    return paymentRepository.save(payment);
                                                })
                                                .then(restoreSeat(savedReservation))
                                );
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

            log.warn("좌석 홀드 만료: schedule={}, zone={}, seat={}",
                    scheduleId, zone, seatNumber);

            seatPoolService.returnSeat(scheduleId, zone, seatNumber)
                    .doOnSuccess(result ->
                            log.info("좌석 반환 완료: seat={}", seatNumber))
                    .doOnError(error ->
                            log.error("좌석 반환 실패: seat={}", seatNumber, error))
                    .subscribe();

        } catch (Exception e) {
            log.error("좌석 홀드 만료 처리 중 오류: key={}", key, e);
        }
    }

    // 좌석 반환 (라이브 트랙만 — 좌석 번호가 있는 경우)
    private reactor.core.publisher.Mono<Void> restoreSeat(
            com.fairticket.domain.reservation.entity.Reservation reservation) {

        if ("LIVE".equals(reservation.getTrackType()) // &&
               // reservation.getSeatNumbers() != null
        ){

            log.info("좌석 반환 시작: schedule={}, grade={}, seats={}",
                    reservation.getScheduleId(),
                    reservation.getGrade()
                   // reservation.getSeatNumbers()
                );

            // seatNumbers는 단일 좌석 번호 또는 쉼표 구분 목록
            // 개별 좌석 반환은 ReservationSeat 기반으로 처리해야 하지만,
            // 여기서는 간단 반환 (zone 정보가 없으므로 grade로 대체 불가 - 추후 개선 필요)
            return reactor.core.publisher.Mono.empty();
        }

        return reactor.core.publisher.Mono.empty();
    }
}
