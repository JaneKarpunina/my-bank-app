package ru.yandex.practicum.accounts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "account_idempotency")
@Getter
@Setter
public class IdempotencyKey {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String status;

    @Column(name = "operation_type")
    private String operationType;

    @Column(name = "request_username")
    private String requestUsername;

    @Column(name = "request_amount")
    private Integer requestAmount;

    @Column(name = "request_action")
    private String requestAction;

    @Column(name = "request_sender")
    private String requestSender;

    @Column(name = "request_recipient")
    private String requestRecipient;
}
