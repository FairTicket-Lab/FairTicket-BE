package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

// 라이브 트랙 마감 조건:
// 1. 라이브 시작(티켓 오픈) 1시간 경과 시 마감
// 2. (보조) 대기열 0인 상태가 10분 이상 지속 시 마감. 단, 오픈 후 30분이 지난 뒤에만 적용
// 추첨 좌석 배정은 라이브 트랙 시작(티켓 오픈) LOTTERY_ASSIGNMENT_DELAY_MINUTES_AFTER_OPEN분 후로 고정되어 실행된다.   
// 배정 완료 시 사용자에게 알림을 보낸다 (TODO(알림): 알림 담당자 구현)
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveTrackCloseScheduler {

    // 라이브 트랙 시작(티켓 오픈) 후 이 시간(분)이 지나면 마감
    private static final int LIVE_TRACK_DURATION_MINUTES = 60;
    // 대기열 0인 상태가 이 시간(분) 이상 지속되면 마감 (보조 조건)
    private static final int QUEUE_ZERO_DURATION_MINUTES = 10;
    // 이 시간(분) 미만이면 오픈 직후로 간주하여, 대기열 0 지속만으로는 마감하지 않음
    private static final int MIN_OPEN_DURATION_MINUTES = 30;
    // 추첨 좌석 배정 실행 시점: 라이브 트랙 시작(티켓 오픈) 후 이 시간(분) 경과 시
    private static final int LOTTERY_ASSIGNMENT_DELAY_MINUTES_AFTER_OPEN = 60;

    private final ScheduleRepository scheduleRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final LotteryTrackService lotteryTrackService;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void checkAndCloseLiveTrackByQueueEmpty() {
        LocalDateTime now = LocalDateTime.now();
        // 티켓 오픈이 이미 지난 회차만 조회
        scheduleRepository.findByTicketOpenAtLessThanEqual(now)
                .flatMap(schedule -> {
                    Long scheduleId = schedule.getId();
                    String queueKey = RedisKeyGenerator.queueKey(scheduleId);
                    String zeroSinceKey = RedisKeyGenerator.queueZeroSinceKey(scheduleId);
                    String closedKey = RedisKeyGenerator.liveClosedKey(scheduleId);
                    long openDurationMin = Duration.between(schedule.getTicketOpenAt(), now).toMinutes();

                    // 1) 라이브 시작 1시간 경과 시 마감
                    if (openDurationMin >= LIVE_TRACK_DURATION_MINUTES) {
                        return redisTemplate.hasKey(closedKey)
                                .flatMap(alreadyClosed -> Boolean.TRUE.equals(alreadyClosed)
                                        ? Mono.just(scheduleId)
                                        : closeLiveTrackAndAssignLotteryIfNeeded(scheduleId, closedKey, zeroSinceKey)
                                                .doOnSuccess(v -> log.info("라이브 트랙 마감(시작 1시간 경과): scheduleId={}", scheduleId))
                                                .thenReturn(scheduleId));
                    }

                    return redisTemplate.opsForZSet().size(queueKey)
                            .flatMap(queueSize -> {
                                if (queueSize != null && queueSize > 0) {
                                    return redisTemplate.delete(zeroSinceKey).thenReturn(scheduleId);
                                }
                                return redisTemplate.opsForValue().get(zeroSinceKey)
                                        .switchIfEmpty(Mono.defer(() -> {
                                            long nowMs = System.currentTimeMillis();
                                            return redisTemplate.opsForValue().set(zeroSinceKey, String.valueOf(nowMs))
                                                    .thenReturn(String.valueOf(nowMs));
                                        }))
                                        .flatMap(tsStr -> {
                                            long zeroSince = Long.parseLong(tsStr);
                                            long elapsedMin = (System.currentTimeMillis() - zeroSince) / (60 * 1000);
                                            boolean canCloseByQueueEmpty = elapsedMin >= QUEUE_ZERO_DURATION_MINUTES
                                                    && openDurationMin >= MIN_OPEN_DURATION_MINUTES;
                                            if (canCloseByQueueEmpty) {
                                                return closeLiveTrackAndAssignLotteryIfNeeded(scheduleId, closedKey, zeroSinceKey)
                                                        .doOnSuccess(v -> log.info("라이브 트랙 마감(대기열 0 지속 {}분): scheduleId={}", QUEUE_ZERO_DURATION_MINUTES, scheduleId))
                                                        .thenReturn(scheduleId);
                                            }
                                            return Mono.just(scheduleId);
                                        })
                                        .switchIfEmpty(Mono.just(scheduleId));
                                });
                })
                .doOnSubscribe(s -> log.debug("라이브 트랙 대기열 0 마감 체크 시작"))
                .onErrorResume(e -> {
                    log.warn("라이브 트랙 마감 스케줄러 오류: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    // 추첨 좌석 배정: 라이브 트랙 시작(티켓 오픈) LOTTERY_ASSIGNMENT_DELAY_MINUTES_AFTER_OPEN분 후로 고정.
    // 1분마다 스캔하여, 오픈 후 1시간이 지났고 아직 배정되지 않은 회차에 대해 배정 실행 및 알림 호출.
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void assignLotterySeatsAtFixedTimeAfterOpen() {
        LocalDateTime now = LocalDateTime.now();
        // 티켓 오픈 후 60분이 지난 회차만 조회 (추첨 좌석 배정 시점)
        LocalDateTime threshold = now.minusMinutes(LOTTERY_ASSIGNMENT_DELAY_MINUTES_AFTER_OPEN);
        scheduleRepository.findByTicketOpenAtLessThanEqual(threshold)
                .flatMap(schedule -> {
                    Long scheduleId = schedule.getId();
                    String lotteryAssignedKey = RedisKeyGenerator.lotteryAssignedKey(scheduleId);
                    return redisTemplate.hasKey(lotteryAssignedKey)
                            .filter(assigned -> Boolean.FALSE.equals(assigned))
                            .flatMap(ignored -> lotteryTrackService.assignSeatsToPaidLotteryAndMerge(scheduleId)
                                    .then(redisTemplate.opsForValue().set(lotteryAssignedKey, "1", Duration.ofDays(1)))
                                    .then(notifyLotterySeatAssignmentComplete(scheduleId))
                                    .doOnSuccess(v -> log.info("추첨 좌석 배정 실행 완료(오픈 {}분 후): scheduleId={}", LOTTERY_ASSIGNMENT_DELAY_MINUTES_AFTER_OPEN, scheduleId)));
                })
                .doOnSubscribe(s -> log.debug("추첨 좌석 배정 스캔 시작"))
                .onErrorResume(e -> {
                    log.warn("추첨 좌석 배정 스케줄러 오류: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    // 라이브 마감 플래그 설정 및 대기열 0 타이머 초기화.
    // 추첨 좌석 배정은 별도 스케줄러(오픈 1시간 후 고정)에서 수행한다.
    private Mono<Void> closeLiveTrackAndAssignLotteryIfNeeded(Long scheduleId, String closedKey, String zeroSinceKey) {
        long closedAtMs = System.currentTimeMillis();
        return redisTemplate.opsForValue().set(closedKey, String.valueOf(closedAtMs), Duration.ofDays(2))
                .then(zeroSinceKey != null ? redisTemplate.delete(zeroSinceKey) : Mono.empty())
                .then();
    }

    // 추첨 좌석 배정이 완료되었을 때 해당 회차 당첨 사용자에게 알림을 보낸다.
    // TODO(알림): 푸시/이메일/SMS 등 - 당첨자 목록 조회 후 발송. 알림 담당자 구현.
    private Mono<Void> notifyLotterySeatAssignmentComplete(Long scheduleId) {
        // TODO(알림): 해당 scheduleId의 추첨 결제 완료자 목록 조회 후 알림 발송
        log.info("추첨 좌석 배정 완료 알림 대상: scheduleId={}", scheduleId);
        return Mono.empty();
    }
}
