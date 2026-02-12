package com.fairticket.domain.reservation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// 추첨 예약 당첨/미당첨 결과 조회용
@Getter
@Builder
public class LotteryResultResponse {
    private Long id;
    private Long scheduleId;
    private String grade;
    private Integer quantity;
    private String status;

    // 결과 구분: PAYMENT_PENDING(결제대기), WON(당첨·결제완료), ASSIGNED(좌석배정완료), LOST(미당첨·취소)
    private String resultType;
    private String message;

    private LocalDateTime paymentDeadline;
    // ASSIGNED일 때 배정된 좌석 목록 (구역-좌석번호)
    private List<AssignedSeatDto> seats;

    @Getter
    @Builder
    public static class AssignedSeatDto {
        private String zone;
        private String seatNumber;
    }
}
