package com.fairticket.global.util;

public class RedisKeyGenerator {

    public static String queue(Long scheduleId) {
        return String.format("queue:%d", scheduleId);
    }

    public static String token(Long userId, Long scheduleId) {
        return String.format("token:%d:%d", userId, scheduleId);
    }

    public static String heartbeat(Long scheduleId, Long userId) {
        return String.format("heartbeat:%d:%d", scheduleId, userId);
    }

    public static String seatPool(Long scheduleId, String grade) {
        return String.format("seats:%d:%s", scheduleId, grade);
    }

    public static String stock(Long scheduleId, String grade) {
        return String.format("stock:%d:%s", scheduleId, grade);
    }

    public static String seatHold(Long scheduleId, String grade, String seatNumber) {
        return String.format("hold:%d:%s:%s", scheduleId, grade, seatNumber);
    }

    public static String soldSameday(Long scheduleId) {
        return String.format("sold:sameday:%d", scheduleId);
    }

    public static String remaining(Long scheduleId) {
        return String.format("remaining:%d", scheduleId);
    }

    public static String lockAssign(Long scheduleId, String grade) {
        return String.format("lock:assign:%d:%s", scheduleId, grade);
    }

    public static String cartPaid(Long scheduleId) {
        return String.format("cart-paid:%d", scheduleId);
    }

    public static String paymentTimer(Long reservationId) {
        return String.format("payment-timer:%d", reservationId);
    }
}