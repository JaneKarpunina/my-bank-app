package ru.yandex.practicum.mybankfront.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.yandex.practicum.mybankfront.client.GatewayClient;
import ru.yandex.practicum.mybankfront.controller.dto.CashAction;
import ru.yandex.practicum.mybankfront.dto.AccountResponse;
import ru.yandex.practicum.mybankfront.dto.CashRequest;
import ru.yandex.practicum.mybankfront.dto.TransferRequest;
import ru.yandex.practicum.mybankfront.dto.UpdateAccountRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
public class MainController {


    private final GatewayClient gatewayClient;

    public MainController(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }


    @GetMapping
    public String index() {
        return "redirect:/account";
    }

    @GetMapping("/account")
    public String getAccount(
            Model model,
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient
    ) {
        String currentUsername = principal.getAttribute("preferred_username");
        String userJwtToken = userClient.getAccessToken().getTokenValue();

        log.info("Пользователь '{}' запрашивает страницу личного кабинета /account.", currentUsername);

        UUID idempotencyKey = UUID.randomUUID();

        log.debug("Сгенерирован новый Idempotency-Key для сессии пользователя {}: {}",
                currentUsername, idempotencyKey);
        model.addAttribute("idempotencyKey", idempotencyKey);

        try {

            log.debug("Фронтенд отправляет исходящий вызов " +
                    "к API Gateway для получения баланса пользователя {}.", currentUsername);
            AccountResponse accountData = gatewayClient.getAccountData("/accounts", userJwtToken);

            if (accountData != null) {
                model.addAttribute("name", accountData.getName());
                model.addAttribute("birthdate", accountData.getBirthdate().format(DateTimeFormatter.ISO_DATE));
                model.addAttribute("sum", accountData.getSum());
                model.addAttribute("accounts", accountData.getAccounts());

                log.debug("Данные аккаунта для {} успешно получены и добавлены в UI модель.", currentUsername);
            }

        } catch (Exception e) {

            log.error("Критический сбой! Не удалось загрузить баланс для пользователя {}. Ошибка: {}",
                    currentUsername, e.getMessage(), e);

            List<String> existingErrors = (List<String>) model.getAttribute("errors");
            List<String> finalErrors = new ArrayList<>();

            if (existingErrors != null) {
                finalErrors.addAll(existingErrors);
            }

            finalErrors.add("Не удалось загрузить актуальный баланс. Сервер временно недоступен.");

            model.addAttribute("errors", finalErrors);

            model.addAttribute("name", currentUsername);
            model.addAttribute("birthdate", LocalDate.now());
            model.addAttribute("sum", 0);
            model.addAttribute("accounts", Collections.emptyList());
        }

        return "main";
    }

    @PostMapping("/account")
    public String editAccount(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient,
            @RequestParam("name") String name,
            @RequestParam("birthdate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            @RequestParam("idempotencyKey") UUID idempotencyKey,
            RedirectAttributes redirectAttributes
    ) {
        String currentUsername = principal.getAttribute("preferred_username");
        String userJwtToken = userClient.getAccessToken().getTokenValue();
        List<String> errorList = new ArrayList<>();

        log.info("Пользователь '{}' инициировал обновление профиля. Новое имя: '{}', ДР: {}. Idempotency-Key: {}",
                currentUsername, name, birthdate, idempotencyKey);

        try {
            UpdateAccountRequest updateRequest = new UpdateAccountRequest(name, birthdate);

            gatewayClient.sendPostOperation("/accounts", userJwtToken, idempotencyKey, updateRequest);

            log.info("Профиль пользователя {} успешно обновлен через API Gateway.", currentUsername);

        } catch (WebClientResponseException e) {

            String errorMsg = e.getResponseBodyAsString();
            log.warn("Бизнес-отказ при обновлении профиля {}. Ответ шлюза: {}", currentUsername, errorMsg);
            errorList.add("Не удалось сохранить изменения: " + errorMsg);
        } catch (Exception e) {

            log.error("Системное исключение при сохранении профиля {}: {}", currentUsername, e.getMessage(), e);
            errorList.add("Системная ошибка при сохранении профиля: " + e.getMessage());
        }

        if (!errorList.isEmpty()) {
            redirectAttributes.addFlashAttribute("errors", errorList);
        }

        return "redirect:/account";

    }


    @PostMapping("/cash")
    public String editCash(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient,
            @RequestParam("value") int value,
            @RequestParam("action") CashAction action,
            @RequestParam("idempotencyKey") UUID idempotencyKey,
            RedirectAttributes redirectAttributes
    ) {
        String currentUsername = principal.getAttribute("preferred_username");
        String userJwtToken = userClient.getAccessToken().getTokenValue();
        List<String> errorList = new ArrayList<>();

        log.info("Пользователь '{}' запросил кассовую операцию [{}]. Сумма: {}. Idempotency-Key: {}",
                currentUsername, action, value, idempotencyKey);

        try {
            CashRequest cashBody = new CashRequest(currentUsername, value, action);

            gatewayClient.sendPostOperation("/cash", userJwtToken, idempotencyKey, cashBody);

            log.info("Кассовая операция [{}] на сумму {} для пользователя {} успешно выполнена.",
                    action, value, currentUsername);

        } catch (WebClientResponseException e) {
            String errorMsg = e.getResponseBodyAsString();
            log.warn("Отказ проведения кассовой операции [{}] для {}. Причина: {}", action, currentUsername, errorMsg);
            errorList.add(errorMsg);
        } catch (Exception e) {
            log.error("Критический сбой кассовой операции для {}: {}", currentUsername, e.getMessage(), e);
            errorList.add("Системная ошибка операции с наличными: " + e.getMessage());
        }

        if (!errorList.isEmpty()) {
            redirectAttributes.addFlashAttribute("errors", errorList);
        }

        return "redirect:/account";
    }

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

        log.info("Пользователь '{}' инициировал перевод денег. Получатель: '{}', Сумма: {}. Idempotency-Key: {}",
                currentUsername, loginRecipient, value, idempotencyKey);

        try {

            gatewayClient.sendPostOperation("/transfers", userJwtToken, idempotencyKey,
                    new TransferRequest(currentUsername, loginRecipient, value));

            log.info("Перевод средств от {} к {} на сумму {} успешно проведен.",
                    currentUsername, loginRecipient, value);

        } catch (WebClientResponseException e) {
            String errorMsg = e.getResponseBodyAsString();
            log.warn("Отказ в переводе средств от {} к {}. Причина: {}", currentUsername, loginRecipient, errorMsg);
            redirectAttributes.addFlashAttribute("errors", List.of(errorMsg));
        } catch (Exception e) {
            log.error("Системный сбой при выполнении перевода от {} к {}: {}",
                    currentUsername, loginRecipient, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errors", List.of("Системная ошибка: " + e.getMessage()));
        }
        return "redirect:/account";
    }

}
