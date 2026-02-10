package com.fairticket.domain.seat.service;

import com.fairticket.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatPoolService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // 좌석 풀 초기화 (공연 생성 시 호출)
    public Mono<Void> initializeSeatPool(Long scheduleId, String grade, int totalSeats) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, grade);
        // 좌석 번호 생성 (1 ~ totalSeats)
        String[] seatNumbers = IntStream.rangeClosed(1, totalSeats)
                .mapToObj(String::valueOf)
                .toArray(String[]::new);
        return redisTemplate.opsForSet()
                .add(poolKey, seatNumbers)
                .doOnSuccess(count -> log.info("좌석 풀 초기화 완료: scheduleId={}, grade={}, seats={}",
                        scheduleId, grade, totalSeats))
                .then();
    }

    // 잔여 좌석 수 조회
    public Mono<Long> getRemainingSeats(Long scheduleId, String grade) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, grade);
        return redisTemplate.opsForSet().size(poolKey);
    }

    // 잔여 좌석 목록 조회
    public Flux<String> getAvailableSeats(Long scheduleId, String grade) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, grade);
        return redisTemplate.opsForSet().members(poolKey);
    }

    // 랜덤 좌석 추출 (장바구니 트랙용) - Set에서 하나를 pop하여 반환
    public Mono<String> popRandomSeat(Long scheduleId, String grade) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, grade);
        return redisTemplate.opsForSet().pop(poolKey)
                .doOnSuccess(seat -> {
                    if (seat != null) {
                        log.info("좌석 추출: scheduleId={}, grade={}, seat={}", scheduleId, grade, seat);
                    }
                });
    }

    // 특정 좌석 선택 (라이브 트랙용)
    // @return true: 선택 성공, false: 이미 선택된 좌석
    public Mono<Boolean> selectSeat(Long scheduleId, String grade, String seatNumber) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, grade);
        return redisTemplate.opsForSet()
                .remove(poolKey, seatNumber)
                .map(removed -> removed > 0)
                .doOnSuccess(success -> log.info("좌석 선택: scheduleId={}, grade={}, seat={}, success={}",
                        scheduleId, grade, seatNumber, success));
    }

    // 좌석 반환 (취소/환불/홀드 만료 시)
    public Mono<Boolean> returnSeat(Long scheduleId, String grade, String seatNumber) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, grade);
        return redisTemplate.opsForSet()
                .add(poolKey, seatNumber)
                .map(added -> added > 0)
                .doOnSuccess(success -> log.info("좌석 반환: scheduleId={}, grade={}, seat={}",
                        scheduleId, grade, seatNumber));
    }

    // 연속 좌석 찾기 (연석 배정용)
    // @param count 필요한 연속 좌석 수
    // @return 연속 좌석 리스트 (없으면 빈 리스트)
    public Mono<List<String>> findConsecutiveSeats(Long scheduleId, String grade, int count) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, grade);
        return redisTemplate.opsForSet().members(poolKey)
                .collectList()
                .map(seats -> {
                    // 좌석 번호를 정수로 변환 후 정렬
                    List<Integer> sortedSeats = seats.stream()
                            .map(Integer::parseInt)
                            .sorted()
                            .collect(Collectors.toList());
                    // 연속 좌석 찾기
                    for (int i = 0; i <= sortedSeats.size() - count; i++) {
                        boolean consecutive = true;
                        for (int j = 0; j < count - 1; j++) {
                            if (sortedSeats.get(i + j + 1) - sortedSeats.get(i + j) != 1) {
                                consecutive = false;
                                break;
                            }
                        }
                        if (consecutive) {
                            List<String> result = new ArrayList<>();
                            for (int j = 0; j < count; j++) {
                                result.add(String.valueOf(sortedSeats.get(i + j)));
                            }
                            log.info("연속 좌석 발견: scheduleId={}, grade={}, seats={}",
                                    scheduleId, grade, result);
                            return result;
                        }
                    }
                    log.info("연속 좌석 없음: scheduleId={}, grade={}, count={}",
                            scheduleId, grade, count);
                    return Collections.<String>emptyList();
                });
    }
}
