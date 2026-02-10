package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.seat.dto.SeatSelectionRequest;
import com.fairticket.domain.seat.dto.SeatSelectionResponse;
import com.fairticket.domain.seat.service.SeatHoldService;
import com.fairticket.domain.seat.service.SeatPoolService;
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
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTrackService {
    private final SeatPoolService seatPoolService;
    private final SeatHoldService seatHoldService;
    private final CartTrackService cartTrackService;
    private final ReservationRepository reservationRepository;
    private final ScheduleRepository scheduleRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final int MAX_LIVE_CAPACITY = 300;
    private static final LocalTime LIVE_DEADLINE = LocalTime.of(21, 15);

    // 라이브 트랙 오픈 여부 확인
    public Mono<Boolean> isLiveTrackOpen(Long scheduleId) {
        return Mono.zip(
                // 조건 1: 시간 체크 (21:15 이전)
                Mono.just(LocalTime.now().isBefore(LIVE_DEADLINE)),
                // 조건 2: 인원 체크 (300명 미만)
                getSoldCount(scheduleId).map(count -> count < MAX_LIVE_CAPACITY),
                // 조건 3: 잔여석 체크
                hasRemainingSeats(scheduleId)
        ).map(tuple -> tuple.getT1() && tuple.getT2() && tuple.getT3());
    }

    // 좌석 선택 (라이브 트랙)
    public Mono<SeatSelectionResponse> selectSeat(SeatSelectionRequest request, Long userId) {
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
                // 2. 라이브 트랙 오픈 여부 확인
                .then(isLiveTrackOpen(request.getScheduleId()))
                .flatMap(isOpen -> {
                    if (!isOpen) {
                        return Mono.error(new BusinessException(ErrorCode.LIVE_TRACK_CLOSED));
                    }
                    // 3. 중복 참여 체크
                    return cartTrackService.canParticipate(request.getScheduleId(), userId);
                })
                .flatMap(canParticipate -> {
                    if (!canParticipate) {
                        return Mono.error(new BusinessException(ErrorCode.ALREADY_PARTICIPATED));
                    }
                    // 4. 좌석 풀에서 제거
                    return seatPoolService.selectSeat(
                            request.getScheduleId(),
                            request.getGrade(),
                            request.getSeatNumber());
                })
                .flatMap(selected -> {
                    if (!selected) {
                        return Mono.error(new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN));
                    }
                    // 5. 홀드 설정
                    return seatHoldService.holdSeat(
                            request.getScheduleId(),
                            request.getGrade(),
                            request.getSeatNumber(),
                            userId);
                })
                .flatMap(held -> {
                    // 6. 예약 생성
                    Reservation reservation = Reservation.builder()
                            .userId(userId)
                            .scheduleId(request.getScheduleId())
                            .grade(request.getGrade())
                            .quantity(1)
                            .trackType(TrackType.LIVE.name())
                            .status(ReservationStatus.PENDING.name())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return reservationRepository.save(reservation);
                })
                .map(reservation -> {
                    log.info("라이브 트랙 좌석 선택: reservationId={}, userId={}, seat={}",
                            reservation.getId(), userId, request.getSeatNumber());
                    return SeatSelectionResponse.builder()
                            .scheduleId(request.getScheduleId())
                            .grade(request.getGrade())
                            .seatNumber(request.getSeatNumber())
                            .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                            .message("좌석이 10분간 홀드되었습니다. 결제를 완료해주세요.")
                            .build();
                });
    }

    // 좌석 선택 취소
    public Mono<Void> releaseSeat(Long scheduleId, String grade, String seatNumber, Long userId) {
        // 1. 홀드 소유자 확인
        return seatHoldService.getHoldOwner(scheduleId, grade, seatNumber)
                .filter(owner -> owner.equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED)))
                // 2. 홀드 해제
                .then(seatHoldService.releaseHold(scheduleId, grade, seatNumber))
                // 3. 좌석 풀에 반환
                .then(seatPoolService.returnSeat(scheduleId, grade, seatNumber))
                .then();
    }

    // 잔여 좌석 조회
    public Mono<List<String>> getAvailableSeats(Long scheduleId, String grade) {
        return seatPoolService.getAvailableSeats(scheduleId, grade)
                .collectList();
    }

    private Mono<Long> getSoldCount(Long scheduleId) {
        String soldKey = RedisKeyGenerator.soldLiveKey(scheduleId);
        return redisTemplate.opsForValue().get(soldKey)
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }

    private Mono<Boolean> hasRemainingSeats(Long scheduleId) {
        return Flux.just("VIP", "R", "S", "A")
                .flatMap(grade -> seatPoolService.getRemainingSeats(scheduleId, grade))
                .reduce(0L, Long::sum)
                .map(total -> total > 0);
    }
}
