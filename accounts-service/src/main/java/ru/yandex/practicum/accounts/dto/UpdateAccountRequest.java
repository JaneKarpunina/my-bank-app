package ru.yandex.practicum.accounts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record UpdateAccountRequest(String name,
                                   @JsonFormat(shape = JsonFormat.Shape.STRING,
                                           pattern = "yyyy-MM-dd")
                                   LocalDate birthdate) {}

