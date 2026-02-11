package com.fairticket.domain.seat.service;

import com.fairticket.domain.reservation.constants.ReservationConstants;
import com.fairticket.domain.seat.entity.SeatStatus;
import com.fairticket.domain.seat.repository.SeatRepository;
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final SeatRepository seatRepository;
    private static final Duration HOLD_TTL = Duration.ofMinutes(ReservationConstants.HOLD_MINUTES);

    // 좌석 임시 홀드 (라이브 트랙, 구역 기준). Redis + seats.status 동기화.
    // @return true: 홀드 성공, false: 이미 홀드된 좌석
    public Mono<Boolean> holdSeat(Long scheduleId, String zone, String seatNumber, Long userId) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, zone, seatNumber);
        return redisTemplate.opsForValue()
                .setIfAbsent(holdKey, userId.toString(), HOLD_TTL)
                .flatMap(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("좌석 홀드 성공: scheduleId={}, zone={}, seat={}, userId={}",
                                scheduleId, zone, seatNumber, userId);
                        return seatRepository.updateStatusByScheduleIdAndZoneAndSeatNumber(
                                        SeatStatus.HELD.name(), scheduleId, zone, seatNumber)
                                .thenReturn(true);
                    }
                    return Mono.just(false);
                });
    }

    // 홀드 해제. Redis 삭제 후 seats.status를 AVAILABLE로 복구 (단일 출처 유지).
    public Mono<Boolean> releaseHold(Long scheduleId, String zone, String seatNumber) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, zone, seatNumber);
        return redisTemplate.delete(holdKey)
                .flatMap(deleted -> deleted > 0
                        ? seatRepository.updateStatusByScheduleIdAndZoneAndSeatNumber(
                                SeatStatus.AVAILABLE.name(), scheduleId, zone, seatNumber)
                                .thenReturn(true)
                        : Mono.just(false))
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("좌석 홀드 해제: scheduleId={}, zone={}, seat={}", scheduleId, zone, seatNumber);
                    }
                });
    }

    // 홀드 소유자 확인
    public Mono<Long> getHoldOwner(Long scheduleId, String zone, String seatNumber) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, zone, seatNumber);
        return redisTemplate.opsForValue().get(holdKey)
                .map(Long::parseLong);
    }

    // 남은 홀드 시간 조회 (초 단위). API에서 "결제 마감까지 N초" 표시용    
    public Mono<Long> getRemainingHoldTime(Long scheduleId, String zone, String seatNumber) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, zone, seatNumber);
        return redisTemplate.getExpire(holdKey)
                .map(Duration::getSeconds);
    }

}
