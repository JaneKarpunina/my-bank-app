package ru.yandex.practicum.accounts.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        InMemoryOAuth2AuthorizedClientService authorizedClientService =
                new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }


    @Bean
    public WebClient notificationWebClient(OAuth2AuthorizedClientManager authorizedClientManager,
                                           @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
                                           @Value("${app.services.notification-url}") String notificationUrl) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("notification-client");

        return webClientBuilder
                .baseUrl(notificationUrl)
                .apply(oauth2Filter.oauth2Configuration())
                .build();
    }
}

