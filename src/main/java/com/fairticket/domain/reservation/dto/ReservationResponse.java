package com.fairticket.domain.reservation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReservationResponse {
    private Long id;
    private Long scheduleId;
    private String grade;
    private Integer quantity;
    private String seatNumbers;
    private String trackType;
    private String status;
    private String message;
    private LocalDateTime paymentDeadline;
}
