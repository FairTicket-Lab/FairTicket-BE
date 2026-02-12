package com.fairticket.domain.reservation.controller;

import com.fairticket.domain.reservation.service.LiveTrackService;
import com.fairticket.domain.seat.dto.SeatSelectionRequest;
import com.fairticket.domain.seat.dto.SeatSelectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/live")
@RequiredArgsConstructor
public class LiveTrackController {
    private final LiveTrackService liveTrackService;

    // 선택한 등급·구역 내 잔여 좌석 번호 목록 (구역 목록은 GET /api/v1/schedules/{scheduleId}/grades/{grade}/zones 사용)
    @GetMapping("/{scheduleId}")
    public Mono<ResponseEntity<List<String>>> getAvailableSeats(
            @PathVariable Long scheduleId,
            @RequestParam String grade,
            @RequestParam String zone) {
        return liveTrackService.getAvailableSeats(scheduleId, grade, zone)
                .map(ResponseEntity::ok);
    }

    // 좌석 선택(홀드). Request body: grade, zone, seatNumber
    @PostMapping("/{scheduleId}")
    public Mono<ResponseEntity<SeatSelectionResponse>> selectSeat(
            @PathVariable Long scheduleId,
            @RequestBody SeatSelectionRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return liveTrackService.selectSeat(scheduleId, request, userId)
                .map(ResponseEntity::ok);
    }

    // 좌석 홀드 해제. Query: zone, seatNumber
    @DeleteMapping("/{scheduleId}")
    public Mono<ResponseEntity<Void>> releaseSeat(
            @PathVariable Long scheduleId,
            @RequestParam String zone,
            @RequestParam String seatNumber,
            @RequestHeader("X-User-Id") Long userId) {
        return liveTrackService.releaseSeat(scheduleId, zone, seatNumber, userId)
                .map(v -> ResponseEntity.noContent().<Void>build());
    }

    @GetMapping("/{scheduleId}/status")
    public Mono<ResponseEntity<Boolean>> checkTrackStatus(@PathVariable Long scheduleId) {
        return liveTrackService.isLiveTrackOpen(scheduleId)
                .map(ResponseEntity::ok);
    }
}
