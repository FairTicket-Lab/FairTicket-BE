package com.fairticket.domain.reservation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 취소 가능 기간 (티켓 오픈 2시간 후 ~ 24시간)
@Getter
@Builder
public class CancellationWindowResponse {
    private final boolean allowed;
    private final LocalDateTime windowStart;
    private final LocalDateTime windowEnd;
    private final String message;
}
