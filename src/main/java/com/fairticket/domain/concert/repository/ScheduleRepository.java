package com.fairticket.domain.concert.repository;

import com.fairticket.domain.concert.entity.Schedule;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ScheduleRepository extends ReactiveCrudRepository<Schedule, Long> {
}
