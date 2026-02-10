package com.fairticket.domain.reservation.controller;

import com.fairticket.domain.reservation.dto.CartReservationRequest;
import com.fairticket.domain.reservation.dto.ReservationResponse;
import com.fairticket.domain.reservation.service.CartTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartTrackController {
    private final CartTrackService cartTrackService;

    @PostMapping("/reservation")
    public Mono<ResponseEntity<ReservationResponse>> createReservation(
            @RequestBody CartReservationRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return cartTrackService.createCartReservation(request, userId)
                .map(ResponseEntity::ok);
    }
}
