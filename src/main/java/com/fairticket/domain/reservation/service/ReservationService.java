package com.fairticket.domain.reservation.service;

import com.fairticket.domain.reservation.dto.ReservationResponse;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;

    /**
     * 사용자의 해당 공연 일정에서 결제 대기 중인(PENDING) 예약을 조회합니다.
     * 결제 준비 API의 입력으로 사용할 reservationId를 재획득하는 용도입니다.
     *
     * @param userId 사용자 ID
     * @param scheduleId 공연 일정 ID
     * @return 결제 대기 중인 예약 정보
     * @throws BusinessException 해당 예약이 없으면 예외 발생
     */
    public Mono<ReservationResponse> getPendingReservation(Long userId, Long scheduleId) {
        return reservationRepository.findFirstByUserIdAndScheduleIdAndStatusOrderByCreatedAtDesc(
                userId, scheduleId, ReservationStatus.PENDING.name())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND)))
                .map(this::toReservationResponse)
                .doOnSuccess(response -> log.info("결제 대기 중 예약 조회: userId={}, scheduleId={}, reservationId={}", 
                        userId, scheduleId, response.getId()))
                .doOnError(error -> log.warn("예약 조회 실패: userId={}, scheduleId={}, error={}", 
                        userId, scheduleId, error.getMessage()));
    }

    /**
     * Reservation 엔티티를 ReservationResponse DTO로 변환합니다.
     */
    private ReservationResponse toReservationResponse(Reservation reservation) {
        // 결제 만료 시간: 예약 생성 후 5분 (Payment 타이머 기준)
        LocalDateTime paymentDeadline = reservation.getCreatedAt() != null ? 
                reservation.getCreatedAt().plusMinutes(5) : null;

        return ReservationResponse.builder()
                .id(reservation.getId())
                .scheduleId(reservation.getScheduleId())
                .grade(reservation.getGrade())
                .quantity(reservation.getQuantity())
                .trackType(reservation.getTrackType())
                .status(reservation.getStatus())
                .paymentDeadline(paymentDeadline)
                .build();
    }
}
