package ru.yandex.practicum.cash.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;

import java.util.UUID;

@Service
public class OutboxStatusService {

    private final CashOutboxRepository repository;

    public OutboxStatusService(CashOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updateStatus(UUID id, String status) {
        repository.findById(id).ifPresent(message -> {
            message.setStatus(status);
        });
    }
}
