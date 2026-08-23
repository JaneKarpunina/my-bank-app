package ru.yandex.practicum.bank.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway-фильтр, реализующий Token Relay:
 * забирает JWT из SecurityContext (если есть) или из входящего заголовка Authorization
 * и подставляет его в исходящий запрос к микросервису.
 * <p>
 * В application.properties используется как:
 * <p>
 * filters:
 * - name: JwtTokenRelay
 */
public class JwtTokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Logger log =
            LoggerFactory.getLogger(JwtTokenRelayGatewayFilterFactory.class);

    public JwtTokenRelayGatewayFilterFactory() {
        super(Object.class);
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) ->
                extractToken(exchange)
                        // если токен не нашли ни в контексте, ни в заголовке — считаем, что конфигурация сломана
                        .switchIfEmpty(Mono.error(
                                new IllegalStateException("JWT token not found in SecurityContext or Authorization header")))
                        .flatMap(token -> chain.filter(addToken(exchange, token)));
    }

    /**
     * Пытаемся достать токен:
     * 1) сначала из SecurityContext (если Gateway выступает как Resource Server),
     * 2) если там пусто — из входящего заголовка Authorization.
     */
    private Mono<String> extractToken(ServerWebExchange exchange) {
        Mono<String> fromContext = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken().getTokenValue());

        Mono<String> fromHeader = Mono
                .justOrEmpty(exchange.getRequest().getHeaders().getFirst("Authorization"))
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring(7));

        return fromContext.switchIfEmpty(fromHeader);
    }

    /**
     * Добавляем в исходящий запрос заголовок Authorization: Bearer <token>.
     */
    private ServerWebExchange addToken(ServerWebExchange exchange, String token) {
        var mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("Authorization", "Bearer " + token)
                        .build())
                .build();

        log.debug("Token relayed for path {} (len={})",
                exchange.getRequest().getPath(), token.length());

        return mutated;
    }
}
