package com.fairticket.domain.concert.repository;

import com.fairticket.domain.concert.entity.Concert;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ConcertRepository extends ReactiveCrudRepository<Concert, Long> {
}
