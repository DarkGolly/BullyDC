FROM debian:trixie-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
      openjdk-25-jdk-headless \
      python3 \
      python3-pip \
    && pip3 install --no-cache-dir --break-system-packages gtts \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY build/libs/DiscordBot.jar build/libs/tts.py ./

ENV TTS_PYTHON_EXECUTABLE=python3
# Без UTF-8-локали glibc считает codeset ASCII, из-за чего JVM определяет
# sun.jnu.encoding как ANSI_X3.4-1968 и портит кириллицу при передаче
# аргументов дочернему процессу (ProcessBuilder в TTSService).
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

ENTRYPOINT ["java", "-Dsun.jnu.encoding=UTF-8", "-Dfile.encoding=UTF-8", "-DsocksProxyHost=xray-proxy", "-DsocksProxyPort=1080", "-jar", "DiscordBot.jar"]