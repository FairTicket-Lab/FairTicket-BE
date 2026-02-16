package com.fairticket.domain.queue.service;

import com.fairticket.domain.queue.config.QueueProperties;
import com.fairticket.domain.queue.dto.QueueEntryResponse;
import com.fairticket.domain.queue.dto.QueueStatusResponse;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final QueueTokenService queueTokenService;
    private final QueueProperties queueProperties;

    private RedisScript<Long> queueEnterScript;

    @PostConstruct
    public void init() {
        queueEnterScript = RedisScript.of(new ClassPathResource("scripts/queue_enter.lua"), Long.class);
    }

    /**
     * 대기열 진입
     */
    public Mono<QueueEntryResponse> enterQueue(Long scheduleId, Long userId) {
        String queueKey = RedisKeyGenerator.queueKey(scheduleId);
        String tokenKey = RedisKeyGenerator.tokenKey(userId, scheduleId);

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
                                    Mono.defer(() -> {
                                        double score = System.currentTimeMillis();

                                        // Lua Script로 큐 크기 체크 + 진입 원자적 처리
                                        return redisTemplate.execute(
                                                        queueEnterScript,
                                                        List.of(queueKey),
                                                        List.of(
                                                                String.valueOf(queueProperties.getMaxQueueSize()),
                                                                userId.toString(),
                                                                String.valueOf((long) score)
                                                        ))
                                                .next()
                                                .flatMap(result -> {
                                                    if (result == 0L) {
                                                        return Mono.error(new BusinessException(ErrorCode.QUEUE_FULL));
                                                    }

                                                    String heartbeatKey = RedisKeyGenerator.heartbeatKey(scheduleId, userId);
                                                    Duration heartbeatTtl = Duration.ofSeconds(queueProperties.getHeartbeatTtlSeconds());

                                                    return redisTemplate.opsForValue().set(heartbeatKey, "alive", heartbeatTtl)
                                                            .then(redisTemplate.opsForSet().add(RedisKeyGenerator.activeSchedulesKey(), scheduleId.toString()))
                                                            .then(getPosition(scheduleId, userId))
                                                            .map(position -> QueueEntryResponse.builder()
                                                                    .scheduleId(scheduleId)
                                                                    .userId(userId)
                                                                    .position(position)
                                                                    .estimatedWaitMinutes(calculateEstimatedWait(position))
                                                                    .message(String.format("%d번째로 대기 중입니다", position))
                                                                    .build());
                                                });
                                    })
                            );
                })
                .doOnSuccess(response -> log.info("대기열 진입: userId={}, scheduleId={}, position={}",
                        userId, scheduleId, response.getPosition()));
    }

    /**
     * 대기열 상태 조회 (Polling용)
     * 토큰 보유 여부 우선 체크 → 큐 위치 조회
     */
    public Mono<QueueStatusResponse> getQueueStatus(Long scheduleId, Long userId) {
        String tokenKey = RedisKeyGenerator.tokenKey(userId, scheduleId);
        String queueKey = RedisKeyGenerator.queueKey(scheduleId);

        return redisTemplate.opsForValue().get(tokenKey)
                .flatMap(token -> Mono.just(QueueStatusResponse.builder()
                        .position(0L)
                        .status("READY")
                        .token(token)
                        .estimatedWaitMinutes(0)
                        .message("입장 가능합니다")
                        .build()))
                .switchIfEmpty(
                        redisTemplate.opsForZSet()
                                .rank(queueKey, userId.toString())
                                .map(rank -> rank + 1)
                                .map(position -> QueueStatusResponse.builder()
                                        .position(position)
                                        .status("WAITING")
                                        .estimatedWaitMinutes(calculateEstimatedWait(position))
                                        .aheadCount(position - 1)
                                        .message(String.format("앞에 %d명이 대기 중입니다", position - 1))
                                        .build())
                                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOT_IN_QUEUE)))
                );
    }

    /**
     * 대기열 취소
     */
    public Mono<Boolean> leaveQueue(Long scheduleId, Long userId) {
        String queueKey = RedisKeyGenerator.queueKey(scheduleId);
        String heartbeatKey = RedisKeyGenerator.heartbeatKey(scheduleId, userId);
        String activeKey = RedisKeyGenerator.activeKey(scheduleId);

        return redisTemplate.opsForZSet()
                .remove(queueKey, userId.toString())
                .flatMap(removed -> redisTemplate.opsForZSet().remove(activeKey, userId.toString())
                        .then(redisTemplate.delete(heartbeatKey))
                        .thenReturn(removed > 0))
                .doOnSuccess(success -> log.info("대기열 이탈: userId={}, scheduleId={}, success={}",
                        userId, scheduleId, success));
    }

    /**
     * Heartbeat 처리
     * 큐 대기자: heartbeat 키 TTL 갱신
     * 활성 유저: active SortedSet score 갱신 (하트비트 타임스탬프)
     */
    public Mono<Boolean> heartbeat(Long scheduleId, Long userId) {
        String heartbeatKey = RedisKeyGenerator.heartbeatKey(scheduleId, userId);
        String activeKey = RedisKeyGenerator.activeKey(scheduleId);
        Duration heartbeatTtl = Duration.ofSeconds(queueProperties.getHeartbeatTtlSeconds());
        double now = System.currentTimeMillis();

        Mono<Boolean> updateHeartbeat = redisTemplate.opsForValue()
                .set(heartbeatKey, "alive", heartbeatTtl);

        Mono<Boolean> updateActive = redisTemplate.opsForZSet()
                .score(activeKey, userId.toString())
                .flatMap(existingScore ->
                        redisTemplate.opsForZSet().add(activeKey, userId.toString(), now))
                .defaultIfEmpty(false);

        return updateHeartbeat.then(updateActive).thenReturn(true);
    }

    private Mono<Long> getPosition(Long scheduleId, Long userId) {
        String queueKey = RedisKeyGenerator.queueKey(scheduleId);

        return redisTemplate.opsForZSet()
                .rank(queueKey, userId.toString())
                .map(rank -> rank + 1);
    }

    private int calculateEstimatedWait(long position) {
        int batchSize = queueProperties.getBatchSize();
        if (position <= batchSize) {
            return 0;
        }
        return (int) Math.ceil((position - batchSize) / 50.0);
    }
}
