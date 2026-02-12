package com.fairticket.domain.reservation.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("reservations")
public class Reservation {

    @Id
    private Long id;

    private Long userId;
    private Long scheduleId;

    // 좌석 정보
    private String grade;                    // VIP, R, S, A
    private Integer quantity;                // 좌석 수량

    // ERD v4.0: needsConsecutiveSeats/seatNumbers → needsConfirm/confirmDeadline으로 변경
    private Boolean needsConfirm;            // 연석 분리 배정 시 사용자 확인 필요 여부
    private LocalDateTime confirmDeadline;   // 확인 마감 시각 (24시간)

    // 트랙 정보
    private String trackType;                // LOTTERY, LIVE
    private String status;                   // PENDING, PAID, PAID_PENDING_SEAT, ASSIGNED, CANCELLED, REFUNDED

    // 시간 정보
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}