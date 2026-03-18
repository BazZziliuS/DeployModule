# DeployModule для GravitLauncher

Модуль для автоматизации деплоя клиентов и профилей на GravitLauncher через HTTP API. Предназначен для использования в CI/CD пайплайнах (GitHub Actions, GitLab CI и т.д.).

## Возможности

- Загрузка профилей (JSON) через HTTP POST
- Загрузка клиентов (ZIP-архив) через HTTP POST
- Токен-авторизация с ограничением доступа по профилям/клиентам (glob-паттерны)
- Автоматическая синхронизация после загрузки
- Защита от path traversal

## Установка

1. Собрать модуль:
   ```bash
   ./gradlew jar
   ```
2. Скопировать `build/libs/DeployModule.jar` в директорию `modules/` LaunchServer
3. Запустить LaunchServer — модуль создаст конфиг автоматически

## Конфигурация

Файл: `config/DeployModule/Config.json`

```json
{
  "enabled": true,
  "tokens": [
    {
      "token": "technomagic-secret-token",
      "allowedProfiles": ["TechnoMagic"],
      "allowedClients": ["TechnoMagic-*"]
    },
    {
      "token": "hitech-secret-token",
      "allowedProfiles": ["HiTech"],
      "allowedClients": ["HiTech-*"]
    },
    {
      "token": "admin-token-full-access",
      "allowedProfiles": [],
      "allowedClients": []
    }
  ]
}
```

При первом запуске генерируется один случайный токен с полным доступом.

### Несколько токенов

Модуль поддерживает неограниченное количество токенов. Каждый токен может быть привязан к конкретным сборкам — например, отдельный токен для TechnoMagic и отдельный для HiTech. Это позволяет разграничить доступ между CI-пайплайнами разных сборок.

### Ограничение доступа токена

| Поле | Описание |
|------|----------|
| `token` | Секретный ключ для авторизации |
| `allowedProfiles` | Glob-паттерны разрешённых профилей |
| `allowedClients` | Glob-паттерны разрешённых клиентов |

- Пустой список `[]` = доступ ко всему
- Поддерживается `*` как wildcard (например `HiTech-*` разрешает `HiTech-1.20.1`, `HiTech-1.19.4` и т.д.)

## API

### Загрузка профиля

```
POST /webapi/upload/profile?token=TOKEN
Content-Type: application/json

<тело JSON профиля>
```

### Загрузка клиента

```
POST /webapi/upload/client?name=ClientName&token=TOKEN

<бинарное тело ZIP-архива>
```

Токен можно передавать через query-параметр `token` или заголовок `X-Upload-Token`.

### Ответы

Все ответы в формате JSON:
```json
{"message": "Profile 'MyProfile' uploaded successfully", "error": null}
```
```json
{"message": null, "error": "Invalid token"}
```

## Примеры использования

### curl

```bash
# Загрузка профиля
curl -X POST "http://localhost:9274/webapi/upload/profile?token=TOKEN" \
  -H "Content-Type: application/json" \
  -d @profile.json

# Загрузка клиента
curl -X POST "http://localhost:9274/webapi/upload/client?name=MyClient&token=TOKEN" \
  --data-binary @client.zip
```

### GitHub Actions

Для каждой сборки используется свой секрет с токеном:

```yaml
# Деплой сборки TechnoMagic
- name: Deploy TechnoMagic profile
  run: |
    curl -X POST \
      "${{ secrets.LAUNCHER_URL }}/webapi/upload/profile" \
      -H "X-Upload-Token: ${{ secrets.TECHNOMAGIC_TOKEN }}" \
      -d @technomagic-profile.json \
      --fail --show-error

- name: Deploy TechnoMagic client
  run: |
    curl -X POST \
      "${{ secrets.LAUNCHER_URL }}/webapi/upload/client?name=TechnoMagic-1.20.1" \
      -H "X-Upload-Token: ${{ secrets.TECHNOMAGIC_TOKEN }}" \
      --data-binary @client.zip \
      --fail --show-error
```

```yaml
# Деплой сборки HiTech (отдельный пайплайн, свой токен)
- name: Deploy HiTech client
  run: |
    curl -X POST \
      "${{ secrets.LAUNCHER_URL }}/webapi/upload/client?name=HiTech-1.20.1" \
      -H "X-Upload-Token: ${{ secrets.HITECH_TOKEN }}" \
      --data-binary @client.zip \
      --fail --show-error
```

## Требования

- GravitLauncher 5.6.9+
- Java 21+
- `LocalProfileProvider` в конфигурации LaunchServer

## Безопасность

- Параметр `name` клиента проверяется на path traversal (`..`, `/`, `\`)
- ZIP-записи проверяются на выход за пределы целевой директории
- Каждый токен может быть ограничен конкретными профилями и клиентами
- Модуль можно отключить через `enabled: false`
