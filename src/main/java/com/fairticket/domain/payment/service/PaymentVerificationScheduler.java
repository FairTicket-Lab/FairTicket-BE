package com.fairticket.domain.payment.service;

import com.fairticket.domain.payment.entity.PaymentStatus;
import com.fairticket.domain.payment.repository.PaymentRepository;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.repository.ReservationRepository;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentVerificationScheduler {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final PortOneClient portOneClient;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void verifyPendingPayments() {
        RLock lock = redissonClient.getLock("scheduler:payment-verification");
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 55, TimeUnit.SECONDS);
            if (!acquired) return;

            LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

            paymentRepository.findByStatus(PaymentStatus.PENDING.name())
                    .filter(payment -> payment.getCreatedAt() != null
                            && payment.getCreatedAt().isBefore(threshold))
                    .filter(payment -> payment.getMerchantUid() != null)
                    .flatMap(payment -> portOneClient.verifyPayment(payment.getMerchantUid())
                            .flatMap(verification -> {
                                switch (verification.getStatus()) {
                                    case COMPLETED -> {
                                        log.info("결제 불일치 복구 - 완료 처리: paymentId={}, impUid={}",
                                                payment.getId(), payment.getImpUid());
                                        payment.setStatus(PaymentStatus.COMPLETED.name());
                                        payment.setPaidAt(LocalDateTime.now());
                                        return paymentRepository.save(payment)
                                                .flatMap(saved -> updateReservationStatus(
                                                        saved.getReservationId(), ReservationStatus.PAID.name()));
                                    }
                                    case FAILED -> {
                                        log.warn("결제 불일치 복구 - 실패 처리: paymentId={}, impUid={}",
                                                payment.getId(), payment.getImpUid());
                                        payment.setStatus(PaymentStatus.FAILED.name());
                                        payment.setUpdatedAt(LocalDateTime.now());
                                        return paymentRepository.save(payment)
                                                .flatMap(saved -> updateReservationStatus(
                                                        saved.getReservationId(), ReservationStatus.CANCELLED.name()));
                                    }
                                    default -> {
                                        log.debug("결제 검증 대기 중: paymentId={}, status={}",
                                                payment.getId(), verification.getStatus());
                                        return Mono.just(payment);
                                    }
                                }
                            })
                            .onErrorResume(e -> {
                                log.warn("결제 검증 API 호출 실패: paymentId={}, error={}",
                                        payment.getId(), e.getMessage());
                                return Mono.just(payment);
                            }))
                    .count()
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            log.info("결제 불일치 복구 처리: {}건", count);
                        }
                    })
                    .block(Duration.ofSeconds(30));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("결제 불일치 복구 스케줄러 오류", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Mono<Void> updateReservationStatus(Long reservationId, String status) {
        return reservationRepository.findById(reservationId)
                .flatMap(reservation -> {
                    reservation.setStatus(status);
                    reservation.setUpdatedAt(LocalDateTime.now());
                    return reservationRepository.save(reservation);
                })
                .doOnSuccess(r -> log.info("예약 상태 업데이트: reservationId={}, status={}",
                        reservationId, status))
                .then();
    }
}
