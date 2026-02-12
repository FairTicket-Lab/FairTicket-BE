package com.fairticket.domain.reservation.repository;

import com.fairticket.domain.reservation.dto.GradeReservedSum;
import com.fairticket.domain.reservation.entity.Reservation;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReservationRepository extends ReactiveCrudRepository<Reservation, Long> {

    Flux<Reservation> findByUserId(Long userId);

    // 해당 회차·트랙별 사용자 예약 목록 (추첨 결과 목록 등 조회용)
    Flux<Reservation> findByUserIdAndScheduleIdAndTrackType(Long userId, Long scheduleId, String trackType);

    Flux<Reservation> findByScheduleIdAndStatus(Long scheduleId, String status);

    @Query("SELECT * FROM reservations WHERE status = :status AND quantity = :quantity")
    Flux<Reservation> findByStatusAndQuantity(String status, Integer quantity);

    Mono<Boolean> existsByUserIdAndScheduleIdAndStatusIn(Long userId, Long scheduleId, String... statuses);

    // 해당 회차에 사용자 예약 존재 여부 (사람당 한 스케줄 한 트랙 정책 검증용)
    Mono<Boolean> existsByUserIdAndScheduleId(Long userId, Long scheduleId);

    Mono<Reservation> findFirstByUserIdAndScheduleIdAndTrackType(Long userId, Long scheduleId, String trackType);

    // 추첨(LOTTERY) 트랙: 해당 회차·등급의 예약된 좌석 수 합계 (PENDING, PAID_PENDING_SEAT)
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM reservations " +
           "WHERE schedule_id = :scheduleId AND grade = :grade AND track_type = 'LOTTERY' " +
           "AND status IN ('PENDING', 'PAID_PENDING_SEAT')")
    Mono<Long> sumLotteryQuantityByScheduleAndGrade(Long scheduleId, String grade);

    // 추첨(LOTTERY) 트랙: 해당 사용자의 해당 회차 예약 수량 합계 (PENDING, PAID_PENDING_SEAT)
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM reservations " +
           "WHERE schedule_id = :scheduleId AND user_id = :userId AND track_type = 'LOTTERY' " +
           "AND status IN ('PENDING', 'PAID_PENDING_SEAT')")
    Mono<Long> sumLotteryQuantityByUserAndSchedule(Long scheduleId, Long userId);

    // 라이브 트랙: 해당 사용자의 해당 회차 예약 수량 합계 (PENDING, PAID_PENDING_SEAT, ASSIGNED 등 좌석 보유 중)
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM reservations " +
           "WHERE schedule_id = :scheduleId AND user_id = :userId AND track_type = 'LIVE' " +
           "AND status IN ('PENDING', 'PAID_PENDING_SEAT', 'ASSIGNED')")
    Mono<Long> sumLiveQuantityByUserAndSchedule(Long scheduleId, Long userId);

    // 결제 완료·좌석 미배정 추첨 예약 목록 (라이브 종료 후 좌석 배정용)
    Flux<Reservation> findByScheduleIdAndTrackTypeAndStatus(Long scheduleId, String trackType, String status);

    // 등급별 추첨 예약 수량 합계 (hasLotteryQuotaReached 최적화용)
    @Query("SELECT grade, COALESCE(SUM(quantity), 0) as total FROM reservations " +
           "WHERE schedule_id = :scheduleId AND track_type = 'LOTTERY' " +
           "AND status IN ('PENDING', 'PAID_PENDING_SEAT') GROUP BY grade")
    Flux<GradeReservedSum> findLotteryReservedSumByScheduleIdGroupByGrade(Long scheduleId);
}