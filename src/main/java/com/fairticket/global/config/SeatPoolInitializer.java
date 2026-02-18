package com.fairticket.global.config;

import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.domain.seat.service.SeatPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatPoolInitializer {

    private final ScheduleRepository scheduleRepository;
    private final SeatPoolService seatPoolService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeAllSeatPools() {
        log.info("===== 좌석 풀 + 재고 카운터 자동 초기화 시작 =====");
        scheduleRepository.findAll()
                .flatMap(schedule -> seatPoolService.initializeSeatPools(schedule.getId())
                        .doOnSuccess(v -> log.info("스케줄 {} 초기화 완료", schedule.getId()))
                        .onErrorResume(e -> {
                            log.warn("스케줄 {} 초기화 실패: {}", schedule.getId(), e.getMessage());
                            return reactor.core.publisher.Mono.empty();
                        }))
                .doOnComplete(() -> log.info("===== 좌석 풀 + 재고 카운터 자동 초기화 완료 ====="))
                .subscribe();
    }
}
