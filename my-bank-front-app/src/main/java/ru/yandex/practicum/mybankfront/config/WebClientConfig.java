package ru.yandex.practicum.mybankfront.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            WebClient.Builder webClientBuilder,
            @Value("${app.services.gateway-url}") String gatewayUrl,
            ObservationRegistry observationRegistry) {

        log.info("Инициализация бина WebClient для отправки запросов через API Gateway." +
                " Целевой URL шлюза: {}", gatewayUrl);

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(
                        clientRegistrationRepository,
                        authorizedClientRepository
                );

        oauth2Filter.setDefaultClientRegistrationId("keycloak");

        WebClient webClient = webClientBuilder
                .baseUrl(gatewayUrl)
                .observationRegistry(observationRegistry)
                .apply(oauth2Filter.oauth2Configuration())
                .build();

        log.info("Бин WebClient для фронтенда успешно собран. " +
                "Сквозной проброс контекста трассировки и токенов Keycloak включен.");
        return webClient;
    }
}

