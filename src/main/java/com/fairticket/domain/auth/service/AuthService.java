package com.fairticket.domain.auth.service;

import com.fairticket.domain.auth.dto.AuthResponse;
import com.fairticket.domain.auth.dto.LoginRequest;
import com.fairticket.domain.auth.dto.SignupRequest;
import com.fairticket.domain.user.entity.Role;
import com.fairticket.domain.user.entity.User;
import com.fairticket.domain.user.repository.UserRepository;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import com.fairticket.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 인증 서비스. 회원가입(BCrypt 암호화) 및 로그인(JWT 발급) 처리.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** 회원가입: 이메일 중복 체크 → BCrypt 암호화 → 저장 → JWT 발급 */
    public Mono<AuthResponse> signup(SignupRequest request) {
        return userRepository.existsByEmail(request.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.<AuthResponse>error(new BusinessException(ErrorCode.DUPLICATE_EMAIL));
                    }

                    User user = User.builder()
                            .email(request.getEmail())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .name(request.getName())
                            .phone(request.getPhone())
                            .role(Role.USER.name())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return userRepository.save(user)
                            .map(saved -> {
                                String token = jwtProvider.createToken(saved.getId(), saved.getEmail(), saved.getRole());
                                return AuthResponse.builder()
                                        .userId(saved.getId())
                                        .email(saved.getEmail())
                                        .token(token)
                                        .build();
                            });
                });
    }

    /** 로그인: 이메일로 조회 → 비밀번호 검증 → JWT 발급 */
    public Mono<AuthResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.UNAUTHORIZED)))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.<AuthResponse>error(new BusinessException(ErrorCode.UNAUTHORIZED));
                    }

                    String token = jwtProvider.createToken(user.getId(), user.getEmail(), user.getRole());
                    return Mono.just(AuthResponse.builder()
                            .userId(user.getId())
                            .email(user.getEmail())
                            .token(token)
                            .build());
                });
    }
}
