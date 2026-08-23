package ru.yandex.practicum.accounts.dto;


public record AccountOperationDto(
        String username,
        int amount,
        CashAction action
) {}

