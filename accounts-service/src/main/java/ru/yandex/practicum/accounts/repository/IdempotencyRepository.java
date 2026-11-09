package ru.yandex.practicum.accounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.accounts.entity.IdempotencyKey;

import java.util.UUID;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO account_idempotency
                (id, status, operation_type, request_username,
                 request_amount, request_action)
            VALUES
                (:id, 'PROCESSING', 'CASH', :username,
                 :amount, :action)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int tryStartCash(
            @Param("id") UUID id,
            @Param("username") String username,
            @Param("amount") int amount,
            @Param("action") String action
    );

    @Modifying
    @Query(value = """
            UPDATE account_idempotency
            SET status = 'SUCCESS'
            WHERE id = :id
              AND status = 'PROCESSING'
              AND operation_type = 'CASH'
            """, nativeQuery = true)
    int markCashSuccess(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO account_idempotency
                (id, status, operation_type, request_sender,
                 request_recipient, request_amount)
            VALUES
                (:id, 'PROCESSING', 'TRANSFER', :sender, :recipient,
                 :amount)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int tryStartTransfer(
            @Param("id") UUID id,
            @Param("sender") String sender,
            @Param("recipient") String recipient,
            @Param("amount") int amount
    );

    @Modifying
    @Query(value = """
            UPDATE account_idempotency
            SET status = 'SUCCESS'
            WHERE id = :id
              AND status = 'PROCESSING'
              AND operation_type = 'TRANSFER'
            """, nativeQuery = true)
    int markTransferSuccess(@Param("id") UUID id);
}
