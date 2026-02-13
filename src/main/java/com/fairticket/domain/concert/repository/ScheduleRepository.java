package com.fairticket.domain.concert.repository;

import com.fairticket.domain.concert.entity.Schedule;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface ScheduleRepository extends ReactiveCrudRepository<Schedule, Long> {

    Flux<Schedule> findByConcertId(Long concertId);

    // 티켓 오픈 시각이 주어진 시각 이전(이하)인 회차만 조회. 스케줄러에서 활성 회차만 처리할 때 사용 
    Flux<Schedule> findByTicketOpenAtLessThanEqual(LocalDateTime time);
}
