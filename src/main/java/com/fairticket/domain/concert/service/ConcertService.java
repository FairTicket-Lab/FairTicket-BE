package com.fairticket.domain.concert.service;

import com.fairticket.domain.concert.dto.ConcertResponse;
import com.fairticket.domain.concert.dto.GradeDetailResponse;
import com.fairticket.domain.concert.dto.ScheduleResponse;
import com.fairticket.domain.concert.entity.Concert;
import com.fairticket.domain.concert.entity.Grade;
import com.fairticket.domain.concert.entity.Schedule;
import com.fairticket.domain.concert.entity.ScheduleStatus;
import com.fairticket.domain.concert.repository.ConcertRepository;
import com.fairticket.domain.concert.repository.GradeRepository;
import com.fairticket.domain.concert.repository.ScheduleRepository;
import com.fairticket.domain.concert.entity.Zone;
import com.fairticket.domain.concert.repository.ZoneRepository;
import com.fairticket.domain.seat.dto.GradeSeatCount;
import com.fairticket.domain.seat.repository.SeatRepository;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConcertService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ConcertRepository concertRepository;
    private final ScheduleRepository scheduleRepository;
    private final GradeRepository gradeRepository;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;

    public Flux<ConcertResponse> getConcerts() {
        return concertRepository.findAll()
                .flatMap(this::toConcertResponse);
    }

    public Mono<ConcertResponse> getConcertById(Long concertId) {
        return concertRepository.findById(concertId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.CONCERT_NOT_FOUND)))
                .flatMap(this::toConcertResponse);
    }

    private Mono<ConcertResponse> toConcertResponse(Concert concert) {
        return scheduleRepository.findByConcertId(concert.getId())
                .collectList()
                .flatMap(schedules -> buildResponse(concert, schedules));
    }

    private Mono<ConcertResponse> buildResponse(Concert concert, List<Schedule> schedules) {
        String saleStatus = deriveSaleStatus(schedules);
        String saleDate = deriveSaleDate(schedules);
        List<ScheduleResponse> dates = schedules.stream()
                .map(s -> ScheduleResponse.builder()
                        .id(s.getId())
                        .date(s.getDateTime().format(DATE_FORMAT))
                        .time(s.getDateTime().format(TIME_FORMAT))
                        .venue(concert.getVenue())
                        .available(ScheduleStatus.OPEN.name().equals(s.getStatus()))
                        .build())
                .collect(Collectors.toList());

        if (schedules.isEmpty()) {
            return Mono.just(ConcertResponse.builder()
                    .id(concert.getId())
                    .title(concert.getTitle())
                    .artist(concert.getArtist())
                    .venue(concert.getVenue())
                    .saleStatus("sold-out")
                    .saleDate(null)
                    .dates(List.of())
                    .grades(List.of())
                    .build());
        }

        Schedule refSchedule = chooseReferenceScheduleForGrades(schedules);
        return buildGrades(refSchedule)
                .map(grades -> ConcertResponse.builder()
                        .id(concert.getId())
                        .title(concert.getTitle())
                        .artist(concert.getArtist())
                        .venue(concert.getVenue())
                        .saleStatus(saleStatus)
                        .saleDate(saleDate)
                        .dates(dates)
                        .grades(grades)
                        .build());
    }

    private String deriveSaleStatus(List<Schedule> schedules) {
        if (schedules.isEmpty()) return "sold-out";
        boolean anyOpen = schedules.stream().anyMatch(s -> ScheduleStatus.OPEN.name().equals(s.getStatus()));
        boolean allUpcoming = schedules.stream().allMatch(s -> ScheduleStatus.UPCOMING.name().equals(s.getStatus()));
        boolean allClosed = schedules.stream().allMatch(s -> ScheduleStatus.CLOSED.name().equals(s.getStatus()));
        if (anyOpen) return "on-sale";
        if (allUpcoming) return "coming-soon";
        if (allClosed) return "sold-out";
        return "on-sale"; // mixed
    }

    private String deriveSaleDate(List<Schedule> schedules) {
        return schedules.stream()
                .filter(s -> ScheduleStatus.UPCOMING.name().equals(s.getStatus()))
                .findFirst()
                .map(s -> s.getTicketOpenAt() != null ? s.getTicketOpenAt().format(ISO_DATE_TIME) : null)
                .orElse(null);
    }

    private Schedule chooseReferenceScheduleForGrades(List<Schedule> schedules) {
        return schedules.stream()
                .filter(s -> ScheduleStatus.OPEN.name().equals(s.getStatus()))
                .findFirst()
                .or(() -> schedules.stream()
                        .filter(s -> ScheduleStatus.UPCOMING.name().equals(s.getStatus()))
                        .findFirst())
                .orElse(schedules.get(0));
    }

    private Mono<List<GradeDetailResponse>> buildGrades(Schedule refSchedule) {
        Mono<List<Grade>> gradesMono =
                gradeRepository.findByScheduleId(refSchedule.getId()).collectList();
        Mono<Map<String, Integer>> totalByGrade = zoneRepository.findByScheduleId(refSchedule.getId())
                .collectList()
                .map(zones -> zones.stream()
                        .collect(Collectors.groupingBy(Zone::getGrade,
                                Collectors.summingInt(z -> z.getSeatCount() != null ? z.getSeatCount() : 0))));
        Mono<Map<String, Long>> availableByGrade = seatRepository
                .findAvailableSeatCountByScheduleIdGroupByGrade(refSchedule.getId())
                .collectList()
                .map(list -> list.stream()
                        .collect(Collectors.toMap(GradeSeatCount::getGrade, GradeSeatCount::getCount)));

        return Mono.zip(gradesMono, totalByGrade, availableByGrade)
                .map(tuple -> {
                    List<Grade> grades = tuple.getT1();
                    Map<String, Integer> totalMap = tuple.getT2();
                    Map<String, Long> availableMap = tuple.getT3();
                    List<GradeDetailResponse> result = new ArrayList<>();
                    for (Grade g : grades) {
                        String grade = g.getGrade();
                        int totalSeats = totalMap.getOrDefault(grade, 0);
                        int availableSeats = Math.toIntExact(availableMap.getOrDefault(grade, 0L));
                        result.add(GradeDetailResponse.builder()
                                .id(grade != null ? grade.toLowerCase() : "")
                                .label(grade != null ? grade : "")
                                .price(g.getPrice() != null ? g.getPrice() : 0)
                                .totalSeats(totalSeats)
                                .availableSeats(availableSeats)
                                .build());
                    }
                    return result;
                });
    }
}
