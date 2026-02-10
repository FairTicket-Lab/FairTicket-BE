package com.fairticket.domain.reservation.repository;

import com.fairticket.domain.reservation.entity.Reservation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReservationRepository extends ReactiveCrudRepository<Reservation, Long> {
    Flux<Reservation> findByUserId(Long userId);
    
    Flux<Reservation> findByScheduleIdAndStatus(Long scheduleId, String status);
    
    @Query("SELECT * FROM reservations WHERE status = :status AND quantity = :quantity")
    Flux<Reservation> findByStatusAndQuantity(String status, Integer quantity);
    
    Mono<Boolean> existsByUserIdAndScheduleIdAndStatusIn(Long userId, Long scheduleId, String... statuses);
    
    Mono<Reservation> findByUserIdAndScheduleIdAndTrackType(Long userId, Long scheduleId, String trackType);
}
