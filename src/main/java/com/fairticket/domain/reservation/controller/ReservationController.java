package com.fairticket.domain.reservation.controller;

import com.fairticket.domain.reservation.dto.CancellationWindowResponse;
import com.fairticket.domain.reservation.dto.ReservationResponse;
import com.fairticket.domain.reservation.service.CancellationWindowService;
import com.fairticket.domain.reservation.service.ReservationCancelService;
import com.fairticket.domain.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Reservation", description = "예약 조회/취소 API")
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationCancelService reservationCancelService;
    private final CancellationWindowService cancellationWindowService;
    private final ReservationService reservationService;

    @Operation(
            summary = "결제 대기 중인 예약 조회",
            description = "사용자가 예약 생성 후 페이지 이동/새로고침 등으로 예약ID를 유실한 경우, " +
                    "해당 공연 일정에서 결제 대기 중(PENDING)인 예약을 재조회하여 reservationId를 획득할 수 있습니다. " +
                    "결제 준비 API(POST /api/v1/payment/prepare)의 입력값으로 사용됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 대기 중인 예약 조회 성공",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "해당 공연 일정에 결제 대기 중인 예약이 없음"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @GetMapping("/pending")
    public Mono<ResponseEntity<ReservationResponse>> getPendingReservation(
            @Parameter(description = "사용자 ID", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "공연 일정 ID", required = true)
            @RequestParam Long scheduleId) {
        return reservationService.getPendingReservation(userId, scheduleId)
                .map(ResponseEntity::ok);
    }

    // 예약 취소 (추첨/라이브 공통). 티켓 오픈 2시간 후 ~ 24시간 이내만 가능.
    @Operation(
            summary = "예약 취소",
            description = "배정된 또는 배정 대기 중인 예약을 취소합니다. 티켓 오픈 2시간 후부터 24시간 이내만 가능합니다."
    )
    @DeleteMapping("/{reservationId}")
    public Mono<ResponseEntity<Void>> cancelReservation(
            @Parameter(description = "예약 ID", required = true)
            @PathVariable Long reservationId,
            @Parameter(description = "사용자 ID", required = true)
            @RequestHeader("X-User-Id") Long userId) {
        return reservationCancelService.cancelReservation(reservationId, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // 해당 회차 취소 가능 기간 조회
    @Operation(
            summary = "예약 취소 가능 기간 조회",
            description = "해당 공연 일정의 티켓 오픈 시간과 취소 가능 윈도우를 조회합니다."
    )
    @GetMapping("/schedules/{scheduleId}/cancellation-window")
    public Mono<ResponseEntity<CancellationWindowResponse>> getCancellationWindow(
            @Parameter(description = "공연 일정 ID", required = true)
            @PathVariable Long scheduleId) {
        return cancellationWindowService.getCancellationWindow(scheduleId)
                .map(ResponseEntity::ok);
    }
}
