package ru.yandex.practicum.transfer.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
        if ("SUCCESS".equals(currentStatus)) {
            log.debug("Идемпотентный пропуск: перевод с ключом {} уже успешно завершен ранее.", idempotencyKey);
            return;
        }
        if ("STARTED".equals(currentStatus)) {
            log.warn("Блокировка транзакции {}: операция уже выполняется в параллельном потоке.", idempotencyKey);
            throw new IllegalStateException("Операция уже выполняется в соседнем потоке");
        }
        if ("REJECTED".equals(currentStatus)) {
            log.warn("Отклонено по идемпотентности: перевод с ключом {}" +
                    " уже был ранее отклонён бэкендом.", idempotencyKey);
            throw new IllegalArgumentException("Этот перевод уже был отклонен банком");
        }

        try {

            AccountTransferDto accountTransferDto = new AccountTransferDto(sender, recipient, amount);
            log.debug("Вызов внешнего AccountsClient для выполнения списания " +
                    "и начисления средств (Ключ: {}).", idempotencyKey);
            accountsClient.executeTransfer(idempotencyKey, accountTransferDto);

            transactionTemplate.executeWithoutResult(tx ->
                    saveSuccessStateAndOutbox(idempotencyKey, recipient, sender, amount)
            );

            log.info("Транзакция перевода успешно завершена! {} перевел {} рублей" +
                            " пользователю {}. Ключ идемпотентности: {}",
                    sender, amount, recipient, idempotencyKey);

        } catch (WebClientResponseException.BadRequest e) {
            String errorBody = e.getResponseBodyAsString();
            log.warn("Бизнес-отказ от Accounts-service для перевода {} -> {}. Ключ {} переведен в REJECTED. Ошибка: {}",
                    sender, recipient, idempotencyKey, errorBody);
            transactionTemplate.executeWithoutResult(tx ->
                    updateKeyStatus(idempotencyKey, "REJECTED"));
            throw new IllegalArgumentException("Ошибка перевода: " + errorBody);
        } catch (Exception e) {

            log.error("Критический системный сбой при выполнении перевода {} -> {}." +
                            " Ключ {} сохранен как STARTED. Ошибка: {}",
                    sender, recipient, idempotencyKey, e.getMessage(), e);
            transactionTemplate.executeWithoutResult(tx ->
                    updateKeyStatus(idempotencyKey, "STARTED"));
            throw new RuntimeException("Системный сбой при переводе: " + e.getMessage());
        }
    }


    public String checkAndStartIdempotencyKey(UUID id) {
        var keyOpt = idempotencyRepository.findById(id);
        if (keyOpt.isPresent()) {
            if ("STARTED".equals(keyOpt.get().getStatus())) {
                log.warn("Конкурентная блокировка: попытка повторного вызова транзакции {}," +
                        " пока она выполняется в соседнем потоке.", id);
                throw new IllegalStateException("Транзакция уже обрабатывается");
            }
            return keyOpt.get().getStatus();
        }

        IdempotencyKey newKey = new IdempotencyKey();
        newKey.setId(id);
        newKey.setStatus("STARTED");
        idempotencyRepository.save(newKey);

        log.debug("Ключ идемпотентности {} не найден в БД. Создана новая запись со статусом STARTED.", id);
        return "STARTED";
    }

    public void updateKeyStatus(UUID id, String status) {
        idempotencyRepository.findById(id).ifPresentOrElse(
                key -> {
                    key.setStatus(status);
                    idempotencyRepository.save(key);
                    log.debug("Статус ключа идемпотентности {} изменен на: {}", id, status);
                },
                () -> log.warn("Внимание! Попытка обновить статус до '{}' " +
                        "для несуществующего ключа идемпотентности {}!", status, id)
        );
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
            outboxRepository.save(outboxMessage);

            log.debug("Транзакция перевода {} зафиксирована как SUCCESS. " +
                    "Сгенерировано Outbox-сообщение TRANSFER_COMPLETED ID: {}", id, outboxMessage.getId());
        } catch (Exception e) {
            log.error("Сбой сохранения состояния SUCCESS или записи Outbox для перевода {}: {}", id, e.getMessage());
            throw new RuntimeException("Ошибка записи Outbox: " + e.getMessage());
        }
    }
}

