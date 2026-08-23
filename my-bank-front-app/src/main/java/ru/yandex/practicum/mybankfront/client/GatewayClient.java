package ru.yandex.practicum.mybankfront.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.mybankfront.dto.AccountResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class GatewayClient {

    private final WebClient webClient;

    public GatewayClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Retry(name = "gateway")
    @CircuitBreaker(name = "gateway", fallbackMethod = "fallbackGet")
    public AccountResponse getAccountData(String uri, String token) {
        return webClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(AccountResponse.class)
                .block();
    }

    @CircuitBreaker(name = "gateway", fallbackMethod = "fallbackPost")
    public void sendPostOperation(String uri, String token, UUID idempotencyKey, Object bodyPayload) {
        webClient.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", idempotencyKey.toString())
                .bodyValue(bodyPayload)
                .retrieve()
                .toBodilessEntity()
                .block();
    }


    private AccountResponse fallbackGet(String uri, String token, Throwable exception) {
        return new AccountResponse(
                "unknown", LocalDate.now(), 0, List.of());
    }


    private void fallbackPost(String uri, String token, UUID idempotencyKey, Object bodyPayload, Throwable exception) {
        throw new IllegalStateException("Банковский шлюз временно недоступен. Операция отклонена предохранителем.");
    }
}

