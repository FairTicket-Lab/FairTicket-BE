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
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // 결제 준비 (결제창 호출 전).
    public Mono<PaymentInitResponse> initiatePayment(Long reservationId, Long userId) {
        // 1. 예약 조회
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND)))
                .flatMap(reservation -> {
                    // 2. 재고 카운터 선점 검증: 0 이하면 매진 처리
                    String stockKey = RedisKeyGenerator.stockKey(
                            reservation.getScheduleId(), reservation.getGrade());

                    return redisTemplate.opsForValue().get(stockKey)
                            .defaultIfEmpty("0")
                            .flatMap(stockStr -> {
                                long stock = parseLong(stockStr);
                                int need = reservation.getQuantity() != null ? reservation.getQuantity() : 1;
                                if (stock <= 0 || stock < need) {
                                    log.warn("재고 부족으로 결제 준비 거부: reservationId={}, scheduleId={}, grade={}, stock={}, need={}",
                                            reservationId, reservation.getScheduleId(), reservation.getGrade(), stock, need);
                                    return Mono.<PaymentInitResponse>error(
                                            new BusinessException(ErrorCode.SOLD_OUT));
                                }
                                // 3. Payment 생성 + 결제 타이머 시작
                                return createPaymentAndTimer(reservation);
                            });
                });
    }

    private Mono<PaymentInitResponse> createPaymentAndTimer(Reservation reservation) {
        int amount = calculateAmount(reservation);
        String merchantUid = generateMerchantUid();

        Payment payment = Payment.builder()
                .reservationId(reservation.getId())
                .merchantUid(merchantUid)
                .amount(amount)
                .status(PaymentStatus.PENDING.name())
                .createdAt(LocalDateTime.now())
                .build();

        return paymentRepository.save(payment)
                .flatMap(saved -> timerService.startPaymentTimer(
                                reservation.getId(), TrackType.valueOf(reservation.getTrackType()))
                        .thenReturn(saved))
                .map(saved -> PaymentInitResponse.builder()
                        .paymentId(saved.getId())
                        .merchantUid(saved.getMerchantUid())
                        .amount(saved.getAmount())
                        .itemName(String.format("%s %d매 티켓",
                                reservation.getGrade(),
                                reservation.getQuantity()))
                        .timeoutSeconds(ReservationConstants.PAYMENT_DEADLINE_MINUTES * 60)
                        .build());
    }

    // 결제 완료 처리 - PortOne Webhook 수신.
    public Mono<Payment> completePayment(WebhookRequest request) {
        return paymentRepository.findByMerchantUid(request.getMerchantUid())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND)))
                // 1. 이미 완료된 결제 중복 처리 방지
                .flatMap(payment -> {
                    if (PaymentStatus.COMPLETED.name().equals(payment.getStatus())) {
                        return Mono.error(new BusinessException(ErrorCode.PAYMENT_ALREADY_COMPLETED));
                    }
                    return Mono.just(payment);
                })
                // 2. PG사 금액 검증
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
                // 3. 타이머 취소
                .flatMap(payment -> timerService.cancelPaymentTimer(payment.getReservationId())
                        .thenReturn(payment))
                // 4. 트랙별 후속 처리 + 재고 카운터 차감
                .flatMap(payment -> reservationRepository.findById(payment.getReservationId())
                        .flatMap(reservation -> {
                            // 재고 카운터 차감: stock:{scheduleId}:{grade} DECR * quantity
                            Mono<Void> decrementStock = decrementStockCounter(reservation);

                            if (TrackType.LOTTERY.name().equals(reservation.getTrackType())) {
                                return decrementStock
                                        .then(lotteryTrackService.onPaymentCompleted(
                                                payment.getReservationId(),
                                                reservation.getUserId(),
                                                reservation.getScheduleId()))
                                        .thenReturn(payment);
                            }
                            // Todo: 라이브 트랙: 홀드 해제
                            return decrementStock.thenReturn(payment);
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

    // 재고 카운터 차감 (결제 완료 시).
    // stock:{scheduleId}:{grade} DECR * quantity
    private Mono<Void> decrementStockCounter(Reservation reservation) {
        if (reservation.getGrade() == null) {
            return Mono.empty();
        }
        String stockKey = RedisKeyGenerator.stockKey(reservation.getScheduleId(), reservation.getGrade());
        int quantity = reservation.getQuantity() != null ? reservation.getQuantity() : 1;

        return Flux.range(0, quantity)
                .flatMap(i -> redisTemplate.opsForValue().decrement(stockKey))
                .then()
                // 카운터가 없으면(키 미존재) 차감 시도 시 음수가 되지 않도록 guard
                .doOnSuccess(v -> log.info("재고 카운터 차감: scheduleId={}, grade={}, qty=-{}",
                        reservation.getScheduleId(), reservation.getGrade(), quantity));
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

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}