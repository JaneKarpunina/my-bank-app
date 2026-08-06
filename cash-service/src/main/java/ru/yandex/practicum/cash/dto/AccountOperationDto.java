package ru.yandex.practicum.cash.dto;

public record AccountOperationDto(
        String username,
        int amount,
        CashAction action
) {}
