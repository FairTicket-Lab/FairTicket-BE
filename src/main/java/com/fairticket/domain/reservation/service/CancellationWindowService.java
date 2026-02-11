package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.entity.Schedule;
import com.fairticket.domain.concert.service.ScheduleService;
import com.fairticket.domain.reservation.dto.CancellationWindowResponse;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 좌석 취소 가능 기간: 라이브 트랙 오픈(티켓 오픈) 시각 기준 2시간 후부터 24시간 동안.
// 두 트랙(추첨/라이브) 모두 동일한 기간에 취소 가능.
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationWindowService {

    // 티켓 오픈 후 취소 가능 시작까지 대기 시간(시간)
    private static final int CANCELLATION_START_HOURS_AFTER_OPEN = 2;
    // 취소 가능 기간(시간)
    private static final int CANCELLATION_DURATION_HOURS = 24;

    private final ScheduleService scheduleService;

    // 해당 회차가 취소 가능 기간인지 검사. 아니면 예외 발생.
    public Mono<Void> validateCancellationAllowed(Long scheduleId) {
        return scheduleService.findScheduleOrThrow(scheduleId)
                .flatMap(schedule -> {
                    long startMs = toEpochMillis(schedule.getTicketOpenAt()) + (CANCELLATION_START_HOURS_AFTER_OPEN * 3600L * 1000L);
                    long endMs = startMs + (CANCELLATION_DURATION_HOURS * 3600L * 1000L);
                    long nowMs = System.currentTimeMillis();
                    if (nowMs < startMs) {
                        return Mono.<Void>error(new BusinessException(ErrorCode.CANCELLATION_NOT_AVAILABLE));
                    }
                    if (nowMs > endMs) {
                        return Mono.<Void>error(new BusinessException(ErrorCode.CANCELLATION_WINDOW_EXPIRED));
                    }
                    return Mono.empty();
                });
    }

    // 취소 가능 여부 및 기간 정보 조회
    public Mono<CancellationWindowResponse> getCancellationWindow(Long scheduleId) {
        return scheduleService.findScheduleOrThrow(scheduleId)
                .map(this::buildWindowResponse);
    }

    private CancellationWindowResponse buildWindowResponse(Schedule schedule) {
        long startMs = toEpochMillis(schedule.getTicketOpenAt()) + (CANCELLATION_START_HOURS_AFTER_OPEN * 3600L * 1000L);
        long endMs = startMs + (CANCELLATION_DURATION_HOURS * 3600L * 1000L);
        long nowMs = System.currentTimeMillis();
        LocalDateTime windowStart = toLocalDateTime(startMs);
        LocalDateTime windowEnd = toLocalDateTime(endMs);
        boolean allowed = nowMs >= startMs && nowMs <= endMs;
        String message = allowed
                ? "취소 가능 기간입니다."
                : (nowMs < startMs
                        ? "티켓 오픈 2시간 후부터 취소 가능합니다."
                        : "취소 가능 기간이 지났습니다.");
        return CancellationWindowResponse.builder()
                .allowed(allowed)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .message(message)
                .build();
    }

    public Mono<Boolean> isCancellationAllowed(Long scheduleId) {
        return getCancellationWindow(scheduleId).map(CancellationWindowResponse::isAllowed);
    }

    private static long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
