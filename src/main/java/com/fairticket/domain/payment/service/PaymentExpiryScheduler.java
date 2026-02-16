package com.fairticket.domain.payment.service;

import com.fairticket.domain.payment.entity.PaymentStatus;
import com.fairticket.domain.payment.repository.PaymentRepository;
import com.fairticket.domain.reservation.constants.ReservationConstants;
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

// 결제 준비(prepare) 후 결제창 미진입(impUid=null)으로 5분 경과한 Payment를 CANCELLED 처리.
// PaymentVerificationScheduler는 impUid가 있는 결제만 PortOne 검증하므로,
// impUid=null인 결제는 이 스케줄러가 정리한다. 추첨/라이브 공통.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void cancelExpiredUnpaidPayments() {
        RLock lock = redissonClient.getLock("scheduler:payment-expiry");
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 55, TimeUnit.SECONDS);
            if (!acquired) return;

            LocalDateTime threshold = LocalDateTime.now()
                    .minusMinutes(ReservationConstants.PAYMENT_DEADLINE_MINUTES);

            paymentRepository.findByStatus(PaymentStatus.PENDING.name())
                    .filter(payment -> payment.getCreatedAt() != null
                            && payment.getCreatedAt().isBefore(threshold))
                    .filter(payment -> payment.getImpUid() == null)
                    .flatMap(payment -> {
                        payment.setStatus(PaymentStatus.CANCELLED.name());
                        payment.setUpdatedAt(LocalDateTime.now());
                        return paymentRepository.save(payment)
                                .flatMap(saved -> cancelReservationIfPending(saved.getReservationId()))
                                .doOnSuccess(v -> log.info("미결제 자동 취소: paymentId={}, reservationId={}",
                                        payment.getId(), payment.getReservationId()))
                                .thenReturn(payment);
                    })
                    .count()
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            log.info("미결제 만료 처리: {}건", count);
                        }
                    })
                    .block(Duration.ofSeconds(30));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("미결제 만료 스케줄러 오류", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Mono<Void> cancelReservationIfPending(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .filter(r -> ReservationStatus.PENDING.name().equals(r.getStatus()))
                .flatMap(reservation -> {
                    reservation.setStatus(ReservationStatus.CANCELLED.name());
                    reservation.setUpdatedAt(LocalDateTime.now());
                    return reservationRepository.save(reservation);
                })
                .then();
    }
}
