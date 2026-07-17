package ru.yandex.practicum.accounts.dto;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,     // Например: "CLIENT_INFO_UPDATED", "TRANSFER_COMPLETED"
        String aggregateType, // Например: "Account", "Transfer"
        String payload,       // Сами данные в виде JSON-строки: "{\"amount\": 500, ...}"
        Instant timestamp
) {}
