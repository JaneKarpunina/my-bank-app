# Локальный запуск My Bank App через Ingress

Нужны Docker Desktop, Minikube, kubectl, Helm, Java 21 и Maven. Команды ниже запускаются из корня проекта в PowerShell. Порт 80 на компьютере должен быть свободен.

## 1. Имена для браузера

Добавьте строку в `C:\Windows\System32\drivers\etc\hosts` из редактора, запущенного от имени администратора:

```text
127.0.0.1 bank.local keycloak.local zipkin.local
```

## 2. Кластер и образы

```powershell
minikube start -p bank-app --driver=docker --memory=4000 --cpus=3 --ports=80:80 --addons=ingress
mvn package -DskipTests
minikube -p bank-app image build -t my-registry/accounts-service:latest .\accounts-service
minikube -p bank-app image build -t my-registry/cash-service:latest .\cash-service
minikube -p bank-app image build -t my-registry/transfer-service:latest .\transfer-service
minikube -p bank-app image build -t my-registry/notification-service:latest .\notification-service
minikube -p bank-app image build -t my-registry/bank-gateway:latest .\bank-gateway
minikube -p bank-app image build -t my-registry/my-bank-front-app:latest .\my-bank-front-app
```

Профиль `bank-app` отделён от других локальных кластеров. Опция `--ports=80:80` публикует HTTP-порт узла Minikube в Docker Desktop; отдельный процесс `kubectl port-forward` не нужен.

## 3. Развёртывание

```powershell
helm upgrade --install my-bank-release .\helm\bank-app -n bank --create-namespace
helm upgrade --install zipkin .\zipkin -n bank
kubectl get pods -n bank
```

Дождитесь состояния `Running` и готовности `1/1` у приложений и Keycloak. Откройте `http://bank.local` и войдите как `ivanov` с паролем `password`. Интерфейс Zipkin доступен по адресу `http://zipkin.local/zipkin/`.

После изменения Java-кода пересоберите JAR и соответствующий образ. При повторном использовании тега `latest` перезапустите изменённый Deployment, чтобы Pod взял новый образ.

## Тестирование

```powershell
mvn test
```

# Инструкция по запуску в докер

### 1. Сборка и холодный запуск

в файл C:\Windows\System32\drivers\etc\hosts добавьте строку 127.0.0.1   bank-keycloak

Запустите полную цепочку компиляции артефактов и сборки контейнеров одной командой:
```bash
# Сборка свежих .jar пакетов
mvn clean package -DskipTests

# Холодный запуск платформы с очисткой кэша
docker compose down -v
docker compose up -d --build
```

### 2. Доступ к локальным веб-интерфейсам
* **Фронтенд Личного Кабинета:** [http://localhost:8084/account](http://localhost:8084/account)
* **Панель Keycloak SSO:** [http://localhost:8080](http://localhost:8080) (Логин/Пароль: `admin` / `admin`)
* **Тестовый пользователь:** `ivanov` / `password`
