package com.fairticket.domain.reservation.repository;

import com.fairticket.domain.reservation.entity.ReservationSeat;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ReservationSeatRepository extends ReactiveCrudRepository<ReservationSeat, Long> {

    Flux<ReservationSeat> findByReservationId(Long reservationId);

    // 라이브 홀드 만료 스케줄러용: PENDING이며 생성 시각이 기준 시각 이전인 좌석
    Flux<ReservationSeat> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

    // 해당 예약의 모든 좌석 상태를 CANCELLED로 일괄 업데이트
    @Modifying
    @Query("UPDATE reservation_seats SET status = 'CANCELLED' WHERE reservation_id = :reservationId")
    Mono<Integer> updateStatusToCancelledByReservationId(Long reservationId);
}
