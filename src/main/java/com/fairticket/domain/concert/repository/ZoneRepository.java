package com.fairticket.domain.concert.repository;

import com.fairticket.domain.concert.entity.Zone;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ZoneRepository extends ReactiveCrudRepository<Zone, Long> {

    Flux<Zone> findByScheduleId(Long scheduleId);

    Flux<Zone> findByScheduleIdAndGrade(Long scheduleId, String grade);

    Mono<Zone> findByScheduleIdAndZone(Long scheduleId, String zone);

    // 선택한 등급에 해당 구역이 속하는지 검증
    Mono<Zone> findByScheduleIdAndGradeAndZone(Long scheduleId, String grade, String zone);
}
