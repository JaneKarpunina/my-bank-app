package ru.yandex.practicum.mybankfront.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.yandex.practicum.mybankfront.controller.dto.AccountDto;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class AccountResponse {

    private String name;
    private LocalDate birthdate;
    private int sum;

    private List<AccountDto> accounts;
}
