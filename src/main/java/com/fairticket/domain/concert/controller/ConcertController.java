package com.fairticket.domain.concert.controller;

import com.fairticket.domain.concert.dto.ConcertResponse;
import com.fairticket.domain.concert.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ConcertResponse> getConcerts() {
        return concertService.getConcerts();
    }

    @GetMapping(value = "/{concertId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ConcertResponse> getConcertById(@PathVariable Long concertId) {
        return concertService.getConcertById(concertId);
    }
}
