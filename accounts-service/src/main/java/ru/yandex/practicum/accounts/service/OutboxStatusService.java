package ru.yandex.practicum.accounts.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.accounts.repository.OutboxRepository;

import java.util.UUID;

@Slf4j
@Service
public class OutboxStatusService {

    private final OutboxRepository repository;

    public OutboxStatusService(OutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updateStatus(UUID id, String status) {

        log.debug("Запрос на обновление статуса Outbox-сообщения. ID: {}, Целевой статус: {}", id, status);

        repository.findById(id).ifPresentOrElse(
                message -> {
                    message.setStatus(status);
                    log.debug("Статус Outbox-сообщения {} успешно переведен в '{}' в контексте транзакции", id, status);
                },
                () -> {
                    log.warn("Внимание! Не удалось обновить статус Outbox-сообщения." +
                            " Запись с ID {} не найдена в базе данных!", id);
                }
        );
    }
}
