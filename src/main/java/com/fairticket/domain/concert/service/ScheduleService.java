package com.fairticket.domain.concert.service;

import com.fairticket.domain.concert.dto.GradeResponse;
import com.fairticket.domain.concert.entity.Schedule;
import com.fairticket.domain.concert.repository.GradeRepository;
import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.domain.concert.repository.ZoneRepository;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final GradeRepository gradeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ZoneRepository zoneRepository;

    // 스케줄이 없으면 SCHEDULE_NOT_FOUND 예외. 여러 API에서 공통 사용 
    public Mono<Schedule> findScheduleOrThrow(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)));
    }

    // 스케줄별 등급 목록 조회 (추첨/라이브 트랙 등급 선택용) 
    public Flux<GradeResponse> getGradesByScheduleId(Long scheduleId) {
        return gradeRepository.findByScheduleId(scheduleId)
                .map(g -> GradeResponse.builder()
                        .grade(g.getGrade())
                        .price(g.getPrice())
                        .build());
    }

    // 선택한 등급에 속한 구역 목록 (등급 선택 후 구역 선택용, 라이브/추첨 공통)
    public Mono<List<String>> getZonesByGrade(Long scheduleId, String grade) {
        return zoneRepository.findByScheduleIdAndGrade(scheduleId, grade)
                .map(zone -> zone.getZone())
                .collectList();
    }

    // 등급·구역 검증: 해당 구역이 등급에 속하는지 확인. 없으면 INVALID_GRADE_ZONE 
    public Mono<Void> validateGradeAndZone(Long scheduleId, String grade, String zone) {
        return zoneRepository.findByScheduleIdAndGradeAndZone(scheduleId, grade, zone)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.INVALID_GRADE_ZONE)))
                .then();
    }

    // 스케줄의 모든 구역명 목록 (잔여석 집계 등에 사용)
    public Flux<String> getZoneNamesByScheduleId(Long scheduleId) {
        return zoneRepository.findByScheduleId(scheduleId).map(zone -> zone.getZone());
    }
}
