package ru.yandex.practicum.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.transfer.entity.IdempotencyKey;

import java.util.UUID;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = """ 
            INSERT INTO idempotency_keys (id, status)
            VALUES (:id, 'STARTED')
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int tryStartTransfer(@Param("id") UUID id);
}
