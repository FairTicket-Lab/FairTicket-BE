package com.fairticket.domain.concert.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("schedule_grades")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGrade {

    @Id
    private Long id;

    private Long scheduleId;

    private String grade;

    private Integer seatCount;

    private Integer price;
}
