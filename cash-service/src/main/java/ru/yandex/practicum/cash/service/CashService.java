package ru.yandex.practicum.cash.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

        String username = request.username();
        String action = request.action() != null ? request.action().toString() : "UNKNOWN";

        var keyOpt = idempotencyRepository.findById(idempotencyKey);
        boolean isRecoveryAttempt = false;

        if (keyOpt.isPresent()) {
            String status = keyOpt.get().getStatus();
            if ("SUCCESS".equals(status)) {
                log.debug("Идемпотентный пропуск: операция с ключом {} уже успешно завершена ранее.", idempotencyKey);
                return;
            }
            if ("STARTED".equals(status)) {
                log.warn("Блокировка транзакции {}: операция уже выполняется в параллельном потоке.", idempotencyKey);
                throw new IllegalStateException("Операция уже выполняется в соседнем потоке");
            }
            if ("REJECTED".equals(status)) {
                log.warn("Отклонено по идемпотентности: попытка повторного вызова " +
                        "ранее отклонённой банком операции {}.", idempotencyKey);
                throw new IllegalArgumentException("Эта операция уже была отклонена банком");
            }

            if ("SYSTEM_ERROR".equals(status)) {
                log.info("Обнаружен статус SYSTEM_ERROR для ключа {}. Запуск восстановления...", idempotencyKey);
                isRecoveryAttempt = true;
            }
        }

        if (!isRecoveryAttempt) {
            log.debug("Регистрация ключа идемпотентности {} со статусом STARTED для пользователя {}.",
                    idempotencyKey, username);

            Integer inserted = transactionTemplate.execute(tx ->
                    idempotencyRepository.tryStartCash(idempotencyKey)
            );

            if (inserted == null || inserted == 0) {
                log.warn("Блокировка транзакции {}: обнаружен конкурентный запрос на вставку.", idempotencyKey);
                throw new IllegalStateException("Операция уже выполняется в соседнем потоке");
            }
        }

        try {
            AccountOperationDto operationDto = new AccountOperationDto(
                    request.username(), request.amount(), request.action()
            );

            log.debug("Вызов внешнего AccountsClient для выполнения списания/начисления" +
                    " по счету пользователя {}.", username);
            accountsClient.executeCash(idempotencyKey, operationDto);

            transactionTemplate.executeWithoutResult(tx -> {
                try {
                    saveSuccessStateAndOutbox(idempotencyKey, request);
                } catch (JsonProcessingException e) {
                    log.error("Сбой сериализации данных при записи в Outbox " +
                            "для пользователя {}: {}", username, e.getMessage());
                    throw new RuntimeException("Ошибка записи Outbox: " + e.getMessage());
                }
            });

            log.info("Кассовая операция [{}] успешно завершена для пользователя {}. Сумма: {}." +
                            " Событие добавлено в Outbox-очередь.",
                    action, username, request.amount());

        } catch (WebClientResponseException.BadRequest e) {
            String errorBody = e.getResponseBodyAsString();
            log.warn("Бизнес-отказ от Accounts-service при кассовой операции [{}] для пользователя {}." +
                            " Причина: {}. Ключ {} переведен в REJECTED.",
                    action, username, errorBody, idempotencyKey);

            transactionTemplate.executeWithoutResult(tx ->
                    saveKeyStatus(idempotencyKey, "REJECTED"));
            throw new IllegalArgumentException("Ошибка операции: " + e.getResponseBodyAsString());
        } catch (Exception e) {

            log.error("Критический системный сбой при вызове AccountsClient для пользователя {}. " +
                            "Ключ {} сохранен как SYSTEM_ERROR. Ошибка: {}",
                    username, idempotencyKey, e.getMessage(), e);

            transactionTemplate.executeWithoutResult(tx ->
                    saveKeyStatus(idempotencyKey, "SYSTEM_ERROR"));
            throw new RuntimeException("Системный сбой при обналичивании: " + e.getMessage());
        }
    }

    public void saveKeyStatus(UUID id, String status) {
        IdempotencyKey key = idempotencyRepository.findById(id).orElse(new IdempotencyKey());
        key.setId(id);
        key.setStatus(status);
        idempotencyRepository.save(key);

        idempotencyRepository.tryStartCash(id);

        log.debug("Статус ключа идемпотентности {} обновлен в БД на: {}", id, status);
    }

    public void saveSuccessStateAndOutbox(UUID id, CashRequest request) throws JsonProcessingException {
        saveKeyStatus(id, "SUCCESS");

        if (outboxRepository.existsById(id)) {
            log.info("Идемпотентный пропуск Outbox: сообщение для транзакции {} уже было сгенерировано ранее.", id);
            return;
        }

        CashOutboxMessage outboxMessage = new CashOutboxMessage();
        outboxMessage.setId(id);
        outboxMessage.setEventType("CASH_OPERATION_COMPLETED");
        outboxMessage.setPayload(objectMapper.writeValueAsString(request));
        outboxMessage.setStatus("PENDING");
        outboxRepository.save(outboxMessage);

        log.debug("Создано Outbox-сообщение CASH_OPERATION_COMPLETED со статусом PENDING." +
                " ID сообщения: {}", outboxMessage.getId());
    }
}

