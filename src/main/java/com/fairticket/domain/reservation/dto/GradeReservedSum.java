package com.fairticket.domain.reservation.dto;

// 등급별 추첨 예약 수량 합계 (GROUP BY grade 쿼리용).
public interface GradeReservedSum {
    String getGrade();
    long getTotal();
}
