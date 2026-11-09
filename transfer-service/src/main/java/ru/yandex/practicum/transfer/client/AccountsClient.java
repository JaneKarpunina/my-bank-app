package ru.yandex.practicum.transfer.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.transfer.dto.AccountTransferDto;

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
    public void executeTransfer(UUID idempotencyKey, AccountTransferDto dto) {

        String targetUrl = accountsServiceUrl + "/accounts/execute-transfer";

        log.debug("Выполнение межсервисного POST-запроса через WebClient." +
                        " Целевой URL: {}, Отправитель: {}, Получатель: {}, Сумма: {}, Idempotency-Key: {}",
                targetUrl, dto.sender(), dto.recipient(), dto.amount(), idempotencyKey);

        internalServicesWebClient.post()
                .uri(targetUrl)
                .header("X-Idempotency-Key", idempotencyKey.toString())
                .bodyValue(dto)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.debug("Успешный сетевой ответ получен от Accounts-service" +
                " для перевода (Idempotency-Key: {}).", idempotencyKey);
    }

    private void fallback(
            UUID idempotencyKey,
            AccountTransferDto dto,
            Throwable exception
    ) throws Throwable {

        log.error("Режим Fallback! Сработал предохранитель Circuit Breaker для вызова Accounts-service. " +
                        "Операция перевода от {} к {} на сумму {} отклонена шлюзом по таймауту или недоступности сети. " +
                        "Idempotency-Key: {}. Причина: {}",
                dto.sender(), dto.recipient(), dto.amount(), idempotencyKey, exception.getMessage(), exception);
        throw exception;
    }
}

