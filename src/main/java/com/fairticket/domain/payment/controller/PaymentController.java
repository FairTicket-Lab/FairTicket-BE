package com.fairticket.domain.payment.controller;

import com.fairticket.domain.payment.dto.PaymentInitResponse;
import com.fairticket.domain.payment.dto.PaymentTimerResponse;
import com.fairticket.domain.payment.dto.WebhookRequest;
import com.fairticket.domain.payment.entity.Payment;
import com.fairticket.domain.payment.service.PaymentService;
import com.fairticket.domain.payment.service.PaymentTimerService;
import com.fairticket.domain.payment.service.PortOneClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentTimerService paymentTimerService;

    @Operation(
            summary = "결제 준비",
            description = "예약 ID를 기반으로 결제를 준비하고 merchantUid를 발급합니다. 추첨/라이브 공통 5분 타이머가 시작됩니다."
    )
    @PostMapping("/prepare")
    public Mono<ResponseEntity<PaymentInitResponse>> preparePayment(
            @Parameter(description = "예약 ID", required = true)
            @RequestParam Long reservationId,
            @Parameter(description = "사용자 ID", required = true)
            @RequestHeader("X-User-Id") Long userId) {
        return paymentService.initiatePayment(reservationId, userId)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "결제 Webhook 수신",
            description = "PortOne에서 결제 완료 후 impUid와 merchantUid를 전달합니다. " +
                    "금액 검증 → 결제 완료 처리 → 트랙별 후속 처리까지 자동으로 진행됩니다. " +
                    "프론트엔드는 이 API에 impUid, merchantUid만 전달하면 됩니다."
    )
    @PostMapping("/webhook")
    public Mono<ResponseEntity<Payment>> handleWebhook(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PortOne 결제 완료 정보 (impUid, merchantUid)",
                    required = true
            )
            @RequestBody WebhookRequest request) {
        return paymentService.completePayment(request)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "결제 환불",
            description = "완료된 결제를 환불 처리합니다. PortOne을 통해 실제 환불이 진행됩니다."
    )
    @PostMapping("/{paymentId}/refund")
    public Mono<ResponseEntity<PortOneClient.RefundResult>> refundPayment(
            @Parameter(description = "결제 ID", required = true)
            @PathVariable Long paymentId,
            @Parameter(description = "환불 사유", example = "사용자 요청")
            @RequestParam(defaultValue = "사용자 요청") String reason) {
        return paymentService.refundPayment(paymentId, reason)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "결제 단건 조회 (관리자용)",
            description = "[관리자] 결제 ID로 결제 정보를 조회합니다. 일반 사용자는 예약 기준 조회(GET /reservation/{reservationId})를 사용하세요."
    )
    @GetMapping("/{paymentId}")
    public Mono<ResponseEntity<Payment>> getPayment(
            @Parameter(description = "결제 ID", required = true)
            @PathVariable Long paymentId) {
        return paymentService.getPayment(paymentId)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "예약 기준 결제 조회",
            description = "예약 ID로 해당 결제 정보를 조회합니다."
    )
    @GetMapping("/reservation/{reservationId}")
    public Mono<ResponseEntity<Payment>> getPaymentByReservation(
            @Parameter(description = "예약 ID", required = true)
            @PathVariable Long reservationId) {
        return paymentService.getPaymentByReservationId(reservationId)
                .map(ResponseEntity::ok);
    }

    @Operation(
            summary = "내 결제 목록 조회",
            description = "로그인한 사용자의 전체 결제 내역을 조회합니다."
    )
    @GetMapping("/my")
    public Flux<Payment> getMyPayments(
            @Parameter(description = "사용자 ID", required = true)
            @RequestHeader("X-User-Id") Long userId) {
        return paymentService.getMyPayments(userId);
    }

    @Operation(
            summary = "결제 타이머 남은 시간 조회",
            description = "결제 제한 시간까지 남은 시간을 반환합니다. 타이머가 없거나 만료된 경우 P004 에러를 반환합니다."
    )
    @GetMapping("/{reservationId}/timer")
    public Mono<ResponseEntity<PaymentTimerResponse>> getRemainingTimer(
            @Parameter(description = "예약 ID", required = true)
            @PathVariable Long reservationId) {
        return paymentTimerService.getRemainingTime(reservationId)
                .map(seconds -> ResponseEntity.ok(PaymentTimerResponse.builder()
                        .reservationId(reservationId)
                        .remainingSeconds(seconds)
                        .expired(false)
                        .build()));
    }
}