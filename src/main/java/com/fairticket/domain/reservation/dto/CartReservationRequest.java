package com.fairticket.domain.reservation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartReservationRequest {
    private Long scheduleId;
    private String grade;
    private Integer quantity; 
}
