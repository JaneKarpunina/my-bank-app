package ru.yandex.practicum.mybankfront.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.yandex.practicum.mybankfront.controller.dto.CashAction;
import ru.yandex.practicum.mybankfront.controller.stub.AccountStub;
import ru.yandex.practicum.mybankfront.dto.AccountResponse;
import ru.yandex.practicum.mybankfront.dto.TransferRequest;
import ru.yandex.practicum.mybankfront.dto.UpdateAccountRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Контроллер main.html.
 * <p>
 * Используемая модель для main.html:
 * model.addAttribute("name", name);
 * model.addAttribute("birthdate", birthdate.format(DateTimeFormatter.ISO_DATE));
 * model.addAttribute("sum", sum);
 * model.addAttribute("accounts", accounts);
 * model.addAttribute("errors", errors);
 * model.addAttribute("info", info);
 * <p>
 * Поля модели:
 * name - Фамилия Имя текущего пользователя, String (обязательное)
 * birthdate - дата рождения текущего пользователя, String в формате 'YYYY-MM-DD' (обязательное)
 * sum - сумма на счету текущего пользователя, Integer (обязательное)
 * accounts - список аккаунтов, которым можно перевести деньги, List<AccountDto> (обязательное)
 * errors - список ошибок после выполнения действий, List<String> (не обязательное)
 * info - строка успешности после выполнения действия, String (не обязательное)
 * <p>
 * С примерами использования можно ознакомиться в тестовом классе заглушке AccountStub
 */
@Controller
public class MainController {
    // TODO: Удалить заглушку, так как используется только для ознакомительных целей
    @Autowired
    private AccountStub accountStub;

    private final WebClient webClient;

    public MainController(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * GET /.
     * Редирект на GET /account
     */
    @GetMapping
    public String index() {
        return "redirect:/account";
    }

    /**
     * GET /account.
     * Что нужно сделать:
     * 1. Сходить в сервис accounts через Gateway API для получения данных аккаунта по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     */
//    @GetMapping("/account")
//    public String getAccount(Model model) {
//        // TODO: Заменить на то, что описано в комментарии к методу
//        accountStub.fillModel(model, null, null);
//
//        return "main";
//    }

    @GetMapping("/account")
    public String getAccount(
            Model model,
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient
    ) {
        String currentUsername = principal.getAttribute("preferred_username");
        String userJwtToken = userClient.getAccessToken().getTokenValue();

        UUID idempotencyKey = UUID.randomUUID();
        model.addAttribute("idempotencyKey", idempotencyKey);

        try {
            AccountResponse accountData = webClient.get()
                    .uri("/accounts")
                    .header("Authorization", "Bearer " + userJwtToken)
                    .retrieve()
                    .bodyToMono(AccountResponse.class)
                    .block();

            if (accountData != null) {
                model.addAttribute("name", accountData.getName());
                model.addAttribute("birthdate", accountData.getBirthdate().format(DateTimeFormatter.ISO_DATE));
                model.addAttribute("sum", accountData.getSum());
                model.addAttribute("accounts", accountData.getAccounts());
            }

        } catch (Exception e) {

            List<String> existingErrors = (List<String>) model.getAttribute("errors");
            List<String> finalErrors = new ArrayList<>();

            if (existingErrors != null) {
                finalErrors.addAll(existingErrors);
            }

            finalErrors.add("Не удалось загрузить актуальный баланс. Сервер временно недоступен.");

            model.addAttribute("errors", finalErrors);

            model.addAttribute("name", currentUsername);
            model.addAttribute("birthdate", "");
            model.addAttribute("sum", 0);
            model.addAttribute("accounts", Collections.emptyList());
        }

        return "main";
    }

    /**
     * POST /account.
     * Что нужно сделать:
     * 1. Сходить в сервис accounts через Gateway API для изменения данных текущего пользователя по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     * <p>
     * Изменяемые данные:
     * 1. name - Фамилия Имя
     * 2. birthdate - дата рождения в формате YYYY-DD-MM
     */
//    @PostMapping("/account")
//    public String editAccount(
//            Model model,
//            @RequestParam("name") String name,
//            @RequestParam("birthdate") LocalDate birthdate
//    ) {
//        // TODO: Заменить на то, что описано в комментарии к методу
//        accountStub.setNameAndBirthdate(name, birthdate);
//        accountStub.fillModel(model, null, null);
//
//        return "main";
//    }
//    @PostMapping("/account")
//    public String editAccount(
//            Model model,
//            @RequestParam("name") String name,
//            @RequestParam("birthdate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
//            LocalDate birthdate
//    ) {
//
//        UpdateAccountRequest updateRequest = new UpdateAccountRequest(name, birthdate);
//
//        AccountResponse updatedAccountData = webClient.post()
//                .uri("/accounts")
//                .bodyValue(updateRequest)
//                .retrieve()
//                .bodyToMono(AccountResponse.class)
//                .block();
//
//        if (updatedAccountData != null) {
//            model.addAttribute("name", updatedAccountData.getName());
//            model.addAttribute("birthdate", updatedAccountData.getBirthdate().format(DateTimeFormatter.ISO_DATE));
//            model.addAttribute("sum", updatedAccountData.getSum());
//            model.addAttribute("accounts", updatedAccountData.getAccounts());
//
//        }
//
//        return "main";
//    }

    @PostMapping("/account")
    public String editAccount(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient,
            @RequestParam("name") String name,
            @RequestParam("birthdate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            RedirectAttributes redirectAttributes
    ) {
        String currentUsername = principal.getAttribute("preferred_username");
        String userJwtToken = userClient.getAccessToken().getTokenValue();
        List<String> errorList = new ArrayList<>();

        try {
            UpdateAccountRequest updateRequest = new UpdateAccountRequest(name, birthdate);

            webClient.post()
                    .uri("/accounts")
                    .header("Authorization", "Bearer " + userJwtToken)
                    .bodyValue(updateRequest)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (WebClientResponseException e) {
            errorList.add("Не удалось сохранить изменения: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            errorList.add("Системная ошибка при сохранении профиля: " + e.getMessage());
        }

        if (!errorList.isEmpty()) {
            redirectAttributes.addFlashAttribute("errors", errorList);
        }

        return "redirect:/account";

        }

    /**
     * POST /cash.
     * Что нужно сделать:
     * 1. Сходить в сервис cash через Gateway API для снятия/пополнения счета текущего аккаунта по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     * <p>
     * Параметры:
     * 1. value - сумма списания
     * 2. action - GET (снять), PUT (пополнить)
     */
    @PostMapping("/cash")
    public String editCash(
            Model model,
            @RequestParam("value") int value,
            @RequestParam("action") CashAction action
    ) {
        // TODO: Заменить на то, что описано в комментарии к методу
        accountStub.editCash(model, value, action);

        return "main";
    }

    /**
     * POST /transfer.
     * Что нужно сделать:
     * 1. Сходить в сервис accounts через Gateway API для перевода со счета текущего аккаунта на счет другого аккаунта по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     * <p>
     * Параметры:
     * 1. value - сумма списания
     * 2. login - логин пользователя получателя
     */
//    @PostMapping("/transfer")
//    public String transfer(
//            Model model,
//            @AuthenticationPrincipal OAuth2User principal,
//            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient,
//            @RequestParam("value") int value,
//            @RequestParam("login") String loginRecipient,
//            @RequestParam("idempotencyKey") UUID idempotencyKey
//    ) {
//        String currentUsername = principal.getAttribute("preferred_username");
//        List<String> errorList = new ArrayList<>();
//
//        TransferRequest transferBody = new TransferRequest(currentUsername, loginRecipient, value);
//
//        String userJwtToken = userClient.getAccessToken().getTokenValue();
//
//        try {
//            webClient.post()
//                    .uri("/transfers")
//                    .header("Authorization", "Bearer " + userJwtToken)
//                    .header("X-Idempotency-Key", idempotencyKey.toString())
//                    .bodyValue(transferBody)
//                    .retrieve()
//                    .toBodilessEntity()
//                    .block();
//
//        } catch (WebClientResponseException e) {
//            errorList.add(e.getResponseBodyAsString());
//        } catch (Exception e) {
//            errorList.add("Системная ошибка: " + e.getMessage());
//        }
//
//        model.addAttribute("idempotencyKey", UUID.randomUUID());
//
//        try {
//            AccountResponse accountData = webClient.get()
//                    .uri("/accounts")
//                    .header("Authorization", "Bearer " + userJwtToken)
//                    .retrieve()
//                    .bodyToMono(AccountResponse.class)
//                    .block();
//
//            if (accountData != null) {
//                model.addAttribute("name", accountData.getName());
//                model.addAttribute("birthdate", accountData.getBirthdate());
//                model.addAttribute("sum", accountData.getSum());
//                model.addAttribute("accounts", accountData.getAccounts());
//            }
//        } catch (Exception e) {
//            errorList.add("Не удалось мгновенно обновить баланс. Пожалуйста, обновите страницу.");
//
//            model.addAttribute("name", currentUsername);
//            model.addAttribute("birthdate", null);
//            model.addAttribute("sum", 0);
//            model.addAttribute("accounts", Collections.emptyList());
//        }
//
//        if (!errorList.isEmpty()) {
//            model.addAttribute("errors", errorList);
//        }
//
//        return "main";
//    }
    @PostMapping("/transfer")
    public String transfer(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient,
            @RequestParam("value") int value,
            @RequestParam("login") String loginRecipient,
            @RequestParam("idempotencyKey") UUID idempotencyKey,
            RedirectAttributes redirectAttributes
    ) {
        String currentUsername = principal.getAttribute("preferred_username");
        String userJwtToken = userClient.getAccessToken().getTokenValue();

        try {
            webClient.post()
                    .uri("/transfers")
                    .header("Authorization", "Bearer " + userJwtToken)
                    .header("X-Idempotency-Key", idempotencyKey.toString())
                    .bodyValue(new TransferRequest(currentUsername, loginRecipient, value))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (WebClientResponseException e) {
            redirectAttributes.addFlashAttribute("errors", List.of(e.getResponseBodyAsString()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errors", List.of("Системная ошибка: " + e.getMessage()));
        }
        return "redirect:/account";
    }

}
