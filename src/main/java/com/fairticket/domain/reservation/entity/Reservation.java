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
    private Boolean needsConsecutiveSeats;   // 연속 좌석 필요 여부
    private String seatNumbers;              // ⭐ 추가! (예: "127,128" 또는 "127")

    // 트랙 정보
    private String trackType;                // CART, SAME_DAY
    private String status;                   // PENDING, PAID_PENDING_SEAT, CONFIRMED, CANCELLED

    // 시간 정보
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}