package ru.yandex.practicum.transfer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;

import java.util.UUID;

@Slf4j
@Service
public class OutboxStatusService {

    private final TransferOutboxRepository repository;

    public OutboxStatusService(TransferOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updateStatus(UUID id, String status) {
        log.debug("Запрос на обновление статуса Transfer Outbox сообщения. ID: {}, Целевой статус: {}", id, status);

        repository.findById(id).ifPresentOrElse(
                message -> {
                    message.setStatus(status);
                    log.debug("Статус Transfer Outbox сообщения {} успешно переведен в " +
                            "'{}' в контексте транзакции.", id, status);
                },
                () -> {
                    log.warn("Внимание! Не удалось обновить статус Transfer Outbox сообщения." +
                            " Запись с ID {} не найдена в базе данных!", id);
                }
        );
    }
}

