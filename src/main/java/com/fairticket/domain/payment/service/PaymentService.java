package com.fairticket.domain.payment.service;

import com.fairticket.domain.payment.dto.PaymentCompleteRequest;
import com.fairticket.domain.payment.dto.PaymentInitResponse;
import com.fairticket.domain.payment.entity.Payment;
import com.fairticket.domain.payment.entity.PaymentStatus;
import com.fairticket.domain.payment.repository.PaymentRepository;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.repository.ReservationRepository;
// import com.fairticket.domain.reservation.service.CartTrackService; // TODO: B 작업 후 주석 해제
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final PortOneClient portOneClient;
    private final PaymentTimerService timerService;
    // private final CartTrackService cartTrackService; // TODO: B팀 작업 후 주석 해제

    /**
     * 결제 준비 (결제창 호출 전)
     */
    public Mono<PaymentInitResponse> initiatePayment(Long reservationId, Long userId) {
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND)))
                .flatMap(reservation -> {
                    int amount = calculateAmount(reservation);
                    String merchantUid = generateMerchantUid();
                    TrackType trackType = TrackType.valueOf(reservation.getTrackType());

                    Payment payment = Payment.builder()
                            .reservationId(reservationId)
                            .merchantUid(merchantUid)
                            .amount(amount)
                            .status(PaymentStatus.PENDING.name())
                            .createdAt(LocalDateTime.now())
                            .build();

                    return paymentRepository.save(payment)
                            .flatMap(saved -> {
                                // 타이머 시작 (⭐ reservationId 전달)
                                return timerService.startPaymentTimer(reservationId, trackType)
                                        .thenReturn(saved);
                            })
                            .map(saved -> PaymentInitResponse.builder()
                                    .paymentId(saved.getId())
                                    .merchantUid(saved.getMerchantUid())
                                    .amount(saved.getAmount())
                                    .itemName(String.format("%s %d매 티켓",
                                            reservation.getGrade(),
                                            reservation.getQuantity()))
                                    .timeoutSeconds(trackType == TrackType.CART ? 300 : 600)
                                    .build());
                });
    }

    /**
     * 결제 완료 처리 (Webhook)
     */
    public Mono<Payment> completePayment(PaymentCompleteRequest request) {
        return paymentRepository.findByMerchantUid(request.getMerchantUid())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                // PG사 검증
                .flatMap(payment -> portOneClient.verifyPayment(request.getImpUid())
                        .flatMap(verification -> {
                            // 금액 검증
                            if (!payment.getAmount().equals(verification.getAmount())) {
                                log.error("결제 금액 불일치: expected={}, actual={}",
                                        payment.getAmount(),
                                        verification.getAmount());
                                return Mono.error(new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
                            }

                            // 결제 정보 업데이트
                            payment.setImpUid(request.getImpUid());
                            payment.setStatus(PaymentStatus.COMPLETED.name());
                            payment.setPaidAt(LocalDateTime.now());
                            return paymentRepository.save(payment);
                        }))
                // 타이머 취소
                .flatMap(payment -> reservationRepository.findById(payment.getReservationId())
                        .flatMap(reservation -> timerService.cancelPaymentTimer(reservation.getId())
                                .thenReturn(payment)))
                // B 콜백 (장바구니 트랙인 경우)
                .flatMap(payment -> reservationRepository.findById(payment.getReservationId())
                        .flatMap(reservation -> {
                            if (TrackType.CART.name().equals(reservation.getTrackType())) {
                                // TODO: B 작업 후 주석 해제
                                // return cartTrackService.onPaymentCompleted(
                                //         reservation.getId(),
                                //         reservation.getUserId(),
                                //         reservation.getScheduleId()
                                // ).thenReturn(payment);

                                log.info("장바구니 결제 완료 (B 연동 대기중): reservationId={}", reservation.getId());
                                return Mono.just(payment);
                            }
                            // 당일 트랙은 별도 처리 없음
                            return Mono.just(payment);
                        }))
                .doOnSuccess(payment -> log.info("결제 완료 처리: paymentId={}, merchantUid={}",
                        payment.getId(), payment.getMerchantUid()));
    }

    /**
     * 환불 처리
     */
    public Mono<PortOneClient.RefundResult> refundPayment(Long paymentId, String reason) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                .flatMap(payment -> portOneClient.cancelPayment(
                        payment.getImpUid(),
                        payment.getAmount(),
                        reason
                ).flatMap(result -> {
                    if (result.isSuccess()) {
                        payment.setStatus(PaymentStatus.REFUNDED.name());
                        payment.setUpdatedAt(LocalDateTime.now());
                        return paymentRepository.save(payment)
                                .thenReturn(result);
                    }
                    return Mono.just(result);
                }));
    }

    private String generateMerchantUid() {
        return "FAIR_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    private int calculateAmount(Reservation reservation) {
        int unitPrice = switch (reservation.getGrade()) {
            case "VIP" -> 150000;
            case "R" -> 120000;
            case "S" -> 90000;
            case "A" -> 60000;
            default -> 0;
        };
        return unitPrice * reservation.getQuantity();
    }
}