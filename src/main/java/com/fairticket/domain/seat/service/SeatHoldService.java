package com.fairticket.domain.seat.service;

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
    private static final Duration HOLD_TTL = Duration.ofMinutes(10);

    // 좌석 임시 홀드 (라이브 트랙)
    // @return true: 홀드 성공, false: 이미 홀드된 좌석
    public Mono<Boolean> holdSeat(Long scheduleId, String grade, String seatNumber, Long userId) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, grade, seatNumber);
        return redisTemplate.opsForValue()
                .setIfAbsent(holdKey, userId.toString(), HOLD_TTL)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("좌석 홀드 성공: scheduleId={}, grade={}, seat={}, userId={}",
                                scheduleId, grade, seatNumber, userId);
                    }
                });
    }

    // 홀드 해제
    public Mono<Boolean> releaseHold(Long scheduleId, String grade, String seatNumber) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, grade, seatNumber);
        return redisTemplate.delete(holdKey)
                .map(deleted -> deleted > 0)
                .doOnSuccess(success -> log.info("좌석 홀드 해제: scheduleId={}, grade={}, seat={}",
                        scheduleId, grade, seatNumber));
    }

    // 홀드 여부 확인
    public Mono<Boolean> isHeld(Long scheduleId, String grade, String seatNumber) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, grade, seatNumber);
        return redisTemplate.hasKey(holdKey);
    }

    // 홀드 소유자 확인
    public Mono<Long> getHoldOwner(Long scheduleId, String grade, String seatNumber) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, grade, seatNumber);
        return redisTemplate.opsForValue().get(holdKey)
                .map(Long::parseLong);
    }

    // 남은 홀드 시간 조회 (초 단위)
    public Mono<Long> getRemainingHoldTime(Long scheduleId, String grade, String seatNumber) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, grade, seatNumber);
        return redisTemplate.getExpire(holdKey)
                .map(Duration::getSeconds);
    }
}
