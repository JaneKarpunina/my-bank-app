package ru.yandex.practicum.cash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.yandex.practicum.cash.dto.AccountOperationDto;
import ru.yandex.practicum.cash.dto.CashRequest;
import ru.yandex.practicum.cash.entity.IdempotencyKey;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.repository.IdempotencyRepository;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import java.util.UUID;

@Service
public class CashService {

    private final IdempotencyRepository idempotencyRepository;
    private final CashOutboxRepository outboxRepository;
    private final WebClient internalServicesWebClient;
    private final ObjectMapper objectMapper;
    private final String accountsServiceUrl;

    public CashService(IdempotencyRepository idempotencyRepository,
                       CashOutboxRepository outboxRepository,
                       WebClient internalServicesWebClient,
                       ObjectMapper objectMapper,
                       @Value("${app.services.accounts-url}") String accountsServiceUrl) {
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.internalServicesWebClient = internalServicesWebClient;
        this.objectMapper = objectMapper;
        this.accountsServiceUrl = accountsServiceUrl;
    }

    public void executeCash(UUID idempotencyKey, CashRequest request) {

        var keyOpt = idempotencyRepository.findById(idempotencyKey);
        if (keyOpt.isPresent()) {
            String status = keyOpt.get().getStatus();
            if ("SUCCESS".equals(status)) return;
            if ("STARTED".equals(status)) {
                throw new IllegalStateException("Операция уже выполняется в соседнем потоке");
            }
            if ("REJECTED".equals(status)) {
                throw new IllegalArgumentException("Эта операция уже была отклонена банком");
            }
        }

        saveKeyStatus(idempotencyKey, "STARTED");

        try {
            AccountOperationDto operationDto = new AccountOperationDto(
                    request.username(), request.amount(), request.action()
            );

            internalServicesWebClient.post()
                    .uri(accountsServiceUrl + "/accounts/execute-cash")
                    .bodyValue(operationDto)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            saveSuccessStateAndOutbox(idempotencyKey, request);

        } catch (WebClientResponseException.BadRequest e) {
            saveKeyStatus(idempotencyKey, "REJECTED");
            throw new IllegalArgumentException("Ошибка операции: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            saveKeyStatus(idempotencyKey, "REJECTED");
            throw new RuntimeException("Системный сбой при обналичивании: " + e.getMessage());
        }
    }

    @Transactional
    public void saveKeyStatus(UUID id, String status) {
        IdempotencyKey key = idempotencyRepository.findById(id).orElse(new IdempotencyKey());
        key.setId(id);
        key.setStatus(status);
        idempotencyRepository.save(key);
    }

    @Transactional
    public void saveSuccessStateAndOutbox(UUID id, CashRequest request) throws Exception {
        saveKeyStatus(id, "SUCCESS");

        CashOutboxMessage outboxMessage = new CashOutboxMessage();
        outboxMessage.setId(UUID.randomUUID());
        outboxMessage.setEventType("CASH_OPERATION_COMPLETED");
        outboxMessage.setPayload(objectMapper.writeValueAsString(request));
        outboxMessage.setStatus("PENDING");
        outboxMessage.setAttempts(0);
        outboxRepository.save(outboxMessage);
    }
}

