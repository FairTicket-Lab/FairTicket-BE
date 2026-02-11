package com.fairticket.domain.reservation.constants;

// TIMELINE.md 기준 예매·결제·홀드 관련 공통 상수.
// 타임라인 변경 시 이 클래스만 수정하면 된다.
public final class ReservationConstants {

    private ReservationConstants() {
        throw new UnsupportedOperationException("상수 클래스는 인스턴스화할 수 없습니다.");
    }

    // 결제 유효 시간 (분). 미결제 시 자동 취소 (추첨/라이브 공통)
    public static final int PAYMENT_DEADLINE_MINUTES = 5;

    // 좌석 홀드 최대 유지 시간 (분). 라이브 트랙 좌석 선택 후 미결제 시 이 시간 후 자동 해제
    public static final int HOLD_MINUTES = 10;

    // 추첨 트랙 열림: 티켓 오픈 N분 전
    public static final int LOTTERY_OPEN_MINUTES = 30;
    // 추첨 트랙 진입 마감: 티켓 오픈 N분 전
    public static final int LOTTERY_ENTRY_CLOSE_MINUTES = 20;
    // 추첨 트랙 결제 마감: 티켓 오픈 N분 전
    public static final int LOTTERY_PAYMENT_CLOSE_MINUTES = 15;

    // 추첨 트랙 1인당 최대 수량
    public static final int LOTTERY_MAX_QUANTITY_PER_USER = 2;
    // 라이브 트랙 1인당 최대 수량
    public static final int LIVE_MAX_QUANTITY_PER_USER = 4;

    // 결제 대기 메시지 (추첨)
    public static final String MESSAGE_PAYMENT_DEADLINE_LOTTERY =
            "결제를 진행해주세요. " + PAYMENT_DEADLINE_MINUTES + "분 내 결제하지 않으면 자동 취소됩니다.";
    // 결제 대기 메시지 (라이브, 홀드 해제 안내)
    public static final String MESSAGE_PAYMENT_DEADLINE_LIVE =
            "좌석이 최대 " + HOLD_MINUTES + "분간 홀드됩니다. " + PAYMENT_DEADLINE_MINUTES + "분 내 결제 시 홀드가 해제됩니다.";
}
