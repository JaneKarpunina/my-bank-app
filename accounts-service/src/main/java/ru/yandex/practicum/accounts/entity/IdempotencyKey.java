package ru.yandex.practicum.accounts.entity;

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
    private String status;
}
