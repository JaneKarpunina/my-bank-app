package ru.yandex.practicum.mybankfront.dto;

import java.time.LocalDate;

public record UpdateAccountRequest(String name, LocalDate birthdate) {}
