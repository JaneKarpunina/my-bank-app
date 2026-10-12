package ru.yandex.practicum.mybankfront.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class IngressOAuth2ClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IngressOAuth2ClientConfig.class)
            .withPropertyValues(
                    "bank.oauth2.public-issuer=http://keycloak.local/realms/bank-realm",
                    "spring.security.oauth2.client.registration.keycloak.client-id=bank-ui",
                    "spring.security.oauth2.client.registration.keycloak.client-secret=test-secret",
                    "spring.security.oauth2.client.registration.keycloak.scope=openid,profile",
                    "spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
                    "spring.security.oauth2.client.provider.keycloak.authorization-uri=http://keycloak.local/realms/bank-realm/protocol/openid-connect/auth",
                    "spring.security.oauth2.client.provider.keycloak.token-uri=http://keycloak:8080/realms/bank-realm/protocol/openid-connect/token",
                    "spring.security.oauth2.client.provider.keycloak.jwk-set-uri=http://keycloak:8080/realms/bank-realm/protocol/openid-connect/certs",
                    "spring.security.oauth2.client.provider.keycloak.user-info-uri=http://keycloak:8080/realms/bank-realm/protocol/openid-connect/userinfo");

    @Test
    void usesPublicIssuerWithoutResolvingItInsideTheCluster() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ClientRegistrationRepository repository = context.getBean(ClientRegistrationRepository.class);
            ClientRegistration registration = repository.findByRegistrationId("keycloak");

            assertThat(registration.getProviderDetails().getIssuerUri())
                    .isEqualTo("http://keycloak.local/realms/bank-realm");
            assertThat(registration.getProviderDetails().getAuthorizationUri())
                    .isEqualTo("http://keycloak.local/realms/bank-realm/protocol/openid-connect/auth");
            assertThat(registration.getProviderDetails().getTokenUri())
                    .isEqualTo("http://keycloak:8080/realms/bank-realm/protocol/openid-connect/token");
        });
    }
}
