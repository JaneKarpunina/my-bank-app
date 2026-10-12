package ru.yandex.practicum.mybankfront.config;

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

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("bank.oauth2.public-issuer")
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class IngressOAuth2ClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            OAuth2ClientProperties properties,
            @Value("${bank.oauth2.public-issuer}") String publicIssuer) {
        OAuth2ClientProperties.Registration registration = properties.getRegistration().get("keycloak");
        OAuth2ClientProperties.Provider provider = properties.getProvider().get("keycloak");

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

        return new InMemoryClientRegistrationRepository(clientRegistration);
    }
}
