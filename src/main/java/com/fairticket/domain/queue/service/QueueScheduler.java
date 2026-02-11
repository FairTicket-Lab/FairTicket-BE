package com.fairticket.domain.queue.service;

import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final QueueTokenService queueTokenService;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ACTIVE_USERS = 500;

    /**
     * 배치 입장 처리 (5초마다)
     * active 인원이 MAX_ACTIVE_USERS 미만이면 대기열에서 다음 배치 입장
     */
    @Scheduled(fixedDelay = 5000)
    public void processBatchEntry() {
        redisTemplate.keys("queue:*")
                .flatMap(queueKey -> {
                    String scheduleId = queueKey.split(":")[1];
                    String activeKey = RedisKeyGenerator.active(Long.parseLong(scheduleId));

                    return redisTemplate.opsForValue().get(activeKey)
                            .defaultIfEmpty("0")
                            .map(Integer::parseInt)
                            .flatMapMany(activeCount -> {
                                if (activeCount >= MAX_ACTIVE_USERS) {
                                    return Flux.empty();
                                }

                                int allowCount = Math.min(BATCH_SIZE, MAX_ACTIVE_USERS - activeCount);

                                return redisTemplate.opsForZSet()
                                        .range(queueKey, org.springframework.data.domain.Range.closed(0L, (long) allowCount - 1))
                                        .flatMap(userId -> {
                                            Long uid = Long.parseLong(userId);
                                            Long sid = Long.parseLong(scheduleId);

                                            return queueTokenService.issueToken(uid, sid)
                                                    .then(redisTemplate.opsForZSet().remove(queueKey, userId))
                                                    .then(redisTemplate.opsForValue().increment(activeKey))
                                                    .doOnSuccess(v -> log.info("배치 입장: userId={}, scheduleId={}", uid, sid));
                                        });
                            });
                })
                .subscribe();
    }

    /**
     * Heartbeat 미갱신 사용자 대기열 제거 (10초마다)
     */
    @Scheduled(fixedDelay = 10000)
    public void cleanupInactiveUsers() {
        redisTemplate.keys("queue:*")
                .flatMap(queueKey -> {
                    String scheduleId = queueKey.split(":")[1];
                    Long sid = Long.parseLong(scheduleId);

                    return redisTemplate.opsForZSet()
                            .range(queueKey, org.springframework.data.domain.Range.closed(0L, -1L))
                            .flatMap(userId -> {
                                String heartbeatKey = RedisKeyGenerator.heartbeat(sid, Long.parseLong(userId));

                                return redisTemplate.hasKey(heartbeatKey)
                                        .flatMap(hasHeartbeat -> {
                                            if (!hasHeartbeat) {
                                                return redisTemplate.opsForZSet()
                                                        .remove(queueKey, userId)
                                                        .doOnSuccess(v -> log.info("비활성 사용자 제거: userId={}, scheduleId={}",
                                                                userId, scheduleId));
                                            }
                                            return reactor.core.publisher.Mono.empty();
                                        });
                            });
                })
                .subscribe();
    }
}
