package ru.yandex.practicum.cash.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.cash.entity.IdempotencyKey;

import java.util.UUID;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = """ 
            INSERT INTO cash_idempotency (id, status)
            VALUES (:id, 'STARTED')
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int tryStartCash(@Param("id") UUID id);
}
