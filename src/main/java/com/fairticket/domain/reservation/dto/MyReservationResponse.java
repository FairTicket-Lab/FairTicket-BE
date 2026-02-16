package com.fairticket.domain.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "마이페이지 예매 내역 응답")
public class MyReservationResponse {

    @Schema(description = "예약 ID")
    private Long id;

    @Schema(description = "공연 일정 ID")
    private Long scheduleId;

    @Schema(description = "좌석 등급")
    private String grade;

    @Schema(description = "예약 수량")
    private Integer quantity;

    @Schema(description = "트랙 타입 (LOTTERY/LIVE)")
    private String trackType;

    @Schema(description = "예약 상태")
    private String status;

    @Schema(description = "배정된 좌석 목록")
    private List<SeatInfo> seats;

    @Schema(description = "예약 생성 시각")
    private LocalDateTime createdAt;

    @Getter
    @Builder
    public static class SeatInfo {
        @Schema(description = "구역")
        private String zone;

        @Schema(description = "좌석 번호")
        private String seatNumber;

        @Schema(description = "좌석 상태 (PENDING/ASSIGNED/CANCELLED)")
        private String status;
    }
}
