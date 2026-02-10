package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.domain.reservation.dto.CartReservationRequest;
import com.fairticket.domain.reservation.dto.ReservationResponse;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartTrackService {
    private final ReservationRepository reservationRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // 장바구니 트랙 예매 요청
    public Mono<ReservationResponse> createCartReservation(CartReservationRequest request, Long userId) {
        // 1. 티켓 오픈 시간 체크
        return scheduleRepository.findById(request.getScheduleId())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND)))
                .flatMap(schedule -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isBefore(schedule.getTicketOpenAt())) {
                        return Mono.error(new BusinessException(ErrorCode.TICKET_NOT_OPENED));
                    }
                    if (schedule.getTicketCloseAt() != null && now.isAfter(schedule.getTicketCloseAt())) {
                        return Mono.error(new BusinessException(ErrorCode.TICKET_CLOSED));
                    }
                    return Mono.just(schedule);
                })
                // 2. 중복 참여 체크
                .then(canParticipate(request.getScheduleId(), userId))
                .flatMap(canParticipate -> {
                    if (!canParticipate) {
                        return Mono.error(new BusinessException(ErrorCode.ALREADY_PARTICIPATED));
                    }
                    // 3. 예약 생성 (좌석 미배정 상태)
                    Reservation reservation = Reservation.builder()
                            .userId(userId)
                            .scheduleId(request.getScheduleId())
                            .grade(request.getGrade())
                            .quantity(request.getQuantity())
                            .trackType(TrackType.CART.name())
                            .status(ReservationStatus.PENDING.name())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return reservationRepository.save(reservation);
                })
                // 4. 결제 요청 준비
                .map(saved -> {
                    log.info("장바구니 예약 생성: reservationId={}, userId={}, scheduleId={}, grade={}",
                            saved.getId(), userId, request.getScheduleId(), request.getGrade());
                    return ReservationResponse.builder()
                            .id(saved.getId())
                            .scheduleId(saved.getScheduleId())
                            .grade(saved.getGrade())
                            .quantity(saved.getQuantity())
                            .trackType(saved.getTrackType())
                            .status(saved.getStatus())
                            .message("결제를 진행해주세요. 5분 내 결제하지 않으면 자동 취소됩니다.")
                            .paymentDeadline(LocalDateTime.now().plusMinutes(5))
                            .build();
                });
    }

    // 장바구니 결제 완료 처리 
    public Mono<Void> onPaymentCompleted(Long reservationId, Long userId, Long scheduleId) {
        String cartPaidKey = RedisKeyGenerator.cartPaidKey(scheduleId);
        return reservationRepository.findById(reservationId)
                .flatMap(reservation -> {
                    reservation.setStatus(ReservationStatus.PAID_PENDING_SEAT.name());
                    reservation.setUpdatedAt(LocalDateTime.now());
                    return reservationRepository.save(reservation);
                })
                .then(redisTemplate.opsForSet().add(cartPaidKey, userId.toString()))
                .doOnSuccess(v -> log.info("장바구니 결제 완료: reservationId={}, userId={}",
                        reservationId, userId))
                .then();
    }

    // 양 트랙 중복 참여 체크
    // @return true: 참여 가능, false: 이미 참여함
    public Mono<Boolean> canParticipate(Long scheduleId, Long userId) {
        String cartPaidKey = RedisKeyGenerator.cartPaidKey(scheduleId);
        // Redis에서 장바구니 결제자 Set 확인
        return redisTemplate.opsForSet()
                .isMember(cartPaidKey, userId.toString())
                .map(isMember -> !isMember); // 결제자면 false (참여 불가)
    }
}
