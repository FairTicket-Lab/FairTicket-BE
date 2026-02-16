package com.fairticket.domain.seat.service;

import com.fairticket.domain.concert.entity.Zone;
import com.fairticket.domain.concert.repository.ZoneRepository;
import com.fairticket.domain.seat.dto.ZoneSeatAssignmentResponse;
import com.fairticket.domain.seat.entity.Seat;
import com.fairticket.domain.seat.repository.SeatRepository;
import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatPoolService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;

    /**
     * seats 테이블 기준으로 해당 회차 좌석 풀 초기화.
     * 단일 출처는 seats 테이블만 사용한다.
     *
     * 추가: 등급별 재고 카운터(stock:{scheduleId}:{grade})도 함께 초기화한다.
     * 카운터 초기값 = 해당 등급 전체 좌석 수 (SeatPool Set의 크기와 동기화).
     */
    public Mono<Void> initializeSeatPools(Long scheduleId) {
        return seatRepository.findByScheduleId(scheduleId)
                .collectMultimap(Seat::getZone, Seat::getSeatNumber)
                .flatMap(zoneToNumbers -> Flux.fromIterable(zoneToNumbers.entrySet())
                        .flatMap(entry -> initializeSeatPoolWithNumbers(
                                scheduleId,
                                entry.getKey(),
                                new ArrayList<>(entry.getValue())))
                        .then())
                .then(initializeStockCounters(scheduleId))
                .doOnSuccess(v -> log.info("좌석 풀 + 재고 카운터 초기화 완료: scheduleId={}", scheduleId));
    }

    /**
     * 등급별 재고 카운터 초기화.
     * seats 테이블에서 scheduleId 기준 등급별 좌석 수를 집계하여 stock 키에 저장.
     */
    private Mono<Void> initializeStockCounters(Long scheduleId) {
        return seatRepository.findSeatCountByScheduleIdGroupByGrade(scheduleId)
                .flatMap(gradeSeatCount -> {
                    String stockKey = RedisKeyGenerator.stockKey(scheduleId, gradeSeatCount.getGrade());
                    String countValue = String.valueOf(gradeSeatCount.getCount());
                    return redisTemplate.opsForValue().set(stockKey, countValue)
                            .doOnSuccess(v -> log.info("재고 카운터 초기화: scheduleId={}, grade={}, stock={}",
                                    scheduleId, gradeSeatCount.getGrade(), countValue));
                })
                .then();
    }

    /**
     * 지정한 구역·좌석 번호 목록으로 Redis 풀 초기화.
     * 기존 데이터를 삭제하고 새로 초기화합니다.
     */
    public Mono<Void> initializeSeatPoolWithNumbers(Long scheduleId, String zone, List<String> seatNumbers) {
        if (seatNumbers.isEmpty()) {
            return Mono.empty();
        }
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.delete(poolKey)
                .then(redisTemplate.opsForSet()
                        .add(poolKey, seatNumbers.toArray(new String[0])))
                .doOnSuccess(c -> log.info("좌석 풀 초기화: scheduleId={}, zone={}, count={}",
                        scheduleId, zone, seatNumbers.size()))
                .then();
    }

    /**
     * 잔여 좌석 수 (단일 구역 풀)
     */
    public Mono<Long> getRemainingSeats(Long scheduleId, String zone) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet().size(poolKey);
    }

    /**
     * 해당 회차 전체 구역의 잔여 좌석 수 합계.
     * Redis pipeline 효과를 위해 execute를 활용하여 sCard 요청을 수행한다.
     */
    public Mono<Long> getRemainingSeatsTotal(Long scheduleId) {
        return zoneRepository.findByScheduleId(scheduleId)
                .map(Zone::getZone)
                .collectList()
                .flatMap(zones -> {
                    if (zones.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return redisTemplate.execute(connection ->
                            Flux.fromIterable(zones)
                                    .map(z -> ByteBuffer.wrap(RedisKeyGenerator.seatsKey(scheduleId, z)
                                            .getBytes(StandardCharsets.UTF_8)))
                                    .flatMap(key -> connection.setCommands().sCard(key))
                                    .reduce(0L, Long::sum)
                    ).next();
                });
    }

    /**
     * 잔여 좌석 목록 (구역별)
     */
    public Flux<String> getAvailableSeats(Long scheduleId, String zone) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet().members(poolKey);
    }

    /**
     * 특정 좌석 선택 (풀에서 제거).
     * @return true: 성공, false: 이미 없음
     */
    public Mono<Boolean> selectSeat(Long scheduleId, String zone, String seatNumber) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet()
                .remove(poolKey, seatNumber)
                .map(removed -> removed > 0)
                .doOnSuccess(success -> log.info("좌석 선택: scheduleId={}, zone={}, seat={}, success={}",
                        scheduleId, zone, seatNumber, success));
    }

    /**
     * 좌석 반환 (취소/홀드 해제 시)
     */
    public Mono<Boolean> returnSeat(Long scheduleId, String zone, String seatNumber) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet()
                .add(poolKey, seatNumber)
                .map(added -> added > 0)
                .doOnSuccess(success -> log.info("좌석 반환: scheduleId={}, zone={}, seat={}",
                        scheduleId, zone, seatNumber));
    }

    /**
     * 랜덤 좌석 추출 (추첨: 해당 등급에 속한 구역들 중 하나에서 pop).
     * 라이브 종료 후 추첨 배정 시 사용
     */
    public Mono<ZoneSeatAssignmentResponse> popRandomSeat(Long scheduleId, String grade) {
        return zoneRepository.findByScheduleIdAndGrade(scheduleId, grade)
                .collectList()
                .flatMap(zones -> {
                    if (zones.isEmpty()) {
                        return Mono.empty();
                    }
                    int idx = ThreadLocalRandom.current().nextInt(zones.size());
                    String zone = zones.get(idx).getZone();
                    String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);

                    return redisTemplate.opsForSet().pop(poolKey)
                            .map(seatNumber -> {
                                log.info("좌석 추출: scheduleId={}, grade={}, zone={}, seat={}",
                                        scheduleId, grade, zone, seatNumber);
                                return new ZoneSeatAssignmentResponse(zone, seatNumber);
                            });
                });
    }

    /**
     * 해당 등급의 모든 잔여 좌석을 구역·좌석번호 목록으로 반환.
     * Fisher-Yates 셔플 후 순서대로 배정할 때 사용.
     */
    public Mono<List<ZoneSeatAssignmentResponse>> getAvailableSeatsForGrade(Long scheduleId, String grade) {
        return zoneRepository.findByScheduleIdAndGrade(scheduleId, grade)
                .flatMap(zone -> getAvailableSeats(scheduleId, zone.getZone())
                        .map(seatNumber -> new ZoneSeatAssignmentResponse(zone.getZone(), seatNumber)))
                .collectList();
    }

    /**
     * 추첨 쿼터: 등급별 추첨에 쓸 수 있는 최대 좌석 수.
     * seats 테이블 기준 해당 등급 좌석 수의 절반.
     */
    public Mono<Long> getRemainingSeatsLottery(Long scheduleId, String grade) {
        return seatRepository.countByScheduleIdAndGrade(scheduleId, grade)
                .map(total -> total / 2)
                .defaultIfEmpty(0L);
    }
}