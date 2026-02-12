package com.fairticket.domain.reservation.service;

import com.fairticket.domain.concert.entity.Grade;
import com.fairticket.domain.concert.entity.Schedule;
import com.fairticket.domain.concert.repository.GradeRepository;
import com.fairticket.domain.concert.service.ScheduleService;
import com.fairticket.domain.reservation.dto.GradeReservedSum;
import com.fairticket.domain.reservation.dto.LotteryReservationRequest;
import com.fairticket.domain.reservation.dto.LotteryResultResponse;
import com.fairticket.domain.reservation.dto.ReservationResponse;
import com.fairticket.domain.reservation.entity.Reservation;
import com.fairticket.domain.reservation.entity.ReservationSeat;
import com.fairticket.domain.reservation.entity.ReservationSeatStatus;
import com.fairticket.domain.reservation.entity.ReservationStatus;
import com.fairticket.domain.reservation.entity.TrackType;
import com.fairticket.domain.reservation.repository.ReservationRepository;
import com.fairticket.domain.reservation.repository.ReservationSeatRepository;
import com.fairticket.domain.seat.dto.GradeSeatCount;
import com.fairticket.domain.seat.dto.ZoneSeatAssignmentResponse;
import com.fairticket.domain.seat.entity.Seat;
import com.fairticket.domain.seat.entity.SeatStatus;
import com.fairticket.domain.seat.repository.SeatRepository;
import com.fairticket.domain.seat.service.SeatPoolService;
import com.fairticket.domain.reservation.constants.ReservationConstants;
import com.fairticket.domain.queue.service.QueueTokenService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryTrackService {
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final ScheduleService scheduleService;
    private final GradeRepository gradeRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final SeatPoolService seatPoolService;
    private final SeatRepository seatRepository;
    private final QueueTokenService queueTokenService;

    // 추첨 트랙 1인당 최대 수량 등은 {@link ReservationConstants} 사용

    // 추첨 트랙 예매 요청
    public Mono<ReservationResponse> createLotteryReservation(LotteryReservationRequest request, Long userId, String queueToken) {
        // 0. 대기열 토큰 검증 + 1회성 소비
        return queueTokenService.validateToken(userId, request.getScheduleId(), queueToken)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.<Void>error(new BusinessException(ErrorCode.INVALID_QUEUE_TOKEN));
                    }
                    return queueTokenService.consumeToken(userId, request.getScheduleId()).then();
                })
                // 1. 추첨 트랙 시간대 체크 (티켓 오픈 30분 전 ~ 티켓 오픈 20분 전: 진입 가능, 20분~15분: 진입 마감·기존 예약 결제만 가능)
                .then(scheduleService.findScheduleOrThrow(request.getScheduleId()))
                .flatMap(schedule -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime lotteryOpenAt = schedule.getTicketOpenAt().minusMinutes(ReservationConstants.LOTTERY_OPEN_MINUTES);
                    LocalDateTime lotteryEntryCloseAt = schedule.getTicketOpenAt().minusMinutes(ReservationConstants.LOTTERY_ENTRY_CLOSE_MINUTES);
                    if (now.isBefore(lotteryOpenAt)) {
                        return Mono.error(new BusinessException(ErrorCode.TICKET_NOT_OPENED));
                    }
                    if (!now.isBefore(lotteryEntryCloseAt)) {
                        return Mono.error(new BusinessException(ErrorCode.LOTTERY_ENTRY_CLOSED));
                    }
                    return Mono.just(schedule);
                })
                // 2. 중복 참여 체크 (추첨 결제 완료자면 라이브 참여 불가 + 해당 회차에 이미 예약 있으면 불가)
                .flatMap(schedule -> canParticipate(request.getScheduleId(), userId)
                        .flatMap(can -> can ? Mono.just(schedule) : Mono.<Schedule>error(new BusinessException(ErrorCode.ALREADY_PARTICIPATED))))
                .flatMap(schedule -> reservationRepository.existsByUserIdAndScheduleId(userId, request.getScheduleId())
                        .flatMap(exists -> exists ? Mono.<Schedule>error(new BusinessException(ErrorCode.ALREADY_PARTICIPATED)) : Mono.just(schedule)))
                // 2-1. 추첨 1인당 최대 2장 제한
                .flatMap(schedule -> reservationRepository.sumLotteryQuantityByUserAndSchedule(request.getScheduleId(), userId)
                        .flatMap(userLotteryQuantity -> {
                            long current = userLotteryQuantity != null ? userLotteryQuantity : 0L;
                            long maxAllowed = ReservationConstants.LOTTERY_MAX_QUANTITY_PER_USER - current;
                            if (request.getQuantity() <= 0 || request.getQuantity() > maxAllowed) {
                                return Mono.error(new BusinessException(ErrorCode.LOTTERY_MAX_QUANTITY_EXCEEDED));
                            }
                            return Mono.just(schedule);
                        }))
                // 3. 추첨 쿼터 체크 (등급별 전체의 절반, 나머지는 라이브로)
                .flatMap(schedule -> Mono.zip(
                        seatPoolService.getRemainingSeatsLottery(request.getScheduleId(), request.getGrade()),
                        reservationRepository.sumLotteryQuantityByScheduleAndGrade(request.getScheduleId(), request.getGrade())
                ))
                .flatMap(tuple -> {
                    long lotteryPoolSize = tuple.getT1();
                    long reserved = tuple.getT2() != null ? tuple.getT2() : 0L;
                    long remaining = lotteryPoolSize - reserved;
                    if (remaining < 0 || request.getQuantity() > remaining) {
                        return Mono.error(new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN));
                    }
                    Reservation reservation = Reservation.builder()
                            .userId(userId)
                            .scheduleId(request.getScheduleId())
                            .grade(request.getGrade())
                            .quantity(request.getQuantity())
                            .trackType(TrackType.LOTTERY.name())
                            .status(ReservationStatus.PENDING.name())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return reservationRepository.save(reservation);
                })
                // 4. 결제 요청 준비 (TODO(결제): 5분 이내 미결제 시 취소 스케줄러는 payment 담당자 구현)
                .map(saved -> createReservationResponse(saved, request, userId));
    }

    private ReservationResponse createReservationResponse(Reservation saved, LotteryReservationRequest request, Long userId) {
        log.info("추첨 예약 생성: reservationId={}, userId={}, scheduleId={}, grade={}",
                saved.getId(), userId, request.getScheduleId(), request.getGrade());

        return ReservationResponse.builder()
                .id(saved.getId())
                .scheduleId(saved.getScheduleId())
                .grade(saved.getGrade())
                .quantity(saved.getQuantity())
                .trackType(saved.getTrackType())
                .status(saved.getStatus())
                .message(ReservationConstants.MESSAGE_PAYMENT_DEADLINE_LOTTERY)
                .paymentDeadline(LocalDateTime.now().plusMinutes(ReservationConstants.PAYMENT_DEADLINE_MINUTES))
                .build();
    }

    // 추첨 결제 완료 처리
    public Mono<Void> onPaymentCompleted(Long reservationId, Long userId, Long scheduleId) {
        String lotteryPaidKey = RedisKeyGenerator.lotteryPaidKey(scheduleId);
        return reservationRepository.findById(reservationId)
                .flatMap(reservation -> {
                    reservation.setStatus(ReservationStatus.PAID_PENDING_SEAT.name());
                    reservation.setUpdatedAt(LocalDateTime.now());
                    return reservationRepository.save(reservation);
                })
                .then(redisTemplate.opsForSet().add(lotteryPaidKey, userId.toString()))
                .doOnSuccess(v -> log.info("추첨 결제 완료: reservationId={}, userId={}",
                        reservationId, userId))
                .then();
    }

    // 추첨 트랙 종료 시 호출
    // 풀 변경 없음. 좌석 배정·merge는 라이브 트랙 종료 후 assignSeatsToPaidLotteryAndMerge(scheduleId) 호출.
    public Mono<Void> onLotteryTrackEnd(Long scheduleId) {
        return Mono.fromRunnable(() -> log.info("추첨 트랙 종료: scheduleId={} (좌석 배정은 라이브 종료 후 수행)", scheduleId));
    }

    // 라이브 트랙 종료 후 호출
    // Fisher-Yates Shuffle로 등급별 잔여 좌석을 균등 랜덤 순열로 섞은 뒤, 예약 ID 순으로 순서대로 배정.
    // 1) 결제 완료된 추첨 예약을 등급별·ID순으로 정렬,
    // 2) 등급별로 Redis 잔여 좌석 목록을 가져와 Fisher-Yates 셔플,
    // 3) 셔플된 순서대로 예약에 좌석 배정 및 Redis에서 제거
    public Mono<Void> assignSeatsToPaidLotteryAndMerge(Long scheduleId) {
        return reservationRepository.findByScheduleIdAndTrackTypeAndStatus(
                        scheduleId, TrackType.LOTTERY.name(), ReservationStatus.PAID_PENDING_SEAT.name())
                .collectList()
                .flatMap(reservations -> {
                    if (reservations.isEmpty()) {
                        return Mono.<Void>empty();
                    }
                    reservations.sort(Comparator.comparing(Reservation::getId));
                    Map<String, List<Reservation>> byGrade = reservations.stream()
                            .collect(Collectors.groupingBy(Reservation::getGrade));
                    return Flux.fromIterable(byGrade.entrySet())
                            .flatMap(entry -> {
                                String grade = entry.getKey();
                                List<Reservation> resList = entry.getValue();
                                return seatPoolService.getAvailableSeatsForGrade(scheduleId, grade)
                                        .flatMap(seatList -> {
                                            fisherYatesShuffle(seatList);
                                            long need = resList.stream().mapToLong(Reservation::getQuantity).sum();
                                            if (need > seatList.size()) {
                                                return Mono.error(new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN));
                                            }
                                            AtomicInteger index = new AtomicInteger(0);
                                            return Flux.fromIterable(resList)
                                                    .concatMap(r -> {
                                                        int start = index.get();
                                                        int q = r.getQuantity();
                                                        index.addAndGet(q);
                                                        List<ZoneSeatAssignmentResponse> slice = seatList.subList(start, start + q);
                                                        return assignSeatsFromList(scheduleId, r, slice)
                                                                .then(updateReservationAssigned(r.getId()));
                                                    })
                                                    .then();
                                        });
                            })
                            .then();
                })
                .doOnSuccess(v -> log.info("추첨 좌석 배정 완료 (Fisher-Yates): scheduleId={}", scheduleId));
    }

    // Fisher-Yates Shuffle: 리스트를 균등 랜덤 순열로 섞는다 (in-place)
    private static void fisherYatesShuffle(List<ZoneSeatAssignmentResponse> list) {
        for (int i = list.size() - 1; i >= 1; i--) {
            int j = ThreadLocalRandom.current().nextInt(i + 1);
            ZoneSeatAssignmentResponse tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    // 셔플된 목록에서 정해진 구역·좌석을 해당 예약에 배정. Redis 풀에서 제거 후 ReservationSeat 저장, seats.status를 SOLD로 갱신
    private Mono<Void> assignSeatsFromList(Long scheduleId, Reservation reservation, List<ZoneSeatAssignmentResponse> assignments) {
        if (assignments.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(assignments)
                .flatMap(a -> seatPoolService.selectSeat(scheduleId, a.getZone(), a.getSeatNumber()).thenReturn(a))
                .collectList()
                .flatMap(kept -> batchFetchSeatsAndBuildReservationSeats(scheduleId, reservation.getId(), kept)
                        .flatMap(toSave -> reservationSeatRepository.saveAll(toSave)
                                .then(Flux.fromIterable(kept)
                                        .flatMap(a -> seatRepository.updateStatusByScheduleIdAndZoneAndSeatNumber(
                                                SeatStatus.SOLD.name(), scheduleId, a.getZone(), a.getSeatNumber()))
                                        .then())));
    }

    // 구역별로 좌석 일괄 조회 후, 배정 순서대로 ReservationSeat 목록 생성
    private Mono<List<ReservationSeat>> batchFetchSeatsAndBuildReservationSeats(
            Long scheduleId, Long reservationId, List<ZoneSeatAssignmentResponse> assignments) {
        if (assignments.isEmpty()) {
            return Mono.just(List.of());
        }
        Map<String, List<String>> zoneToSeatNumbers = assignments.stream()
                .collect(Collectors.groupingBy(ZoneSeatAssignmentResponse::getZone,
                        Collectors.mapping(ZoneSeatAssignmentResponse::getSeatNumber, Collectors.toList())));
        return Flux.fromIterable(zoneToSeatNumbers.entrySet())
                .flatMap(e -> seatRepository.findByScheduleIdAndZoneAndSeatNumberIn(scheduleId, e.getKey(), e.getValue()))
                .collectList()
                .flatMap(seats -> {
                    Map<String, Seat> keyToSeat = seats.stream()
                            .collect(Collectors.toMap(s -> s.getZone() + "_" + s.getSeatNumber(), Function.identity()));
                    List<ReservationSeat> toSave = new ArrayList<>();
                    for (ZoneSeatAssignmentResponse a : assignments) {
                        Seat seat = keyToSeat.get(a.getZone() + "_" + a.getSeatNumber());
                        if (seat == null) {
                            return Mono.<List<ReservationSeat>>error(new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN));
                        }
                        toSave.add(ReservationSeat.builder()
                                .reservationId(reservationId)
                                .seatId(seat.getId())
                                .zone(a.getZone())
                                .seatNumber(a.getSeatNumber())
                                .status(ReservationSeatStatus.ASSIGNED.name())
                                .assignedAt(LocalDateTime.now())
                                .createdAt(LocalDateTime.now())
                                .build());
                    }
                    return Mono.just(toSave);
                });
    }

    // 예약을 ASSIGNED로 전환. quantity를 실제 배정된 reservation_seats 개수와 동기화한다
    private Mono<Void> updateReservationAssigned(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .flatMap(r -> reservationSeatRepository.findByReservationId(reservationId)
                        .filter(rs -> ReservationSeatStatus.ASSIGNED.name().equals(rs.getStatus()))
                        .count()
                        .flatMap(count -> {
                            r.setQuantity(count.intValue());
                            r.setStatus(ReservationStatus.ASSIGNED.name());
                            r.setUpdatedAt(LocalDateTime.now());
                            return reservationRepository.save(r);
                        }))
                .then();
    }

    // 양 트랙 중복 참여 체크.
    // 추첨 결제 성공자 목록(lotteryPaidKey)에 있으면 라이브 참여 불가.
    // @return true: 참여 가능, false: 이미 추첨으로 결제함
    public Mono<Boolean> canParticipate(Long scheduleId, Long userId) {
        String lotteryPaidKey = RedisKeyGenerator.lotteryPaidKey(scheduleId);
        return redisTemplate.opsForSet()
                .isMember(lotteryPaidKey, userId.toString())
                .map(isMember -> !isMember);
    }

    // 추첨 결제 마감 시각(티켓 오픈 15분 전) 경과 여부. PaymentService에서 결제 수락 시 검증용
    public Mono<Boolean> isLotteryPaymentDeadlinePassed(Long scheduleId) {
        return scheduleService.findScheduleOrThrow(scheduleId)
                .map(schedule -> !LocalDateTime.now().isBefore(
                        schedule.getTicketOpenAt().minusMinutes(ReservationConstants.LOTTERY_PAYMENT_CLOSE_MINUTES)));
    }

    // 특정 등급이 추첨 절반 할당에 도달했는지 여부 (seats 테이블 기준)
    // 도달 시 해당 등급만 마감(라이브 예매 불가), 미도달 시 해당 등급 예매 가능.
    public Mono<Boolean> hasLotteryQuotaReachedForGrade(Long scheduleId, String grade) {
        return seatRepository.countByScheduleIdAndGrade(scheduleId, grade)
                .flatMap(gradeTotal -> reservationRepository.sumLotteryQuantityByScheduleAndGrade(scheduleId, grade)
                        .map(reserved -> (reserved != null ? reserved : 0L) >= (gradeTotal / 2)))
                .defaultIfEmpty(false);
    }

    // 추첨 트랙 할당 좌석 수 도달 여부 (전체)
    // 모든 등급이 등급별 절반 할당에 도달했을 때만 true. 등급별 집계 쿼리 2회로 조회.
    public Mono<Boolean> hasLotteryQuotaReached(Long scheduleId) {
        Mono<Map<String, Long>> seatCountsMono = seatRepository.findSeatCountByScheduleIdGroupByGrade(scheduleId)
                .collectMap(GradeSeatCount::getGrade, GradeSeatCount::getCount);
        Mono<Map<String, Long>> reservedMono = reservationRepository.findLotteryReservedSumByScheduleIdGroupByGrade(scheduleId)
                .collectMap(GradeReservedSum::getGrade, GradeReservedSum::getTotal);
        Mono<List<String>> gradesMono = gradeRepository.findByScheduleId(scheduleId)
                .map(Grade::getGrade)
                .collectList();
        return Mono.zip(seatCountsMono, reservedMono, gradesMono)
                .map(tuple -> {
                    Map<String, Long> seatCounts = tuple.getT1();
                    Map<String, Long> reserved = tuple.getT2();
                    List<String> grades = tuple.getT3();
                    return !grades.isEmpty() && grades.stream()
                            .allMatch(grade -> reserved.getOrDefault(grade, 0L) >= (seatCounts.getOrDefault(grade, 0L) / 2));
                });
    }

    // 추첨 예약 단건 결과 조회 (본인 예약만). 당첨/미당첨·좌석 배정 상태 포함
    public Mono<LotteryResultResponse> getLotteryReservationResult(Long reservationId, Long userId) {
        return reservationRepository.findById(reservationId)
                .filter(r -> TrackType.LOTTERY.name().equals(r.getTrackType()) && r.getUserId().equals(userId))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND)))
                .flatMap(this::toLotteryResultResponse);
    }

    // 해당 회차 내 추첨 예약 목록 (본인 것만). 당첨 결과·좌석 포함
    public Flux<LotteryResultResponse> getMyLotteryResultsBySchedule(Long scheduleId, Long userId) {
        return reservationRepository.findByUserIdAndScheduleIdAndTrackType(userId, scheduleId, TrackType.LOTTERY.name())
                .flatMap(this::toLotteryResultResponse);
    }

    private Mono<LotteryResultResponse> toLotteryResultResponse(Reservation r) {
        String resultType = toResultType(r.getStatus());
        String message = toResultMessage(r.getStatus());
        LocalDateTime paymentDeadline = ReservationStatus.PENDING.name().equals(r.getStatus())
                ? r.getCreatedAt().plusMinutes(ReservationConstants.PAYMENT_DEADLINE_MINUTES) : null;

        if (ReservationStatus.ASSIGNED.name().equals(r.getStatus())) {
            return reservationSeatRepository.findByReservationId(r.getId())
                    .filter(rs -> ReservationSeatStatus.ASSIGNED.name().equals(rs.getStatus()))
                    .map(rs -> LotteryResultResponse.AssignedSeatDto.builder()
                            .zone(rs.getZone())
                            .seatNumber(rs.getSeatNumber())
                            .build())
                    .collectList()
                    .map(seats -> LotteryResultResponse.builder()
                            .id(r.getId())
                            .scheduleId(r.getScheduleId())
                            .grade(r.getGrade())
                            .quantity(r.getQuantity())
                            .status(r.getStatus())
                            .resultType(resultType)
                            .message(message)
                            .paymentDeadline(paymentDeadline)
                            .seats(seats)
                            .build());
        }
        return Mono.just(LotteryResultResponse.builder()
                .id(r.getId())
                .scheduleId(r.getScheduleId())
                .grade(r.getGrade())
                .quantity(r.getQuantity())
                .status(r.getStatus())
                .resultType(resultType)
                .message(message)
                .paymentDeadline(paymentDeadline)
                .seats(null)
                .build());
    }

    private static String toResultType(String status) {
        switch (status == null ? "" : status) {
            case "PENDING": return "PAYMENT_PENDING";
            case "PAID_PENDING_SEAT": return "WON";
            case "ASSIGNED": return "ASSIGNED";
            case "CANCELLED":
            case "REFUNDED": return "LOST";
            default: return status;
        }
    }

    private static String toResultMessage(String status) {
        switch (status == null ? "" : status) {
            case "PENDING": return "결제 대기 중입니다. 5분 내 결제해 주세요.";
            case "PAID_PENDING_SEAT": return "당첨되었습니다. 좌석 배정 후 안내됩니다.";
            case "ASSIGNED": return "좌석 배정이 완료되었습니다.";
            case "CANCELLED": return "미당첨(결제 기한 초과) 또는 취소되었습니다.";
            case "REFUNDED": return "환불 처리되었습니다.";
            default: return "";
        }
    }
}
