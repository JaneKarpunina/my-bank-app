# Инструкция по локальному развертыванию My Bank App в Kubernetes

## Требования к окружению
* Установленный **Minikube** (драйвер `docker`)
* Утилиты **kubectl** и **helm**
* Запущенный Docker Desktop на Windows

---

## Инструкция по запуску

### Шаг 1. Локальная компиляция кода
Перед сборкой Docker-образов скомпилируйте Java-архивы во всех модулях:
```bash
mvn clean package -DskipTests
```

### Шаг 2. Подготовка кластера Minikube
1. Запустите локальный кластер:
   ```bash
   minikube start --driver=docker
   ```
2. Переключите контекст терминала на встроенный Docker-демон Minikube, чтобы образы собирались прямо в его локальный кэш:
   ```bash
   minikube docker-env | Invoke-Expression
   ```

### Шаг 3. Сборка Docker-образов проекта
Соберите образы всех микросервисов из корня проекта:
```bash
docker build -t my-registry/accounts-service:latest ./accounts-service
docker build -t my-registry/cash-service:latest ./cash-service
docker build -t my-registry/transfer-service:latest ./transfer-service
docker build -t my-registry/notification-service:latest ./notification-service
docker build -t my-registry/bank-gateway:latest ./bank-gateway
docker build -t my-registry/my-bank-front-app:latest ./my-bank-front-app
```

### Шаг 4. Деплоймент банковской экосистемы через Helm
Разверните всю инфраструктуру (Postgres, Keycloak, раздельную Кафку 2/2) и бэкенд одной командой из корня проекта:

```powershell
helm upgrade --install my-bank-release .\helm\bank-app -n bank --create-namespace
```

---

## Сетевой доступ и проверка работоспособности

### 1. Регистрация DNS-хоста Keycloak в Windows
Для работы редиректов OAuth 2.0 добавьте одну строчку в системный файл `C:\Windows\System32\drivers\etc\hosts` (от имени Администратора):
```text
127.0.0.1    keycloak
```

### 2. Запуск сетевых туннелей Port-Forward
Откройте два параллельных окна терминала и пробросьте порты фронтенда и сервера авторизации напрямую на вашу Windows-машину:
```bash
# В Окне №1: Проброс Фронтенда на свободный порт 9090 Windows
kubectl port-forward deployment/front 9090:8080 -n bank

# В Окне №2: Проброс Keycloak на стандартный порт 8080 Windows
kubectl port-forward deployment/keycloak 8080:8080 -n bank
```

### 3. Вход в личный кабинет банка
Откройте браузер в **режиме Инкогнито** и перейдите по адресу:
👉 **http://localhost:9090/account**

Система автоматически перенаправит вас на защищенную форму авторизации Keycloak кластера Kubernetes. Для входа используйте тестовые финтех-данные:
* **Логин:** `ivanov`
* **Пароль:** `password`

---

## Тестирование

Для запуска тестов из корня проекта выполните команду mvn test
