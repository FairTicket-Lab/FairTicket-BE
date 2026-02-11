package com.fairticket.domain.seat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SeatSelectionResponse {
    private Long scheduleId;
    private String grade;
    private String zone;
    private String seatNumber;
    private LocalDateTime holdExpiresAt;
    // 결제 마감 시각 (라이브/추첨 공통 5분. TODO(결제): 미결제 시 취소 처리 구현)
    private LocalDateTime paymentDeadline;
    private String message;
}
