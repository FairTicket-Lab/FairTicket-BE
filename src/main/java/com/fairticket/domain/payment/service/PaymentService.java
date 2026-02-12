package com.fairticket.domain.payment.service;

import com.fairticket.domain.payment.dto.PaymentInitResponse;
import com.fairticket.domain.payment.dto.WebhookRequest;
import com.fairticket.domain.payment.entity.Payment;
import com.fairticket.domain.payment.entity.PaymentStatus;
import com.fairticket.domain.payment.repository.PaymentRepository;
import com.fairticket.domain.payment.repository.PaymentQueryRepository;
import com.fairticket.domain.reservation.constants.ReservationConstants;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.reservation.service.LotteryTrackService;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentQueryRepository paymentQueryRepository;
    private final ReservationRepository reservationRepository;
    private final PortOneClient portOneClient;
    private final PaymentTimerService timerService;
    private final LotteryTrackService lotteryTrackService;

    // 결제 준비 (결제창 호출 전)
    public Mono<PaymentInitResponse> initiatePayment(Long reservationId, Long userId) {
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND)))
                .flatMap(reservation -> {
                    int amount = calculateAmount(reservation);
                    String merchantUid = generateMerchantUid();

                    Payment payment = Payment.builder()
                            .reservationId(reservationId)
                            .merchantUid(merchantUid)
                            .amount(amount)
                            .status(PaymentStatus.PENDING.name())
                            .createdAt(LocalDateTime.now())
                            .build();

                    return paymentRepository.save(payment)
                            .flatMap(saved -> timerService.startPaymentTimer(
                                            reservationId, TrackType.valueOf(reservation.getTrackType()))
                                    .thenReturn(saved))
                            .map(saved -> PaymentInitResponse.builder()
                                    .paymentId(saved.getId())
                                    .merchantUid(saved.getMerchantUid())
                                    .amount(saved.getAmount())
                                    .itemName(String.format("%s %d매 티켓",
                                            reservation.getGrade(),
                                            reservation.getQuantity()))
                                    // 타임라인 기준 추첨/라이브 공통 5분
                                    // .timeoutSeconds(trackType == TrackType.LOTTERY ? 300 : 600)
                                    .timeoutSeconds(ReservationConstants.PAYMENT_DEADLINE_MINUTES * 60)
                                    .build());
                });
    }

    // 결제 완료 처리 - PortOne Webhook 수신
    // 프론트엔드에서 impUid, merchantUid만 전달하면 이후 처리 자동 진행
    public Mono<Payment> completePayment(WebhookRequest request) {
        return paymentRepository.findByMerchantUid(request.getMerchantUid())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                // 이미 완료된 결제 중복 처리 방지
                .flatMap(payment -> {
                    if (PaymentStatus.COMPLETED.name().equals(payment.getStatus())) {
                        return Mono.error(new BusinessException(ErrorCode.PAYMENT_ALREADY_COMPLETED));
                    }
                    return Mono.just(payment);
                })
                // PG사 금액 검증
                .flatMap(payment -> portOneClient.verifyPayment(request.getImpUid())
                        .flatMap(verification -> {
                            if (!payment.getAmount().equals(verification.getAmount())) {
                                log.error("결제 금액 불일치: expected={}, actual={}",
                                        payment.getAmount(), verification.getAmount());
                                return Mono.error(new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
                            }
                            payment.setImpUid(request.getImpUid());
                            payment.setStatus(PaymentStatus.COMPLETED.name());
                            payment.setPaidAt(LocalDateTime.now());
                            return paymentRepository.save(payment);
                        }))
                // 타이머 취소
                .flatMap(payment -> timerService.cancelPaymentTimer(payment.getReservationId())
                        .thenReturn(payment))
                // 트랙별 후속 처리
                .flatMap(payment -> reservationRepository.findById(payment.getReservationId())
                        .flatMap(reservation -> {
                            if (TrackType.LOTTERY.name().equals(reservation.getTrackType())) {
                                return lotteryTrackService.onPaymentCompleted(
                                        payment.getReservationId(),
                                        reservation.getUserId(),
                                        reservation.getScheduleId()
                                ).thenReturn(payment);
                            }
                            // 라이브 트랙: 홀드 해제는 이슈 2에서 연동 예정
                            return Mono.just(payment);
                        }))
                .doOnSuccess(payment -> log.info("결제 완료: paymentId={}, merchantUid={}",
                        payment.getId(), payment.getMerchantUid()));
    }

    // 환불 처리
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
                        return paymentRepository.save(payment).thenReturn(result);
                    }
                    return Mono.just(result);
                }));
    }

    // 결제 단건 조회
    public Mono<Payment> getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)));
    }

    // 예약 기준 결제 조회
    public Mono<Payment> getPaymentByReservationId(Long reservationId) {
        return paymentRepository.findByReservationId(reservationId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)));
    }

    // 내 결제 목록 조회
    public Flux<Payment> getMyPayments(Long userId) {
        return paymentQueryRepository.findByUserId(userId);
    }

    private String generateMerchantUid() {
        return "FAIR_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    private int calculateAmount(Reservation reservation) {
        int unitPrice = switch (reservation.getGrade()) {
            case "VIP" -> 150000;
            case "R"   -> 120000;
            case "S"   -> 90000;
            case "A"   -> 60000;
            default    -> 0;
        };
        return unitPrice * reservation.getQuantity();
    }
}