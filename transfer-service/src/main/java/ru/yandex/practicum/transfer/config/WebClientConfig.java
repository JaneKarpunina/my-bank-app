package ru.yandex.practicum.transfer.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository) {

        log.info("Инициализация OAuth2AuthorizedClientManager" +
                " для управления клиентскими токенами (Client Credentials)...");

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        InMemoryOAuth2AuthorizedClientService clientService =
                new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, clientService);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        log.info("OAuth2AuthorizedClientManager успешно создан и настроен на провайдер Client Credentials.");
        return authorizedClientManager;
    }

    @Bean
    public WebClient internalServicesWebClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            WebClient.Builder webClientBuilder,
            ObservationRegistry observationRegistry) {

        log.info("Сборка бина WebClient для интеграции бэкенд-микросервисов внутри transfer-service");

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("transfer-client");

        WebClient webClient = webClientBuilder
                .observationRegistry(observationRegistry)
                .apply(oauth2Filter.oauth2Configuration())
                .build();

        log.info("Бин internalServicesWebClient успешно собран. " +
                "Сквозной проброс контекста трассировки и токенов Keycloak включен.");
        return webClient;
    }
}

