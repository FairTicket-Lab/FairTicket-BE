package com.fairticket.global.security;

import com.fairticket.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis 기반 Rate Limiting WebFilter.
 * 인증된 유저는 userId, 비인증은 IP 기준으로 분당 60회 제한.
 * 초과 시 429 Too Many Requests 반환.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String RATE_LIMIT_PREFIX = "rate-limit:";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal().toString())
                .defaultIfEmpty(getClientIp(exchange))
                .flatMap(identifier -> {
                    String key = RATE_LIMIT_PREFIX + identifier;

                    return redisTemplate.opsForValue().increment(key)
                            .flatMap(count -> {
                                if (count == 1) {
                                    return redisTemplate.expire(key, WINDOW)
                                            .thenReturn(count);
                                }
                                return Mono.just(count);
                            })
                            .flatMap(count -> {
                                if (count > MAX_REQUESTS_PER_MINUTE) {
                                    log.warn("Rate limit exceeded: identifier={}, count={}", identifier, count);
                                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                    exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "60");
                                    return exchange.getResponse().setComplete();
                                }

                                exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",
                                        String.valueOf(MAX_REQUESTS_PER_MINUTE - count));
                                return chain.filter(exchange);
                            });
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
