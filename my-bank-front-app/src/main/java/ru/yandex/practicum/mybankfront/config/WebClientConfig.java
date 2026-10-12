package ru.yandex.practicum.mybankfront.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            WebClient.Builder webClientBuilder,
            @Value("${app.services.gateway-url}") String gatewayUrl,
            ObservationRegistry observationRegistry) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(
                        clientRegistrationRepository,
                        authorizedClientRepository
                );

        oauth2Filter.setDefaultClientRegistrationId("keycloak");

        return webClientBuilder
                .baseUrl(gatewayUrl)
                .observationRegistry(observationRegistry)
                .apply(oauth2Filter.oauth2Configuration())
                .build();
    }
}

