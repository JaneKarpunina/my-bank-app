package ru.yandex.practicum.cash.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.yandex.practicum.cash.client.AccountsClient;
import ru.yandex.practicum.cash.dto.AccountOperationDto;
import ru.yandex.practicum.cash.dto.CashRequest;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.entity.IdempotencyKey;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.repository.IdempotencyRepository;

import java.util.UUID;

@Service
public class CashService {

    private final IdempotencyRepository idempotencyRepository;
    private final CashOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AccountsClient accountsClient;

    public CashService(IdempotencyRepository idempotencyRepository,
                       CashOutboxRepository outboxRepository,
                       ObjectMapper objectMapper,
                       TransactionTemplate transactionTemplate,
                       AccountsClient accountsClient) {
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.accountsClient = accountsClient;
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

        transactionTemplate.executeWithoutResult(tx ->
                saveKeyStatus(idempotencyKey, "STARTED"));

        try {
            AccountOperationDto operationDto = new AccountOperationDto(
                    request.username(), request.amount(), request.action()
            );

            accountsClient.executeCash(idempotencyKey, operationDto);

            transactionTemplate.executeWithoutResult(tx -> {
                try {
                    saveSuccessStateAndOutbox(idempotencyKey, request);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Ошибка записи Outbox: " + e.getMessage());
                }
            });

        } catch (WebClientResponseException.BadRequest e) {
            transactionTemplate.executeWithoutResult(tx ->
                    saveKeyStatus(idempotencyKey, "REJECTED"));
            throw new IllegalArgumentException("Ошибка операции: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(tx ->
                    saveKeyStatus(idempotencyKey, "STARTED"));
            throw new RuntimeException("Системный сбой при обналичивании: " + e.getMessage());
        }
    }

    public void saveKeyStatus(UUID id, String status) {
        IdempotencyKey key = idempotencyRepository.findById(id).orElse(new IdempotencyKey());
        key.setId(id);
        key.setStatus(status);
        idempotencyRepository.save(key);
    }

    public void saveSuccessStateAndOutbox(UUID id, CashRequest request) throws JsonProcessingException {
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

