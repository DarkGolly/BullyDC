# Обновление и перезапуск бота в Docker (NAS)

Инструкция описывает только пересборку и перезапуск Docker-контейнера. Сборка `DiscordBot.jar` делается отдельно (локально, через Gradle) и в эту инструкцию не входит.

## 1. Убедитесь, что собран свежий JAR

В `build/libs/` должны лежать актуальные `DiscordBot.jar` и `tts.py` — Dockerfile берёт их именно оттуда:

```
COPY build/libs/DiscordBot.jar build/libs/tts.py ./
```

## 2. Скопируйте проект на NAS

Если проект уже лежит на NAS — обновите изменившиеся файлы (`Dockerfile`, `build/libs/*`), например через `scp`/`rsync` или папку SMB.

## 3. Подключитесь к NAS по SSH и перейдите в папку проекта

```bash
ssh <ваш_пользователь>@<ip_nas>
cd /volume1/homes/darkgolly/projects/BullyDC
```

## 4. Соберите новый образ

```bash
sudo docker build -t discord-bot:latest .
```

## 5. Остановите и удалите старый контейнер

Узнайте имя/ID старого контейнера, если не помните:

```bash
sudo docker ps -a
```

Затем:

```bash
sudo docker stop <имя_контейнера>
sudo docker rm <имя_контейнера>
```

## 6. Запустите новый контейнер с теми же параметрами, что и раньше

Важно сохранить сеть, в которой доступен контейнер `xray-proxy` — Dockerfile жёстко прописывает `-DsocksProxyHost=xray-proxy`, значит новый контейнер должен быть в той же docker-сети, что и прокси.

Токен и остальные настройки теперь не передаются через `-e`, а лежат в файле `.env` рядом с проектом на NAS (см. `MusicArchiver`/`Env`) — его нужно примонтировать в `/app/.env`. Папка с музыкой тоже монтируется отдельным томом и должна совпадать с `MUSIC_ARCHIVE_PATH` из `.env`:

```bash
sudo docker run -d \
  --name discord-bot \
  --network <та_же_сеть_что_и_xray-proxy> \
  --restart unless-stopped \
  -v /volume1/homes/darkgolly/projects/BullyDC/.env:/app/.env:ro \
  -v /volume1/music:/music \
  discord-bot:latest
```

Если раньше контейнер запускался через **Container Manager** (GUI Synology) — проще пересобрать образ командой из шага 4 по SSH, а затем в Container Manager: остановить старый контейнер → удалить → создать новый из образа `discord-bot:latest`, указав ту же сеть и те же тома (`.env` и папку с музыкой), что описаны выше.

## 7. Проверьте логи

```bash
sudo docker logs -f discord-bot
```

Убедитесь, что бот залогинился в Discord и ошибок при старте нет.

---

**TODO:** подставить реальное имя сети/контейнера `xray-proxy` и имя старого контейнера бота вместо плейсхолдеров, когда они будут известны.
