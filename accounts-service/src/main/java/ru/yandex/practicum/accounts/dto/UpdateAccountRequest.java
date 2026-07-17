package ru.yandex.practicum.accounts.dto;

import java.time.LocalDate;

public record UpdateAccountRequest(String name, LocalDate birthdate) {}

