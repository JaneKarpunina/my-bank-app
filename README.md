## my-bank-app


### как запускать проект

- нужно в корне проекта выполнить команду mvn clean package -DskipTests
- в файл C:\Windows\System32\drivers\etc\hosts добавьте строку 127.0.0.1   bank-keycloak
- запустите микросервисы в докер командой docker compose up -d --build из корня проекта
- откройте панель Consul по адресу http://localhost:8500 -> раздел Key/Value и внесите следующие настройки:

  config/account-service/data:

```properties
server.port=8082

spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST:localhost}:5432/account_db
spring.datasource.username=postgres
spring.datasource.password=root_password

spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

spring.sql.init.mode=always

spring.security.oauth2.client.registration.notification-client.provider=keycloak
spring.security.oauth2.client.registration.notification-client.authorization-grant-type=client_credentials
spring.security.oauth2.client.registration.notification-client.client-id=accounts-service-client
spring.security.oauth2.client.registration.notification-client.client-secret=NzQhQZt7JnPubsovCoVUbpIARuqgVIET

spring.security.oauth2.client.provider.keycloak.issuer-uri=http://bank-keycloak:8080/realms/bank-realm

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://bank-keycloak:8080/realms/bank-realm
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://bank-keycloak:8080/realms/bank-realm/protocol/openid-connect/certs

app.services.notification-url=http://notification-service
```
config/application/data:

```properties
spring.cloud.consul.discovery.register-health-check=false

spring.cloud.config.fail-fast=true
spring.cloud.consul.config.watch.delay=10000

spring.cloud.consul.discovery.prefer-ip-address=true
```

config/bank-gateway/data:

```properties
server.port=8085

#spring.security.oauth2.resourceserver.jwt.issuer-uri=http://${KEYCLOAK_HOST:localhost}:8080/realms/bank-realm
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://bank-keycloak:8080/realms/bank-realm

spring.cloud.gateway.routes[0].id=account-service
spring.cloud.gateway.routes[0].uri=lb://account-service
spring.cloud.gateway.routes[0].predicates[0]=Path=/accounts/**
spring.cloud.gateway.routes[0].filters[0].name=TokenRelay

spring.cloud.gateway.routes[1].id=transfer-service
spring.cloud.gateway.routes[1].uri=lb://transfer-service
spring.cloud.gateway.routes[1].predicates[0]=Path=/transfers
spring.cloud.gateway.routes[1].predicates[1]=Path=/transfers/**
spring.cloud.gateway.routes[1].filters[0].name=TokenRelay


spring.cloud.gateway.routes[2].id=cash-service
spring.cloud.gateway.routes[2].uri=lb://cash-service
spring.cloud.gateway.routes[2].predicates[0]=Path=/cash
spring.cloud.gateway.routes[2].predicates[1]=Path=/cash/**
spring.cloud.gateway.routes[2].filters[0].name=TokenRelay

management.endpoints.web.exposure.include=*

logging.level.org.springframework.cloud.gateway=TRACE
logging.level.org.springframework.security=DEBUG
logging.level.web=DEBUG
```

config/cash-service/data:

```properties
server.port=8087

spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST:localhost}:5432/cash_db
spring.datasource.username=postgres
spring.datasource.password=root_password
spring.jpa.hibernate.ddl-auto=validate

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://bank-keycloak:8080/realms/bank-realm
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://bank-keycloak:8080/realms/bank-realm/protocol/openid-connect/certs

spring.security.oauth2.client.registration.cash-client.client-id=cash-service-client
spring.security.oauth2.client.registration.cash-client.client-secret=iwrF4t35VCH5NeGz9U8N23GSdYqcWHSk
spring.security.oauth2.client.registration.cash-client.client-authentication-method=client_secret_post
spring.security.oauth2.client.registration.cash-client.authorization-grant-type=client_credentials
spring.security.oauth2.client.registration.cash-client.provider=keycloak

spring.security.oauth2.client.provider.keycloak.issuer-uri=http://bank-keycloak:8080/realms/bank-realm

app.services.accounts-url=http://account-service
app.services.notification-url=http://notification-service
```

config/my-bank-front-app/data:

```properties
server.port=8084

spring.security.oauth2.client.registration.keycloak.client-id=bank-ui
spring.security.oauth2.client.registration.keycloak.client-secret=B8qFblHYQ20liM4QdS4getoKctNoj9tU
spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.keycloak.scope=openid,profile
spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
spring.security.oauth2.client.registration.keycloak.provider=keycloak



spring.security.oauth2.client.provider.keycloak.issuer-uri=http://bank-keycloak:8080/realms/bank-realm
spring.security.oauth2.client.provider.keycloak.authorization-uri=http://bank-keycloak:8080/realms/bank-realm/protocol/openid-connect/auth
spring.security.oauth2.client.provider.keycloak.token-uri=http://bank-keycloak:8080/realms/bank-realm/protocol/openid-connect/token
spring.security.oauth2.client.provider.keycloak.jwk-set-uri=http://bank-keycloak:8080/realms/bank-realm/protocol/openid-connect/certs
app.gateway.host=${GATEWAY_HOST:localhost}

app.services.gateway-url=http://${app.gateway.host}:8085

```

config/notification-service/data:

```properties
server.port=8086

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://bank-keycloak:8080/realms/bank-realm
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://bank-keycloak:8080/realms/bank-realm/protocol/openid-connect/certs
```

config/transfer-service/data:

```properties
server.port=8083

spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST:localhost}:5432/transfer_db
spring.datasource.username=postgres
spring.datasource.password=root_password
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate

spring.security.oauth2.client.registration.transfer-client.client-id=transfer-service-client
spring.security.oauth2.client.registration.transfer-client.client-secret=40MRsM2jMbHsOuA3QRsBZrTZZMtpOtuP
spring.security.oauth2.client.registration.transfer-client.client-authentication-method=client_secret_post
spring.security.oauth2.client.registration.transfer-client.authorization-grant-type=client_credentials

spring.security.oauth2.client.registration.transfer-client.provider=keycloak

spring.security.oauth2.client.provider.keycloak.issuer-uri=http://bank-keycloak:8080/realms/bank-realm

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://bank-keycloak:8080/realms/bank-realm
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://bank-keycloak:8080/realms/bank-realm/protocol/openid-connect/certs



app.services.accounts-url=http://account-service
app.services.notification-url=http://notification-service
```

- настройте Keycloak:
  1. Перейдите в админку по адресу http://localhost:8080/admin/ (логин: admin, пароль: admin).
  2. Создайте Realm с именем bank-realm.
  3. В разделе Realm Roles создайте роли ROLE_BALANCE_MODIFIER и ROLE_NOTIFICATION
  4. в разделе Clients создайте клиента bank-ui: Включите Client authentication, Standard flow. 
     Добавьте Valid Redirect URIs: `http://bank-keycloak:8084/*`, `http://localhost:8084/*`. Добавьте Web Origins:
     http://localhost:8084, http://bank-keycloak:8084.
     Скопируйте Secret в настройки my-bank-front-app в Consul.
  5. в разделе Clients создайте клиента transfer-service-client: Включите Client authentication, Service accounts roles. Выключите Standard flow. 
     Во вкладке Service account roles назначьте роли ROLE_BALANCE_MODIFIER и ROLE_NOTIFICATION. Скопируйте Secret в свойства transfer-service в Consul.
  6. в разделе Clients создайте клиента cash-service-client: Настройте аналогично transfer-service-client, выдав роли ROLE_BALANCE_MODIFIER и ROLE_NOTIFICATION.
     Скопируйте Secret в свойства cash-service в Consul.
  7.  в разделе Clients создайте клиента accounts-service-client: Настройте аналогично transfer-service-client, выдав роль ROLE_NOTIFICATION.
      Скопируйте Secret в свойства account-service в Consul.
  8.  В разделе Users создайте пользователя ivanov. 
      Во вкладке Credentials установите ему пароль ivanov (выключите тумблер Temporary).

- Откройте новое окно Инкогнито в браузере, Перейдите по адресу: http://bank-keycloak:8084/account.
  Авторизуйтесь под ivanov / ivanov.

### как запускать тесты

из корня проекта запустите команду mvn clean test.
