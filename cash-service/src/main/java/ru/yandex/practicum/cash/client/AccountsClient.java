package ru.yandex.practicum.cash.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.cash.dto.AccountOperationDto;

import java.util.UUID;

@Slf4j
@Component
public class AccountsClient {

    private final WebClient internalServicesWebClient;
    private final String accountsServiceUrl;

    public AccountsClient(WebClient internalServicesWebClient,
                          @Value("${app.services.accounts-url}") String accountsServiceUrl) {
        this.internalServicesWebClient = internalServicesWebClient;
        this.accountsServiceUrl = accountsServiceUrl;
    }


    @Retry(name = "accounts")
    @CircuitBreaker(name = "accounts", fallbackMethod = "fallback")
    public void executeCash(UUID idempotencyKey, AccountOperationDto dto) {

        String targetUrl = accountsServiceUrl + "/accounts/execute-cash";

        log.debug("Выполнение межсервисного POST-запроса через WebClient. Целевой URL: {}," +
                        " Пользователь: {}, Idempotency-Key: {}",
                targetUrl, dto.username(), idempotencyKey);
        internalServicesWebClient.post()
                .uri(targetUrl)
                .header("X-Idempotency-Key", idempotencyKey.toString())
                .bodyValue(dto)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.debug("Успешный сетевой ответ получен от " +
                "Accounts-service для пользователя {}. Ключ: {}", dto.username(), idempotencyKey);
    }

    private void fallback(
            UUID idempotencyKey,
            AccountOperationDto dto,
            Throwable exception
    ) throws Throwable {

        log.error("Режим Fallback! Сработал предохранитель Circuit Breaker для вызова Accounts-service. " +
                        "Операция кассы [{}] для пользователя {} отклонена по таймауту или недоступности сети. " +
                        "Idempotency-Key: {}. Причина: {}",
                dto.action(), dto.username(), idempotencyKey, exception.getMessage(), exception);

        throw exception;
    }
}
