package com.fairticket.domain.seat.dto;

// 등급별 좌석 수 집계 결과 (GROUP BY grade 쿼리용)
public interface GradeSeatCount {
    String getGrade();
    long getCount();
}
