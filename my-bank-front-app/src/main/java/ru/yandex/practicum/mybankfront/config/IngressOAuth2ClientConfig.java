package ru.yandex.practicum.mybankfront.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("bank.oauth2.public-issuer")
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class IngressOAuth2ClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            OAuth2ClientProperties properties,
            @Value("${bank.oauth2.public-issuer}") String publicIssuer) {

        log.info("Инициализация ClientRegistrationRepository для интеграции с Keycloak Ingress SSO." +
                " Публичный эмитент (Issuer): {}", publicIssuer);
        OAuth2ClientProperties.Registration registration = properties.getRegistration().get("keycloak");
        OAuth2ClientProperties.Provider provider = properties.getProvider().get("keycloak");

        if (registration == null || provider == null) {
            log.warn("Внимание! Свойства конфигурации 'spring.security.oauth2.client.registration.keycloak'" +
                    " не обнаружены в текущей среде!");
        }

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("keycloak")
                .clientId(registration.getClientId())
                .clientSecret(registration.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(registration.getRedirectUri())
                .scope(registration.getScope())
                .authorizationUri(provider.getAuthorizationUri())
                .tokenUri(provider.getTokenUri())
                .jwkSetUri(provider.getJwkSetUri())
                .userInfoUri(provider.getUserInfoUri())
                .userNameAttributeName("preferred_username")
                .issuerUri(publicIssuer)
                .build();

        log.debug("OAuth2 ClientRegistration собран: ClientId='{}', AuthorizationUri='{}', TokenUri='{}'",
                clientRegistration.getClientId(), clientRegistration.getProviderDetails().getAuthorizationUri(),
                clientRegistration.getProviderDetails().getTokenUri());

        log.info("Регистрация Keycloak клиента авторизации успешно выполнена " +
                "в памяти (InMemoryClientRegistrationRepository).");

        return new InMemoryClientRegistrationRepository(clientRegistration);
    }
}
