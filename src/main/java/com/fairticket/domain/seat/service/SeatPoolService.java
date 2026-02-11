package com.fairticket.domain.seat.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SeatPoolService {

    //좌석 반환 (취소/타임아웃 시)
    public Mono<Boolean> returnSeat(Long scheduleId, String grade, String seatNumber) {
        // TODO: B 구현 대기
        return Mono.just(true);
    }
}