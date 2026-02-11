package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.service.ScheduleService;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.entity.ReservationSeat;
import com.fairticket.domain.reservation.entity.ReservationSeatStatus;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.reservation.repository.ReservationSeatRepository;
import com.fairticket.domain.reservation.constants.ReservationConstants;
import com.fairticket.domain.seat.dto.SeatSelectionRequest;
import com.fairticket.domain.seat.dto.SeatSelectionResponse;
import com.fairticket.domain.seat.repository.SeatRepository;
import com.fairticket.domain.seat.service.SeatHoldService;
import com.fairticket.domain.seat.service.SeatPoolService;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTrackService {

    private final SeatPoolService seatPoolService;
    private final SeatHoldService seatHoldService;
    private final SeatRepository seatRepository;
    private final LotteryTrackService lotteryTrackService;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ScheduleService scheduleService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;


    // 좌석 선택 (라이브 트랙). 플로우: 등급 선택 → 구역 선택 → 좌석 선택.
    // TODO(결제): 결제 5분 이내 미결제 시 취소 스케줄러는 payment 담당자 구현. 라이브/추첨 공통.
    public Mono<SeatSelectionResponse> selectSeat(Long scheduleId, SeatSelectionRequest request, Long userId) {
        if (request.getGrade() == null || request.getGrade().isBlank()) {
            return Mono.error(new BusinessException(ErrorCode.INVALID_INPUT));
        }
        // 1. 티켓 오픈 시간 체크 (라이브 트랙: 티켓 오픈 시각부터)
        return scheduleService.findScheduleOrThrow(scheduleId)
                .flatMap(schedule -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isBefore(schedule.getTicketOpenAt())) {
                        return Mono.error(new BusinessException(ErrorCode.TICKET_NOT_OPENED));
                    }
                    return Mono.just(schedule);
                })
                // 2. 잔여석 체크 + 대기열 0인 상태 10분 지속 시 마감
                .then(requireLiveTrackOpen(isLiveTrackOpen(scheduleId)))
                // 3. 추첨 결제 완료자 라이브 참여 불가
                .then(requireTrue(
                        lotteryTrackService.canParticipate(scheduleId, userId),
                        ErrorCode.ALREADY_PARTICIPATED))
                // 4. 라이브 1인당 최대 4장 제한
                .then(requireTrue(
                        reservationRepository.sumLiveQuantityByUserAndSchedule(scheduleId, userId)
                                .map(qty -> (qty != null ? qty : 0L) < ReservationConstants.LIVE_MAX_QUANTITY_PER_USER),
                        ErrorCode.LIVE_MAX_QUANTITY_EXCEEDED))
                // 5. 등급·구역 검증: 선택한 구역이 해당 등급에 속하는지
                .then(scheduleService.validateGradeAndZone(scheduleId, request.getGrade(), request.getZone()))
                // 6. 좌석 풀에서 제거 후 7. 홀드 설정 (홀드 실패 시 풀에 좌석 반환)
                .then(seatPoolService.selectSeat(
                        scheduleId,
                        request.getZone(),
                        request.getSeatNumber())
                        .flatMap(selected -> {
                            if (!selected) {
                                return Mono.error(new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN));
                            }
                            return seatHoldService.holdSeat(
                                    scheduleId,
                                    request.getZone(),
                                    request.getSeatNumber(),
                                    userId);
                        })
                        .flatMap(held -> {
                            if (!held) {
                                return seatPoolService.returnSeat(
                                                scheduleId,
                                                request.getZone(),
                                                request.getSeatNumber())
                                        .then(Mono.<Reservation>error(new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN)));
                            }
                            return Mono.just(true);
                        }))
                // 8. 예약 생성 또는 기존 예약에 좌석 추가
                .flatMap(ignored -> reservationRepository.findFirstByUserIdAndScheduleIdAndTrackType(
                                userId, scheduleId, TrackType.LIVE.name())
                        .flatMap(existing -> addSeatToReservation(existing, request.getZone(), request.getSeatNumber()))
                        .switchIfEmpty(Mono.defer(() ->
                                reservationRepository.existsByUserIdAndScheduleId(userId, scheduleId)
                                        .flatMap(exists -> exists
                                                ? Mono.<Reservation>error(new BusinessException(ErrorCode.ALREADY_PARTICIPATED))
                                                : Mono.just(Reservation.builder()
                                                        .userId(userId)
                                                        .scheduleId(scheduleId)
                                                        .grade(request.getGrade())
                                                        .quantity(1)
                                                        .trackType(TrackType.LIVE.name())
                                                        .status(ReservationStatus.PENDING.name())
                                                        .createdAt(LocalDateTime.now())
                                                        .updatedAt(LocalDateTime.now())
                                                        .build()))
                                        .flatMap(newReservation -> reservationRepository.save(newReservation)
                                                .flatMap(newRes -> buildAndSaveReservationSeat(newRes.getId(), scheduleId, request.getZone(), request.getSeatNumber(), ReservationSeatStatus.PENDING.name())
                                                        .thenReturn(newRes)))))
                )
                .map(reservation -> {
                    log.info("라이브 트랙 좌석 선택: reservationId={}, userId={}, grade={}, zone={}, seat={}",
                            reservation.getId(), userId, request.getGrade(), request.getZone(), request.getSeatNumber());
                    LocalDateTime now = LocalDateTime.now();
                    return SeatSelectionResponse.builder()
                            .scheduleId(scheduleId)
                            .grade(request.getGrade())
                            .zone(request.getZone())
                            .seatNumber(request.getSeatNumber())
                            .holdExpiresAt(now.plusMinutes(ReservationConstants.HOLD_MINUTES))
                            .paymentDeadline(now.plusMinutes(ReservationConstants.PAYMENT_DEADLINE_MINUTES))
                            .message(ReservationConstants.MESSAGE_PAYMENT_DEADLINE_LIVE)
                            .build();
                });
    }

    // 좌석 선택 취소 (본인이 홀드한 좌석만 해제 가능)
    public Mono<Void> releaseSeat(Long scheduleId, String zone, String seatNumber, Long userId) {
        return seatHoldService.getHoldOwner(scheduleId, zone, seatNumber)
                .filter(owner -> owner.equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SEAT_HOLD_NOT_OWNED)))
                .then(seatHoldService.releaseHold(scheduleId, zone, seatNumber))
                .then(seatPoolService.returnSeat(scheduleId, zone, seatNumber))
                .then();
    }

    // 라이브 예약 결제 완료 시 해당 예약의 좌석 홀드를 즉시 해제.
    // PaymentService(또는 결제 콜백)에서 라이브 결제 완료 처리 후 반드시 호출할 것.
    public Mono<Void> releaseHoldsForReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .filter(r -> TrackType.LIVE.name().equals(r.getTrackType()))
                .flatMapMany(reservation -> reservationSeatRepository.findByReservationId(reservationId)
                        .flatMap(seat -> seatHoldService.releaseHold(reservation.getScheduleId(), seat.getZone(), seat.getSeatNumber())
                                .thenReturn(seat)))
                .then()
                .doOnSuccess(v -> log.info("라이브 결제 완료로 홀드 해제: reservationId={}", reservationId));
    }

    // 선택한 등급에 속한 구역 목록 (등급 선택 후 구역 선택용)
    public Mono<List<String>> getZonesByGrade(Long scheduleId, String grade) {
        return scheduleService.getZonesByGrade(scheduleId, grade);
    }

    // 한 구역의 선택 가능 좌석 번호 목록. 해당 구역이 등급에 속할 때만 조회 가능
    public Mono<List<String>> getAvailableSeats(Long scheduleId, String grade, String zone) {
        return scheduleService.validateGradeAndZone(scheduleId, grade, zone)
                .then(seatPoolService.getAvailableSeats(scheduleId, zone).collectList());
    }

    private Mono<Reservation> addSeatToReservation(Reservation reservation, String zone, String seatNumber) {
        return buildAndSaveReservationSeat(reservation.getId(), reservation.getScheduleId(), zone, seatNumber, ReservationSeatStatus.PENDING.name())
                .then(Mono.defer(() -> {
                    reservation.setQuantity(reservation.getQuantity() + 1);
                    reservation.setUpdatedAt(LocalDateTime.now());
                    return reservationRepository.save(reservation);
                }));
    }

    // seats 테이블에서 좌석 조회 후 ReservationSeat 저장 (seat_id 설정)
    private Mono<ReservationSeat> buildAndSaveReservationSeat(Long reservationId, Long scheduleId, String zone, String seatNumber, String status) {
        return seatRepository.findByScheduleIdAndZoneAndSeatNumber(scheduleId, zone, seatNumber)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.INVALID_INPUT)))
                .flatMap(seat -> reservationSeatRepository.save(ReservationSeat.builder()
                        .reservationId(reservationId)
                        .seatId(seat.getId())
                        .zone(zone)
                        .seatNumber(seatNumber)
                        .status(status)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    // condition이 true일 때만 통과, false면 주어진 ErrorCode로 실패
    private Mono<Boolean> requireTrue(Mono<Boolean> condition, ErrorCode whenFalse) {
        return condition.flatMap(ok -> ok ? Mono.just(true) : Mono.error(new BusinessException(whenFalse)));
    }

    private Mono<Boolean> requireLiveTrackOpen(Mono<Boolean> openCondition) {
        return requireTrue(openCondition, ErrorCode.LIVE_TRACK_CLOSED);
    }

    // 전체 구역 합쳐 잔여석 1개 이상인지. API 상태/selectSeat 검증용 (Redis pipeline 사용)
    public Mono<Boolean> hasRemainingSeats(Long scheduleId) {
        return seatPoolService.getRemainingSeatsTotal(scheduleId)
                .map(total -> total > 0);
    }

    // 라이브 트랙 오픈 여부: 잔여석 존재 + 대기열 0인 상태 10분 지속으로 마감되지 않음.
    public Mono<Boolean> isLiveTrackOpen(Long scheduleId) {
        return hasRemainingSeats(scheduleId)
                .flatMap(hasSeats -> hasSeats
                        ? isLiveTrackClosedByQueue(scheduleId).map(closed -> !closed)
                        : Mono.just(false));
    }

    // 대기열 사이즈 0이 10분 이상 지속되어 설정된 라이브 마감 플래그 존재 여부
    public Mono<Boolean> isLiveTrackClosedByQueue(Long scheduleId) {
        return redisTemplate.hasKey(RedisKeyGenerator.liveClosedKey(scheduleId));
    }

}
