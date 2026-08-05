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

ENTRYPOINT ["java", "-DsocksProxyHost=xray-proxy", "-DsocksProxyPort=1080", "-jar", "DiscordBot.jar"]