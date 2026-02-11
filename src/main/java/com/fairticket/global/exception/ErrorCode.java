package com.fairticket.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Payment 관련
    PAYMENT_NOT_FOUND("P001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH("P002", "결제 금액이 일치하지 않습니다."),

    // Reservation 관련
    ALREADY_PARTICIPATED("R001", "이미 참여한 공연입니다."),
    RESERVATION_NOT_FOUND("R002", "예약 정보를 찾을 수 없습니다."),

    // 공통
    INTERNAL_SERVER_ERROR("C002", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String message;
}