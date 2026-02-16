package com.fairticket.domain.queue.controller;

import com.fairticket.domain.queue.dto.QueueEntryResponse;
import com.fairticket.domain.queue.dto.QueueStatusResponse;
import com.fairticket.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * 대기열 진입
     * POST /api/v1/queue/{scheduleId}/enter
     */
    @PostMapping("/{scheduleId}/enter")
    public Mono<ResponseEntity<QueueEntryResponse>> enterQueue(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal Long userId) {
        return queueService.enterQueue(scheduleId, userId)
                .map(ResponseEntity::ok);
    }

    /**
     * 대기열 상태 조회
     * GET /api/v1/queue/{scheduleId}/status
     */
    @GetMapping("/{scheduleId}/status")
    public Mono<ResponseEntity<QueueStatusResponse>> getStatus(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal Long userId) {
        return queueService.getQueueStatus(scheduleId, userId)
                .map(ResponseEntity::ok);
    }

    /**
     * 대기열 취소
     * DELETE /api/v1/queue/{scheduleId}/leave
     */
    @DeleteMapping("/{scheduleId}/leave")
    public Mono<ResponseEntity<Void>> leaveQueue(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal Long userId) {
        return queueService.leaveQueue(scheduleId, userId)
                .map(success -> ResponseEntity.noContent().<Void>build());
    }

    /**
     * Heartbeat
     * POST /api/v1/queue/{scheduleId}/heartbeat
     */
    @PostMapping("/{scheduleId}/heartbeat")
    public Mono<ResponseEntity<Void>> heartbeat(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal Long userId) {
        return queueService.heartbeat(scheduleId, userId)
                .map(success -> ResponseEntity.ok().<Void>build());
    }
}
