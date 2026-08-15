package ru.yandex.practicum.accounts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.accounts.repository.OutboxRepository;

import java.util.UUID;

@Service
public class OutboxStatusService {

    private final OutboxRepository repository;

    public OutboxStatusService(OutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updateStatus(UUID id, String status, int attempts) {
        repository.findById(id).ifPresent(message -> {
            message.setStatus(status);
            message.setAttempts(attempts);
        });
    }
}
