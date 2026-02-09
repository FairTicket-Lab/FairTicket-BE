package com.fairticket.domain.payment.service;

import com.fairticket.domain.payment.entity.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortOneClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${fairticket.portone.api-key}")
    private String apiKey;

    @Value("${fairticket.portone.api-secret}")
    private String apiSecret;

    private static final String PORTONE_API_URL = "https://api.iamport.kr";

    /**
     * 액세스 토큰 발급
     */
    public Mono<String> getAccessToken() {
        return webClientBuilder.build()
                .post()
                .uri(PORTONE_API_URL + "/users/getToken")
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
                .doOnError(error -> log.error("PortOne 토큰 발급 실패", error));
    }

    /**
     * 결제 검증
     */
    public Mono<PaymentVerificationResult> verifyPayment(String impUid) {
        return getAccessToken()
                .flatMap(token -> webClientBuilder.build()
                        .get()
                        .uri(PORTONE_API_URL + "/payments/" + impUid)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> {
                            Map<String, Object> data = (Map<String, Object>) response.get("response");
                            return PaymentVerificationResult.builder()
                                    .impUid(impUid)
                                    .merchantUid((String) data.get("merchant_uid"))
                                    .amount(((Number) data.get("amount")).intValue())
                                    .status(mapStatus((String) data.get("status")))
                                    .build();
                        }))
                .doOnSuccess(result -> log.info("결제 검증 완료: impUid={}, status={}",
                        impUid, result.getStatus()));
    }

    /**
     * 결제 취소 (환불)
     */
    public Mono<RefundResult> cancelPayment(String impUid, int amount, String reason) {
        return getAccessToken()
                .flatMap(token -> webClientBuilder.build()
                        .post()
                        .uri(PORTONE_API_URL + "/payments/cancel")
                        .header("Authorization", "Bearer " + token)
                        .bodyValue(Map.of(
                                "imp_uid", impUid,
                                "amount", amount,
                                "reason", reason
                        ))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> {
                            Integer code = (Integer) response.get("code");
                            return RefundResult.builder()
                                    .success(code == 0)
                                    .message((String) response.get("message"))
                                    .build();
                        }))
                .doOnSuccess(result -> log.info("환불 처리 완료: impUid={}, success={}",
                        impUid, result.isSuccess()));
    }

    private PaymentStatus mapStatus(String portoneStatus) {
        return switch (portoneStatus) {
            case "paid" -> PaymentStatus.COMPLETED;
            case "cancelled" -> PaymentStatus.REFUNDED;
            case "failed" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
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