package com.fairticket.domain.reservation.controller;

import com.fairticket.domain.reservation.dto.CancellationWindowResponse;
import com.fairticket.domain.reservation.service.CancellationWindowService;
import com.fairticket.domain.reservation.service.ReservationCancelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationCancelService reservationCancelService;
    private final CancellationWindowService cancellationWindowService;

    // 예약 취소 (추첨/라이브 공통). 티켓 오픈 2시간 후 ~ 24시간 이내만 가능.
    @PostMapping("/{reservationId}/cancel")
    public Mono<ResponseEntity<Void>> cancelReservation(
            @PathVariable Long reservationId,
            @RequestHeader("X-User-Id") Long userId) {
        return reservationCancelService.cancelReservation(reservationId, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // 해당 회차 취소 가능 기간 조회
    @GetMapping("/schedules/{scheduleId}/cancellation-window")
    public Mono<ResponseEntity<CancellationWindowResponse>> getCancellationWindow(@PathVariable Long scheduleId) {
        return cancellationWindowService.getCancellationWindow(scheduleId)
                .map(ResponseEntity::ok);
    }
}
