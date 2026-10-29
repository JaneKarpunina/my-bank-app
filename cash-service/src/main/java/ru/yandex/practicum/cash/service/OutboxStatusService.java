package ru.yandex.practicum.cash.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;

import java.util.UUID;

@Slf4j
@Service
public class OutboxStatusService {

    private final CashOutboxRepository repository;

    public OutboxStatusService(CashOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updateStatus(UUID id, String status) {

        log.debug("Запрос на обновление статуса Cash Outbox сообщения. ID: {}, Целевой статус: {}", id, status);
        repository.findById(id).ifPresentOrElse(
                message -> {
                    message.setStatus(status);
                    log.debug("Статус Cash Outbox сообщения {} успешно переведен в '{}' в контексте транзакции.", id, status);
                },
                () -> {
                    log.warn("Внимание! Не удалось обновить статус Cash Outbox сообщения." +
                            " Запись с ID {} не найдена в базе данных!", id);
                }
        );
    }
}
