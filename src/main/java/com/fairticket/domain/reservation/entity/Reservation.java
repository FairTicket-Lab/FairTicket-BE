package com.fairticket.domain.reservation.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("reservations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    private Long id;

    private Long userId;

    private Long scheduleId;

    private String grade;

    private String trackType;

    private String status;

    private Integer quantity;

    private Boolean needsConfirm;

    private LocalDateTime confirmDeadline;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
