package ru.yandex.practicum.cash.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;

import java.util.List;
import java.util.UUID;

@Repository
public interface CashOutboxRepository extends JpaRepository<CashOutboxMessage, UUID> {
    List<CashOutboxMessage> findByStatus(String status);
}
