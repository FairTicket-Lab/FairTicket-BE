package com.fairticket.domain.concert.repository;

import com.fairticket.domain.concert.entity.Grade;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface GradeRepository extends ReactiveCrudRepository<Grade, Long> {

    Flux<Grade> findByScheduleId(Long scheduleId);
}
