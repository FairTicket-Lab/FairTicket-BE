package com.fairticket.domain.seat.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SeatSelectionResponse {
    private Long scheduleId;
    private String grade;
    private String seatNumber;
    private LocalDateTime holdExpiresAt;
    private String message;
}
