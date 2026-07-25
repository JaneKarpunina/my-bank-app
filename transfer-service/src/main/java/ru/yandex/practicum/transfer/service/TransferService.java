package ru.yandex.practicum.transfer.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import ru.yandex.practicum.transfer.entity.IdempotencyKey;
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;
import ru.yandex.practicum.transfer.repository.IdempotencyRepository;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;
import ru.yandex.practicum.transfer.dto.AccountTransferDto;
import ru.yandex.practicum.transfer.dto.NotificationEventDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.UUID;

@Service
public class TransferService {

    private final IdempotencyRepository idempotencyRepository;
    private final TransferOutboxRepository outboxRepository;
    private final WebClient internalServicesWebClient;
    private final ObjectMapper objectMapper;
    private final String accountsServiceUrl;

    public TransferService(IdempotencyRepository idempotencyRepository,
                           TransferOutboxRepository outboxRepository,
                           WebClient internalServicesWebClient,
                           ObjectMapper objectMapper,
                           @Value("${app.services.accounts-url}") String accountsServiceUrl) {
        this.idempotencyRepository = idempotencyRepository;
        this.outboxRepository = outboxRepository;
        this.internalServicesWebClient = internalServicesWebClient;
        this.objectMapper = objectMapper;
        this.accountsServiceUrl = accountsServiceUrl;
    }


    public void executeTransfer(UUID idempotencyKey, String sender, String recipient, int amount) {

        String currentStatus = checkAndStartIdempotencyKey(idempotencyKey);
        if ("SUCCESS".equals(currentStatus)) return;
        if ("REJECTED".equals(currentStatus)) {
            throw new IllegalArgumentException("Этот перевод уже был отклонен банком");
        }

        try {
            internalServicesWebClient.post()
                    .uri(accountsServiceUrl + "/accounts/execute-transfer")
                    .bodyValue(new AccountTransferDto(sender, recipient, amount))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            saveSuccessStateAndOutbox(idempotencyKey, recipient, sender, amount);

        } catch (WebClientResponseException.BadRequest e) {
            updateKeyStatus(idempotencyKey, "REJECTED");
            throw new IllegalArgumentException("Ошибка перевода: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            updateKeyStatus(idempotencyKey, "REJECTED");
            throw new RuntimeException("Системный сбой при переводе: " + e.getMessage());
        }
    }


    @Transactional
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

    @Transactional
    public void updateKeyStatus(UUID id, String status) {
        idempotencyRepository.findById(id).ifPresent(key -> {
            key.setStatus(status);
            idempotencyRepository.save(key);
        });
    }

    @Transactional
    public void saveSuccessStateAndOutbox(UUID id, String recipient, String sender, int amount) {
        try {
            idempotencyRepository.findById(id).ifPresent(key -> {
                key.setStatus("SUCCESS");
                idempotencyRepository.save(key);
            });

            NotificationEventDto eventDto = new NotificationEventDto(recipient,
                    String.format("Вам поступил перевод на сумму %d руб. от пользователя %s", amount, sender));

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

