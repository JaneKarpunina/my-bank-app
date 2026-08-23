package ru.yandex.practicum.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferOutboxRepository extends JpaRepository<TransferOutboxMessage, UUID> {
    List<TransferOutboxMessage> findByStatus(String pending);
}
