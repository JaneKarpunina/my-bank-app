package ru.yandex.practicum.bank.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import ru.yandex.practicum.bank.gateway.security.JwtTokenRelayGatewayFilterFactory;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        log.info("Инициализация реактивной цепочки фильтров безопасности SecurityWebFilterChain для API-шлюза");
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        log.info("Правила маршрутизации шлюза успешно настроены. Доступ к эндпоинтам /actuator/** открыт без авторизации.");

        return http.build();
    }

    @Bean
    public JwtTokenRelayGatewayFilterFactory jwtTokenRelayGatewayFilterFactory() {
        log.debug("Регистрация фабрики фильтров JwtTokenRelayGatewayFilterFactory для проброса JWT-токенов в бэкенд-микросервисы.");
        return new JwtTokenRelayGatewayFilterFactory();
    }
}
