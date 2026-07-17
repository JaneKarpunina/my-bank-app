package ru.yandex.practicum.accounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.accounts.entity.OutboxMessage;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(String status);

    List<OutboxMessage> findByStatus(String status);
}

