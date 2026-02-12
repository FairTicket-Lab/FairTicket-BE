package com.fairticket.domain.reservation.controller;

import com.fairticket.domain.reservation.dto.LotteryReservationRequest;
import com.fairticket.domain.reservation.dto.LotteryResultResponse;
import com.fairticket.domain.reservation.dto.ReservationResponse;
import com.fairticket.domain.reservation.service.LotteryTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lottery")
@RequiredArgsConstructor
public class LotteryTrackController {
    private final LotteryTrackService lotteryTrackService;

    // 추첨 예약 생성
    @PostMapping("/{scheduleId}")
    public Mono<ResponseEntity<ReservationResponse>> createReservation(
            @RequestBody LotteryReservationRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Queue-Token") String queueToken) {
        return lotteryTrackService.createLotteryReservation(request, userId, queueToken)
                .map(ResponseEntity::ok);
    }

    // 추첨 예약 단건 결과 조회 (당첨/미당첨·좌석 배정 상태)
    @GetMapping("/reservations/{reservationId}")
    public Mono<ResponseEntity<LotteryResultResponse>> getLotteryResult(
            @PathVariable Long reservationId,
            @RequestHeader("X-User-Id") Long userId) {
        return lotteryTrackService.getLotteryReservationResult(reservationId, userId)
                .map(ResponseEntity::ok);
    }

    // 해당 회차 내 내 추첨 예약 목록 (당첨 결과·좌석 포함). Query: scheduleId 필수
    @GetMapping("/reservations")
    public Mono<ResponseEntity<List<LotteryResultResponse>>> getMyLotteryResults(
            @RequestParam Long scheduleId,
            @RequestHeader("X-User-Id") Long userId) {
        return lotteryTrackService.getMyLotteryResultsBySchedule(scheduleId, userId)
                .collectList()
                .map(ResponseEntity::ok);
    }
}
