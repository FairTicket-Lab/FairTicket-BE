package com.fairticket.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 오류가 발생했습니다"),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A003", "토큰이 만료되었습니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "A004", "이미 사용 중인 이메일입니다"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "A005", "요청 한도를 초과했습니다"),

    // Queue
    NOT_IN_QUEUE(HttpStatus.NOT_FOUND, "Q001", "대기열에 존재하지 않습니다"),
    QUEUE_ENTRY_FAILED(HttpStatus.CONFLICT, "Q002", "대기열 진입에 실패했습니다"),
    INVALID_QUEUE_TOKEN(HttpStatus.FORBIDDEN, "Q003", "유효하지 않은 입장 토큰입니다"),

    // Reservation
    ALREADY_PARTICIPATED(HttpStatus.CONFLICT, "R001", "이미 참여한 공연입니다"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "예약을 찾을 수 없습니다"),
    RESERVATION_CANCELLED(HttpStatus.BAD_REQUEST, "R003", "취소된 예약입니다"),

    // Seat
    SEAT_ALREADY_TAKEN(HttpStatus.CONFLICT, "S001", "이미 선택된 좌석입니다"),
    SEAT_HOLD_EXPIRED(HttpStatus.GONE, "S002", "좌석 홀드가 만료되었습니다"),
    NO_AVAILABLE_SEATS(HttpStatus.NOT_FOUND, "S003", "잔여 좌석이 없습니다"),
    SOLD_OUT(HttpStatus.GONE, "S004", "매진되었습니다"),

    // Track
    SAMEDAY_TRACK_CLOSED(HttpStatus.FORBIDDEN, "T001", "당일 트랙이 마감되었습니다"),
    CART_TRACK_CLOSED(HttpStatus.FORBIDDEN, "T002", "장바구니 트랙이 마감되었습니다"),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "결제 정보를 찾을 수 없습니다"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "P002", "결제 금액이 일치하지 않습니다"),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "P003", "이미 완료된 결제입니다"),
    PAYMENT_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "P004", "결제 시간이 초과되었습니다"),
    REFUND_FAILED(HttpStatus.BAD_REQUEST, "P005", "환불 처리에 실패했습니다"),

    // Lock
    LOCK_ACQUISITION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "L001", "락 획득에 실패했습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
