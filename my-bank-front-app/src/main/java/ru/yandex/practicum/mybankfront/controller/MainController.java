package ru.yandex.practicum.mybankfront.controller;

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

@Controller
public class MainController {


    private final WebClient webClient;

    public MainController(WebClient webClient) {
        this.webClient = webClient;
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

    @PostMapping("/account")
    public String editAccount(
            @AuthenticationPrincipal OAuth2User principal,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient userClient,
            @RequestParam("name") String name,
            @RequestParam("birthdate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            RedirectAttributes redirectAttributes
    ) {
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

        try {
            CashRequest cashBody = new CashRequest(currentUsername, value, action);

            webClient.post()
                    .uri("/cash")
                    .header("Authorization", "Bearer " + userJwtToken)
                    .header("X-Idempotency-Key", idempotencyKey.toString())
                    .bodyValue(cashBody)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (WebClientResponseException e) {
            errorList.add(e.getResponseBodyAsString());
        } catch (Exception e) {
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
