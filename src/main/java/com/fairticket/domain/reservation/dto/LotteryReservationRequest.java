package com.fairticket.domain.reservation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LotteryReservationRequest {
    private Long scheduleId;
    private String grade;
    private Integer quantity; 
}
