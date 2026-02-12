package com.fairticket.domain.concert.controller;

import com.fairticket.domain.concert.dto.GradeResponse;
import com.fairticket.domain.concert.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // 등급 목록 조회 (추첨/라이브 트랙 등급 선택 단계용)
    @GetMapping("/{scheduleId}/grades")
    public Mono<ResponseEntity<List<GradeResponse>>> getGrades(
            @PathVariable Long scheduleId) {
        return scheduleService.getGradesByScheduleId(scheduleId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    // 등급별 구역 목록 조회 (등급 선택 → 구역 선택 플로우, 추첨/라이브 공통)
    @GetMapping("/{scheduleId}/grades/{grade}/zones")
    public Mono<ResponseEntity<List<String>>> getZonesByGrade(
            @PathVariable Long scheduleId,
            @PathVariable String grade) {
        return scheduleService.getZonesByGrade(scheduleId, grade)
                .map(ResponseEntity::ok);
    }
}
