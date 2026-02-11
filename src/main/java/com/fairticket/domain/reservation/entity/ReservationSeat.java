package com.fairticket.domain.reservation.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("reservation_seats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSeat {

    @Id
    private Long id;

    private Long reservationId;

    private Long seatId;

    // 구역 (라이브: 선택 구역, 추첨: 배정된 구역)
    private String zone;

    // Redis 풀에서 배정된 좌석 번호 (추첨 배정 시) 또는 선택 좌석 번호 (라이브)
    private String seatNumber;

    private String status;

    private LocalDateTime assignedAt;

    private LocalDateTime createdAt;
}
