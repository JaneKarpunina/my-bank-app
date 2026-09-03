# 🏦 Инструкция по локальному развертыванию My Bank App в Kubernetes

Проект полностью переведен с инфраструктуры Docker Compose + Consul на Cloud-Native стек **Kubernetes (Minikube)** и **Helm**.
Все секреты вынесены в `Secret`, несекретные параметры в `ConfigMap`, а базы данных автоматически инициализируются (схемы `JSONB` и стартовые пользователи) при первом старте.

---

## 🎛️ Требования к окружению
* Установленный **Minikube** (драйвер `docker`)
* Утилиты **kubectl** и **helm**
* Запущенный Docker Desktop на Windows

---

## 🚀 Инструкция по запуску «в одну кнопку»

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
Перейдите в папку с зонтичным чартом `cd helm/bank-app/` и запустите автоматическую установку релиза в изолированном пространстве имен `bank`:
```bash
# 1. Создаем namespace
kubectl create namespace bank

# 2. Накатываем инфраструктуру
helm install my-bank-release . -n bank
```
*Благодаря настроенным Init-контейнерам, Kubernetes сам выстроит правильную очередь: сначала поднимутся PostgreSQL и Keycloak с автоимпортом реалма, а бэкенды и фронтенд будут терпеливо ждать их готовности, исключая ошибки BeanInstantiationException.*

---

## 🌐 Сетевой доступ и проверка работоспособности

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

После успешного входа перед вами откроется личный кабинет пользователя **Сергея Иванова** со стартовым балансом **8900 рублей**, полученным из PostgreSQL. Операции переводов и кассы полностью доступны.

---

## 🛠️ Полезные команды для демонстрации (DevOps Cheat Sheet)

* **Проверить статус всех компонентов:** `kubectl get pods -n bank`
* **Посмотреть логи любого микросервиса:** `kubectl logs deployment/accounts -n bank --tail=50`
* **Проверить наполнение БД внутри K8s:** `kubectl exec -it bank-postgres-0 -n bank -- psql -U postgres -d account_db -c "SELECT * FROM bank_accounts;"`
* **Полное удаление релиза и очистка кластера:** `helm uninstall my-bank-release -n bank && kubectl delete pvc --all -n bank`
