package ru.yandex.practicum.accounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.accounts.entity.IdempotencyKey;

import java.util.UUID;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, UUID> {
}
