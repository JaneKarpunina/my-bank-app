package ru.yandex.practicum.accounts.dto;

import java.time.LocalDate;

public record ClientInfoUpdatedEventDto(
        String username,
        String name,
        LocalDate birthDate
) {}
