package com.fairticket.global.util;

// Redis 키 생성을 위한 유틸리티 클래스
public class RedisKeyGenerator {

    private RedisKeyGenerator() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }

    // 대기열 순번 관리 키 (SortedSet) - queue:{scheduleId}
    public static String queueKey(Long scheduleId) {
        return String.format("queue:%d", scheduleId);
    }

    // 등급별 잔여 좌석 풀 키 (Set) - seats:{scheduleId}:{grade}
    public static String seatsKey(Long scheduleId, String grade) {
        return String.format("seats:%d:%s", scheduleId, grade);
    }

    // 라이브 트랙 잔여 좌석 수 키 (String) - remaining:{scheduleId}
    public static String remainingKey(Long scheduleId) {
        return String.format("remaining:%d", scheduleId);
    }

    // 라이브 트랙 판매 수 키 (String) - sold:live:{scheduleId}
    public static String soldLiveKey(Long scheduleId) {
        return String.format("sold:live:%d", scheduleId);
    }

    // 좌석 임시 홀드 키 (String+TTL, 660초) - hold:{scheduleId}:{grade}:{seatNo}
    public static String holdKey(Long scheduleId, String grade, String seatNo) {
        return String.format("hold:%d:%s:%s", scheduleId, grade, seatNo);
    }

    // 입장 토큰 키 (Token+TTL, 300초) - token:{userId}:{scheduleId}
    public static String tokenKey(Long userId, Long scheduleId) {
        return String.format("token:%d:%d", userId, scheduleId);
    }

    // 좌석 배정 분산 락 키 (Lock) - lock:assign:{scheduleId}:{grade}
    public static String lockAssignKey(Long scheduleId, String grade) {
        return String.format("lock:assign:%d:%s", scheduleId, grade);
    }

    // 대기열 이탈 감지 키 (String+TTL, 30초) - heartbeat:{scheduleId}:{userId}
    public static String heartbeatKey(Long scheduleId, Long userId) {
        return String.format("heartbeat:%d:%d", scheduleId, userId);
    }

    // 동시 입장 인원 수 키 (String) - active:{scheduleId}
    public static String activeKey(Long scheduleId) {
        return String.format("active:%d", scheduleId);
    }

    // 장바구니 결제 성공자 목록 키 (Set) - cart-paid:{scheduleId}
    public static String cartPaidKey(Long scheduleId) {
        return String.format("cart-paid:%d", scheduleId);
    }

    // 미결제 자동 취소 타이머 키 (String+TTL) - payment-timer:{reservationId}
    public static String paymentTimerKey(Long reservationId) {
        return String.format("payment-timer:%d", reservationId);
    }
}
