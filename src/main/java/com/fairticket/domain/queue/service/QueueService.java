package com.fairticket.domain.queue.service;

import com.fairticket.domain.queue.dto.QueueEntryResponse;
import com.fairticket.domain.queue.dto.QueueStatusResponse;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
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
public class QueueService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final QueueTokenService queueTokenService;

    private static final int BATCH_SIZE = 100;
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);

    /**
     * 대기열 진입
     */
    public Mono<QueueEntryResponse> enterQueue(Long scheduleId, Long userId) {
        String queueKey = RedisKeyGenerator.queue(scheduleId);
        String tokenKey = RedisKeyGenerator.queueToken(userId, scheduleId);

        // 1. 토큰 존재 여부 체크 (이미 입장 처리된 유저)
        return redisTemplate.hasKey(tokenKey)
                .flatMap(hasToken -> {
                    if (hasToken) {
                        return Mono.<QueueEntryResponse>just(QueueEntryResponse.builder()
                                .scheduleId(scheduleId)
                                .userId(userId)
                                .position(0L)
                                .estimatedWaitMinutes(0)
                                .message("이미 입장 토큰이 발급되었습니다")
                                .build());
                    }

                    // 2. 대기열 존재 여부 체크 (이미 대기 중인 유저)
                    return redisTemplate.opsForZSet()
                            .rank(queueKey, userId.toString())
                            .flatMap(existingRank -> {
                                long position = existingRank + 1;
                                return Mono.<QueueEntryResponse>just(QueueEntryResponse.builder()
                                        .scheduleId(scheduleId)
                                        .userId(userId)
                                        .position(position)
                                        .estimatedWaitMinutes(calculateEstimatedWait(position))
                                        .message(String.format("이미 대기 중입니다. 현재 %d번째입니다", position))
                                        .build());
                            })
                            .switchIfEmpty(
                                    // 3. 신규 진입
                                    Mono.defer(() -> {
                                        double score = System.currentTimeMillis();
                                        String heartbeatKey = RedisKeyGenerator.heartbeat(scheduleId, userId);

                                        return redisTemplate.opsForZSet()
                                                .add(queueKey, userId.toString(), score)
                                                .then(redisTemplate.opsForValue().set(heartbeatKey, "alive", HEARTBEAT_TTL))
                                                .then(getPosition(scheduleId, userId))
                                                .map(position -> QueueEntryResponse.builder()
                                                        .scheduleId(scheduleId)
                                                        .userId(userId)
                                                        .position(position)
                                                        .estimatedWaitMinutes(calculateEstimatedWait(position))
                                                        .message(String.format("%d번째로 대기 중입니다", position))
                                                        .build());
                                    })
                            );
                })
                .doOnSuccess(response -> log.info("대기열 진입: userId={}, scheduleId={}, position={}",
                        userId, scheduleId, response.getPosition()));
    }

    /**
     * 대기열 상태 조회 (Polling용)
     */
    public Mono<QueueStatusResponse> getQueueStatus(Long scheduleId, Long userId) {
        String queueKey = RedisKeyGenerator.queue(scheduleId);

        return redisTemplate.opsForZSet()
                .rank(queueKey, userId.toString())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOT_IN_QUEUE)))
                .map(rank -> rank + 1)
                .flatMap(position -> {
                    if (position <= BATCH_SIZE) {
                        return queueTokenService.issueToken(userId, scheduleId)
                                .map(token -> QueueStatusResponse.builder()
                                        .position(position)
                                        .status("READY")
                                        .token(token)
                                        .estimatedWaitMinutes(0)
                                        .message("입장 가능합니다")
                                        .build());
                    }

                    return Mono.just(QueueStatusResponse.builder()
                            .position(position)
                            .status("WAITING")
                            .estimatedWaitMinutes(calculateEstimatedWait(position))
                            .aheadCount(position - 1)
                            .message(String.format("앞에 %d명이 대기 중입니다", position - 1))
                            .build());
                });
    }

    /**
     * 대기열 취소
     */
    public Mono<Boolean> leaveQueue(Long scheduleId, Long userId) {
        String queueKey = RedisKeyGenerator.queue(scheduleId);
        String heartbeatKey = RedisKeyGenerator.heartbeat(scheduleId, userId);

        return redisTemplate.opsForZSet()
                .remove(queueKey, userId.toString())
                .flatMap(removed -> redisTemplate.delete(heartbeatKey).thenReturn(removed > 0))
                .doOnSuccess(success -> log.info("대기열 이탈: userId={}, scheduleId={}, success={}",
                        userId, scheduleId, success));
    }

    /**
     * Heartbeat 처리
     */
    public Mono<Boolean> heartbeat(Long scheduleId, Long userId) {
        String heartbeatKey = RedisKeyGenerator.heartbeat(scheduleId, userId);

        return redisTemplate.opsForValue()
                .set(heartbeatKey, "alive", HEARTBEAT_TTL);
    }

    private Mono<Long> getPosition(Long scheduleId, Long userId) {
        String queueKey = RedisKeyGenerator.queue(scheduleId);

        return redisTemplate.opsForZSet()
                .rank(queueKey, userId.toString())
                .map(rank -> rank + 1);
    }

    private int calculateEstimatedWait(long position) {
        if (position <= BATCH_SIZE) {
            return 0;
        }
        return (int) Math.ceil((position - BATCH_SIZE) / 50.0);
    }
}
