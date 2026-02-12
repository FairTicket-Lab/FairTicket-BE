package com.fairticket.domain.payment.controller;

import com.fairticket.domain.payment.dto.PaymentCompleteRequest;
import com.fairticket.domain.payment.dto.PaymentInitResponse;
import com.fairticket.domain.payment.entity.Payment;
import com.fairticket.domain.payment.service.PaymentService;
import com.fairticket.domain.payment.service.PortOneClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "결제 준비",
            description = "예약 ID를 기반으로 결제를 준비하고 merchantUid를 발급합니다. 장바구니는 5분, 당일은 10분 타이머가 시작됩니다."
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
            summary = "결제 완료 처리",
            description = "PortOne PG사를 통한 결제 완료 후 Webhook으로 호출됩니다. 결제 금액을 검증하고 예약 상태를 업데이트합니다."
    )
    @PostMapping("/complete")
    public Mono<ResponseEntity<Payment>> completePayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "PortOne 결제 완료 정보",
                    required = true
            )
            @RequestBody PaymentCompleteRequest request) {
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
}