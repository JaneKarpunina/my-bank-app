package ru.yandex.practicum.transfer.dto;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        String payload,
        Instant timestamp
) {}
