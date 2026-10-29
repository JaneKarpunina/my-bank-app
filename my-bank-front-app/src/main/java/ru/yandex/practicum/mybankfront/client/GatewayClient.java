package ru.yandex.practicum.mybankfront.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.yandex.practicum.mybankfront.dto.AccountResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class GatewayClient {

    private final WebClient webClient;

    public GatewayClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Retry(name = "gateway-get")
    @CircuitBreaker(name = "gateway-get", fallbackMethod = "fallbackGet")
    public AccountResponse getAccountData(String uri, String token) {
        log.debug("Фронтенд отправляет исходящий REST-запрос [GET] через шлюз на URI: {}", uri);

        AccountResponse response = webClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(AccountResponse.class)
                .block();

        log.debug("Успешно получены данные аккаунта от API Gateway по пути: {}", uri);
        return response;
    }

    @CircuitBreaker(name = "gateway-post", fallbackMethod = "fallbackPost")
    public void sendPostOperation(String uri, String token, UUID idempotencyKey, Object bodyPayload) {
        log.debug("Фронтенд отправляет исходящую POST-операцию через шлюз на URI: {}." +
                " Idempotency-Key: {}", uri, idempotencyKey);
        webClient.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", idempotencyKey.toString())
                .bodyValue(bodyPayload)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.debug("POST-операция успешно доставлена и обработана шлюзом на URI: {}. " +
                "Idempotency-Key: {}", uri, idempotencyKey);
    }


    private AccountResponse fallbackGet(String uri, String token, Throwable exception) {

        log.error("Режим Fallback [GET]! Сработал предохранитель Circuit Breaker для пути: {}." +
                        " Шлюз недоступен. Причина: {}",
                uri, exception.getMessage(), exception);
        throw new RuntimeException("Сервер временно недоступен", exception);
    }


    private void fallbackPost(String uri, String token, UUID idempotencyKey, Object bodyPayload, Throwable exception) {

        if (exception instanceof WebClientResponseException) {

            log.warn("Бизнес-отказ при POST-операции на URI: {} (Idempotency-Key: {}). Ответ бэкенда: {}",
                    uri, idempotencyKey, ((WebClientResponseException) exception).getResponseBodyAsString());
            throw (WebClientResponseException) exception;
        }

        log.error("Режим Fallback [POST]! Сработал предохранитель Circuit Breaker для пути: {} (Idempotency-Key: {}). " +
                "Сетевой вызов заблокирован. Причина: {}", uri, idempotencyKey, exception.getMessage(), exception);
        throw new IllegalStateException("Банковский шлюз временно недоступен. Операция отклонена предохранителем.");
    }
}

