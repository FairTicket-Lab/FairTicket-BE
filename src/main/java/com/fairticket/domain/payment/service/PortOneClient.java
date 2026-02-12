package com.fairticket.domain.payment.service;

import com.fairticket.domain.payment.entity.PaymentStatus;
import com.fairticket.global.exception.BusinessException;
import com.fairticket.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@Service
@SuppressWarnings("unchecked")
public class PortOneClient {

    private WebClient webClient;

    @Value("${fairticket.portone.api-key}")
    private String apiKey;

    @Value("${fairticket.portone.api-secret}")
    private String apiSecret;

    private static final String PORTONE_API_URL = "https://api.iamport.kr";

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(PORTONE_API_URL)
                .build();
    }

    // 액세스 토큰 발급
    public Mono<String> getAccessToken() {
        return webClient.post()
                .uri("/users/getToken")
                .bodyValue(Map.of(
                        "imp_key", apiKey,
                        "imp_secret", apiSecret
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> responseBody = (Map<String, Object>) response.get("response");
                    return (String) responseBody.get("access_token");
                })
                .doOnSuccess(token -> log.debug("PortOne 액세스 토큰 발급 완료"))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("PortOne 토큰 발급 실패: status={}, body={}",
                            e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.error(new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "PortOne 인증 실패: " + e.getResponseBodyAsString()));
                })
                .onErrorResume(e -> !(e instanceof BusinessException), e -> {
                    log.error("PortOne 토큰 발급 중 오류: {}", e.getMessage());
                    return Mono.error(new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "PortOne 연결 실패: " + e.getMessage()));
                });
    }

    // 결제 검증
    public Mono<PaymentVerificationResult> verifyPayment(String impUid) {
        return getAccessToken()
                .flatMap(token -> webClient.get()
                        .uri("/payments/" + impUid)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .<PaymentVerificationResult>map(response -> {
                            Map<String, Object> data = (Map<String, Object>) response.get("response");
                            return PaymentVerificationResult.builder()
                                    .impUid(impUid)
                                    .merchantUid((String) data.get("merchant_uid"))
                                    .amount(((Number) data.get("amount")).intValue())
                                    .status(mapStatus((String) data.get("status")))
                                    .build();
                        })
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.error("PortOne 결제 검증 실패: impUid={}, status={}, body={}",
                                    impUid, e.getStatusCode(), e.getResponseBodyAsString());
                            return Mono.error(new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                                    "PortOne 결제 검증 실패: " + e.getResponseBodyAsString()));
                        }))
                .doOnSuccess(result -> log.info("결제 검증 완료: impUid={}, status={}",
                        impUid, result.getStatus()))
                .onErrorResume(e -> !(e instanceof BusinessException), e -> {
                    log.error("PortOne 결제 검증 중 오류: impUid={}, error={}", impUid, e.getMessage());
                    return Mono.error(new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "PortOne 결제 검증 오류: " + e.getMessage()));
                });
    }

    // 결제 취소 (환불)
    public Mono<RefundResult> cancelPayment(String impUid, int amount, String reason) {
        return getAccessToken()
                .flatMap(token -> webClient.post()
                        .uri("/payments/cancel")
                        .header("Authorization", "Bearer " + token)
                        .bodyValue(Map.of(
                                "imp_uid", impUid,
                                "amount", amount,
                                "reason", reason
                        ))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .<RefundResult>map(response -> {
                            Integer code = (Integer) response.get("code");
                            return RefundResult.builder()
                                    .success(code == 0)
                                    .message((String) response.get("message"))
                                    .build();
                        })
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.error("PortOne 환불 실패: impUid={}, status={}, body={}",
                                    impUid, e.getStatusCode(), e.getResponseBodyAsString());
                            return Mono.error(new BusinessException(ErrorCode.REFUND_FAILED,
                                    "PortOne 환불 실패: " + e.getResponseBodyAsString()));
                        }))
                .doOnSuccess(result -> log.info("환불 처리 완료: impUid={}, success={}",
                        impUid, result.isSuccess()))
                .onErrorResume(e -> !(e instanceof BusinessException), e -> {
                    log.error("PortOne 환불 중 오류: impUid={}, error={}", impUid, e.getMessage());
                    return Mono.error(new BusinessException(ErrorCode.REFUND_FAILED,
                            "PortOne 환불 오류: " + e.getMessage()));
                });
    }

    private PaymentStatus mapStatus(String portoneStatus) {
        return switch (portoneStatus) {
            case "paid"      -> PaymentStatus.COMPLETED;
            case "cancelled" -> PaymentStatus.REFUNDED;
            case "failed"    -> PaymentStatus.FAILED;
            default          -> PaymentStatus.PENDING;
        };
    }

    @lombok.Builder
    @lombok.Getter
    public static class PaymentVerificationResult {
        private String impUid;
        private String merchantUid;
        private Integer amount;
        private PaymentStatus status;
    }

    @lombok.Builder
    @lombok.Getter
    public static class RefundResult {
        private boolean success;
        private String message;
    }
}