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
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class LiveTrackController {
    private final LiveTrackService liveTrackService;

    @GetMapping("/{scheduleId}")
    public Mono<ResponseEntity<List<String>>> getAvailableSeats(
            @PathVariable Long scheduleId,
            @RequestParam String grade) {
        return liveTrackService.getAvailableSeats(scheduleId, grade)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{scheduleId}/select")
    public Mono<ResponseEntity<SeatSelectionResponse>> selectSeat(
            @PathVariable Long scheduleId,
            @RequestBody SeatSelectionRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        request.setScheduleId(scheduleId);
        return liveTrackService.selectSeat(request, userId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{scheduleId}/release")
    public Mono<ResponseEntity<Void>> releaseSeat(
            @PathVariable Long scheduleId,
            @RequestParam String grade,
            @RequestParam String seatNumber,
            @RequestHeader("X-User-Id") Long userId) {
        return liveTrackService.releaseSeat(scheduleId, grade, seatNumber, userId)
                .map(v -> ResponseEntity.noContent().<Void>build());
    }

    @GetMapping("/{scheduleId}/status")
    public Mono<ResponseEntity<Boolean>> checkTrackStatus(@PathVariable Long scheduleId) {
        return liveTrackService.isLiveTrackOpen(scheduleId)
                .map(ResponseEntity::ok);
    }
}
