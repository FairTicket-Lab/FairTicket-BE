package com.fairticket.domain.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentTimerResponse {

    private Long reservationId;
    private Long remainingSeconds;
    private boolean expired;
}