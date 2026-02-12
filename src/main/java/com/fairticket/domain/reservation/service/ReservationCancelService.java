package com.fairticket.domain.reservation.service;

import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.reservation.repository.ReservationSeatRepository;
import com.fairticket.domain.seat.entity.SeatStatus;
import com.fairticket.domain.seat.repository.SeatRepository;
import com.fairticket.domain.seat.service.SeatPoolService;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

// 추첨/라이브 두 트랙 모두, 티켓 오픈 2시간 후부터 24시간 동안 좌석 취소(환불) 가능.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationCancelService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final CancellationWindowService cancellationWindowService;
    private final SeatPoolService seatPoolService;
    private final SeatRepository seatRepository;

    public Mono<Void> cancelReservation(Long reservationId, Long userId) {
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND)))
                .filter(r -> r.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED)))
                .flatMap(reservation -> cancellationWindowService.validateCancellationAllowed(reservation.getScheduleId())
                        .thenReturn(reservation))
                .flatMap(reservation -> {
                    String status = reservation.getStatus();
                    if (ReservationStatus.CANCELLED.name().equals(status) || ReservationStatus.REFUNDED.name().equals(status)) {
                        return Mono.error(new BusinessException(ErrorCode.RESERVATION_CANCELLED));
                    }
                    if (!ReservationStatus.ASSIGNED.name().equals(status) && !ReservationStatus.PAID_PENDING_SEAT.name().equals(status)) {
                        return Mono.error(new BusinessException(ErrorCode.INVALID_INPUT));
                    }
                    return Mono.just(reservation);
                })
                .flatMap(this::applyCancellation)
                .doOnSuccess(v -> log.info("예약 취소 완료: reservationId={}, userId={}", reservationId, userId))
                .then();
    }

    private Mono<Void> applyCancellation(Reservation reservation) {
        reservation.setStatus(ReservationStatus.REFUNDED.name());
        reservation.setUpdatedAt(LocalDateTime.now());
        Long reservationId = reservation.getId();
        Long scheduleId = reservation.getScheduleId();
        return reservationRepository.save(reservation)
                .then(reservationSeatRepository.findByReservationId(reservationId).collectList())
                .flatMap(seats -> reservationSeatRepository.updateStatusToCancelledByReservationId(reservationId)
                        .thenReturn(seats))
                .flatMap(seats -> Flux.fromIterable(seats)
                        .filter(rs -> rs.getZone() != null && rs.getSeatNumber() != null)
                        .flatMap(rs -> seatPoolService.returnSeat(scheduleId, rs.getZone(), rs.getSeatNumber())
                                .then(seatRepository.updateStatusByScheduleIdAndZoneAndSeatNumber(
                                        SeatStatus.AVAILABLE.name(), scheduleId, rs.getZone(), rs.getSeatNumber())))
                        .then());
    }
}
