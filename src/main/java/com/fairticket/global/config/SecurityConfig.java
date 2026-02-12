package com.fairticket.global.config;

import com.fairticket.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security WebFlux 설정.
 * CSRF/httpBasic/formLogin 비활성화, JWT 필터 등록, 경로별 인가 설정.
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 인증 불필요
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        // 공연·스케줄 조회 (비인증 허용)
                        .pathMatchers(HttpMethod.GET, "/api/v1/concerts/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/schedules/**").permitAll()
                        // 결제 Webhook (PortOne 서버→서버 호출)
                        .pathMatchers(HttpMethod.POST, "/api/v1/payment/webhook").permitAll()
                        // Actuator
                        .pathMatchers("/actuator/**").permitAll()
                        // Swagger (WebFlux)
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                        // 나머지 인증 필요
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
