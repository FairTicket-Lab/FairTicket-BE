package com.fairticket.global.util;

public final class RedisKeyGenerator {

    private RedisKeyGenerator() {}

    // ===== 대기열 =====
    private static final String QUEUE_PREFIX = "queue:";
    private static final String QUEUE_TOKEN_PREFIX = "queue-token:";
    private static final String HEARTBEAT_PREFIX = "heartbeat:";
    private static final String ACTIVE_PREFIX = "active:";

    public static String queue(Long scheduleId) {
        return QUEUE_PREFIX + scheduleId;
    }

    public static String queueToken(Long userId, Long scheduleId) {
        return QUEUE_TOKEN_PREFIX + userId + ":" + scheduleId;
    }

    public static String heartbeat(Long scheduleId, Long userId) {
        return HEARTBEAT_PREFIX + scheduleId + ":" + userId;
    }

    public static String active(Long scheduleId) {
        return ACTIVE_PREFIX + scheduleId;
    }

    // ===== 좌석 =====
    private static final String SEATS_PREFIX = "seats:";
    private static final String HOLD_PREFIX = "hold:";
    private static final String LOCK_ASSIGN_PREFIX = "lock:assign:";

    public static String seats(Long scheduleId, String zone) {
        return SEATS_PREFIX + scheduleId + ":" + zone;
    }

    public static String hold(Long scheduleId, String zone, String seatNo) {
        return HOLD_PREFIX + scheduleId + ":" + zone + ":" + seatNo;
    }

    public static String lockAssign(Long scheduleId, String grade) {
        return LOCK_ASSIGN_PREFIX + scheduleId + ":" + grade;
    }

    // ===== 트랙 =====
    private static final String LOTTERY_PAID_PREFIX = "lottery-paid:";
    private static final String LIVE_CLOSED_PREFIX = "live-closed:";
    private static final String QUEUE_ZERO_SINCE_PREFIX = "queue-zero-since:";
    private static final String LOTTERY_ASSIGNED_PREFIX = "lottery-assigned:";

    public static String lotteryPaid(Long scheduleId) {
        return LOTTERY_PAID_PREFIX + scheduleId;
    }

    public static String liveClosed(Long scheduleId) {
        return LIVE_CLOSED_PREFIX + scheduleId;
    }

    public static String queueZeroSince(Long scheduleId) {
        return QUEUE_ZERO_SINCE_PREFIX + scheduleId;
    }

    public static String lotteryAssigned(Long scheduleId) {
        return LOTTERY_ASSIGNED_PREFIX + scheduleId;
    }

    // ===== 결제 =====
    private static final String PAYMENT_TIMER_PREFIX = "payment-timer:";

    public static String paymentTimer(Long reservationId) {
        return PAYMENT_TIMER_PREFIX + reservationId;
    }
}
