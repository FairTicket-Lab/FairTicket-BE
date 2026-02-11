package com.fairticket.domain.seat.repository;

import com.fairticket.domain.seat.dto.GradeSeatCount;
import com.fairticket.domain.seat.entity.Seat;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SeatRepository extends ReactiveCrudRepository<Seat, Long> {

    // 좌석 상태 갱신 (홀드/해제/판매 시 Redis와 DB 단일 출처 유지용) 
    @Modifying
    @Query("UPDATE seats SET status = :status WHERE schedule_id = :scheduleId AND zone = :zone AND seat_number = :seatNumber")
    Mono<Integer> updateStatusByScheduleIdAndZoneAndSeatNumber(
            @Param("status") String status,
            @Param("scheduleId") Long scheduleId,
            @Param("zone") String zone,
            @Param("seatNumber") String seatNumber);

    Mono<Long> countByScheduleIdAndGrade(Long scheduleId, String grade);

    Flux<Seat> findByScheduleId(Long scheduleId);

    Flux<Seat> findByScheduleIdAndZone(Long scheduleId, String zone);

    Mono<Seat> findByScheduleIdAndZoneAndSeatNumber(Long scheduleId, String zone, String seatNumber);

    // 한 구역 내 여러 좌석 번호로 일괄 조회 (배치 배정용)
    Flux<Seat> findByScheduleIdAndZoneAndSeatNumberIn(Long scheduleId, String zone, List<String> seatNumbers);

    // 등급별 좌석 수 집계 (hasLotteryQuotaReached 최적화용)
    @Query("SELECT grade, COUNT(*) as count FROM seats WHERE schedule_id = :scheduleId GROUP BY grade")
    Flux<GradeSeatCount> findSeatCountByScheduleIdGroupByGrade(Long scheduleId);
}
