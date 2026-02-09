package com.fairticket.domain.payment.repository;

import com.fairticket.domain.payment.entity.Payment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {

    Mono<Payment> findByMerchantUid(String merchantUid);

    Mono<Payment> findByReservationId(Long reservationId);

    Flux<Payment> findByStatus(String status);

    Flux<Payment> findByUserId(Long userId);
}