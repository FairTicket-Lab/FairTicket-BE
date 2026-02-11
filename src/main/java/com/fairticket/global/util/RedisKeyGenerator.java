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

    // 구역별 잔여 좌석 풀 키 (Set) - seats:{scheduleId}:{zone}
    public static String seatsKey(Long scheduleId, String zone) {
        return String.format("seats:%d:%s", scheduleId, zone);
    }

    // 좌석 임시 홀드 키 (String+TTL, 660초) - hold:{scheduleId}:{zone}:{seatNo}
    public static String holdKey(Long scheduleId, String zone, String seatNo) {
        return String.format("hold:%d:%s:%s", scheduleId, zone, seatNo);
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

    // 추첨 결제 성공자 목록 키 (Set) - lottery-paid:{scheduleId}
    public static String lotteryPaidKey(Long scheduleId) {
        return String.format("lottery-paid:%d", scheduleId);
    }

    // 미결제 자동 취소 타이머 키 (String+TTL) - payment-timer:{reservationId}
    // 라이브/추첨 공통: 예약 후 5분 이내 미결제 시 취소 처리.
    // TODO(결제): 예약 생성 시 이 키로 TTL 5분 설정, 만료 시 PENDING 예약 취소 스케줄러/리스너 구현.
    public static String paymentTimerKey(Long reservationId) {
        return String.format("payment-timer:%d", reservationId);
    }

    // 라이브 트랙 마감 시각 (값=epoch millis, 취소 가능 기간 계산용) - live-closed:{scheduleId}
    public static String liveClosedKey(Long scheduleId) {
        return String.format("live-closed:%d", scheduleId);
    }

    // 대기열이 0이 된 시각 (0이 10분 지속 시 라이브 마감 판단용) - queue-zero-since:{scheduleId}
    public static String queueZeroSinceKey(Long scheduleId) {
        return String.format("queue-zero-since:%d", scheduleId);
    }

    // 추첨 좌석 배정 완료 플래그 (라이브 마감 후 1회만 배정 실행) - lottery-assigned:{scheduleId}
    public static String lotteryAssignedKey(Long scheduleId) {
        return String.format("lottery-assigned:%d", scheduleId);
    }
}
