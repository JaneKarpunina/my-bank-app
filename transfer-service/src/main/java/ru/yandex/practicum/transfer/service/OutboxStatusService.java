package ru.yandex.practicum.transfer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;

import java.util.UUID;

@Service
public class OutboxStatusService {

    private final TransferOutboxRepository repository;

    public OutboxStatusService(TransferOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updateStatus(UUID id, String status) {
        repository.findById(id).ifPresent(message -> {
            message.setStatus(status);
        });
    }
}

