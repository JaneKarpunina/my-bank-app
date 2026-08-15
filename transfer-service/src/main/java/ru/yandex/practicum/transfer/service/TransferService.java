package ru.yandex.practicum.transfer.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.yandex.practicum.transfer.client.AccountsClient;
import ru.yandex.practicum.transfer.dto.AccountTransferDto;
import ru.yandex.practicum.transfer.dto.NotificationEventDto;
import ru.yandex.practicum.transfer.entity.IdempotencyKey;
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;
import ru.yandex.practicum.transfer.repository.IdempotencyRepository;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;

import java.util.UUID;

@Service
public class TransferService {

    private final IdempotencyRepository idempotencyRepository;
    private final TransferOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AccountsClient accountsClient;

    public TransferService(IdempotencyRepository idempotencyRepository,
                           TransferOutboxRepository outboxRepository,
                           ObjectMapper objectMapper,
                           TransactionTemplate transactionTemplate, AccountsClient accountsClient) {
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.accountsClient = accountsClient;
    }


    public void executeTransfer(UUID idempotencyKey, String sender, String recipient, int amount) {

        String currentStatus = transactionTemplate.execute(
                tx -> checkAndStartIdempotencyKey(idempotencyKey)
        );
        if ("SUCCESS".equals(currentStatus)) return;
        if ("REJECTED".equals(currentStatus)) {
            throw new IllegalArgumentException("Этот перевод уже был отклонен банком");
        }

        try {

            AccountTransferDto accountTransferDto = new AccountTransferDto(sender, recipient, amount);

            accountsClient.executeTransfer(idempotencyKey, accountTransferDto);

            transactionTemplate.executeWithoutResult(tx ->
                    saveSuccessStateAndOutbox(idempotencyKey, recipient, sender, amount)
            );

        } catch (WebClientResponseException.BadRequest e) {
            transactionTemplate.executeWithoutResult(tx ->
                    updateKeyStatus(idempotencyKey, "REJECTED"));
            throw new IllegalArgumentException("Ошибка перевода: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(tx ->
                    updateKeyStatus(idempotencyKey, "STARTED"));
            throw new RuntimeException("Системный сбой при переводе: " + e.getMessage());
        }
    }


    public String checkAndStartIdempotencyKey(UUID id) {
        var keyOpt = idempotencyRepository.findById(id);
        if (keyOpt.isPresent()) {
            if ("STARTED".equals(keyOpt.get().getStatus())) {
                throw new IllegalStateException("Транзакция уже обрабатывается");
            }
            return keyOpt.get().getStatus();
        }

        IdempotencyKey newKey = new IdempotencyKey();
        newKey.setId(id);
        newKey.setStatus("STARTED");
        idempotencyRepository.save(newKey);
        return "STARTED";
    }

    public void updateKeyStatus(UUID id, String status) {
        idempotencyRepository.findById(id).ifPresent(key -> {
            key.setStatus(status);
            idempotencyRepository.save(key);
        });
    }

    public void saveSuccessStateAndOutbox(UUID id, String recipient, String sender, int amount) {
        try {
            idempotencyRepository.findById(id).ifPresent(key -> {
                key.setStatus("SUCCESS");
                idempotencyRepository.save(key);
            });

            NotificationEventDto eventDto = new NotificationEventDto(recipient,
                    amount, sender);

            TransferOutboxMessage outboxMessage = new TransferOutboxMessage();
            outboxMessage.setId(UUID.randomUUID());
            outboxMessage.setEventType("TRANSFER_COMPLETED");
            outboxMessage.setPayload(objectMapper.writeValueAsString(eventDto));
            outboxMessage.setStatus("PENDING");
            outboxMessage.setAttempts(0);
            outboxRepository.save(outboxMessage);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка записи Outbox: " + e.getMessage());
        }
    }
}

