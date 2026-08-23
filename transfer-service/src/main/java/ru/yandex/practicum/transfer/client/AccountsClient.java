package ru.yandex.practicum.transfer.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.transfer.dto.AccountTransferDto;
import ru.yandex.practicum.transfer.exception.AccountsServiceUnavailableException;

import java.util.UUID;

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
        internalServicesWebClient.post()
                .uri(accountsServiceUrl + "/accounts/execute-transfer")
                .header("X-Idempotency-Key", idempotencyKey.toString())
                .bodyValue(dto)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private void fallback(
            UUID idempotencyKey,
            AccountTransferDto dto,
            Throwable exception
    ) {
        throw new AccountsServiceUnavailableException(exception);
    }
}

