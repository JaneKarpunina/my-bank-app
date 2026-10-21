package ru.yandex.practicum.mybankfront.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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

    @Retry(name = "gateway-get")
    @CircuitBreaker(name = "gateway-get", fallbackMethod = "fallbackGet")
    public AccountResponse getAccountData(String uri, String token) {
        return webClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(AccountResponse.class)
                .block();
    }

    @CircuitBreaker(name = "gateway-post", fallbackMethod = "fallbackPost")
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
        throw new RuntimeException("Сервер временно недоступен", exception);
    }


    private void fallbackPost(String uri, String token, UUID idempotencyKey, Object bodyPayload, Throwable exception) {

        if (exception instanceof WebClientResponseException) {
            throw (WebClientResponseException) exception;
        }
        throw new IllegalStateException("Банковский шлюз временно недоступен. Операция отклонена предохранителем.");
    }
}

