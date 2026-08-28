package com.darkgolly.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Единая точка доступа к конфигурации. Реальные переменные окружения имеют приоритет,
// а значения из .env-файла рядом с jar-файлом используются как основной способ хранить
// токены/пути, не прописывая их вручную в системных переменных или .bat-скрипте запуска.
public final class Env {
    private static final Logger log = LoggerFactory.getLogger(Env.class);
    private static final Map<String, String> FILE_VALUES = load();

    private Env() {
    }

    public static String get(String key) {
        return get(key, null);
    }

    public static String get(String key, String defaultValue) {
        String systemValue = System.getenv(key);
        if (systemValue != null) {
            return systemValue;
        }
        return FILE_VALUES.getOrDefault(key, defaultValue);
    }

    private static Map<String, String> load() {
        Map<String, String> values = new HashMap<>();
        Path envFile = resolveEnvFilePath();
        if (!Files.isRegularFile(envFile)) {
            return values;
        }

        try {
            List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }

                String key = line.substring(0, eq).strip();
                String value = line.substring(eq + 1).strip();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }

                values.put(key, value);
            }
            log.info("Загружен .env из {} ({} перем.)", envFile, values.size());
        } catch (IOException e) {
            log.warn("Не удалось прочитать .env ({}): {}", envFile, e.getMessage());
        }

        return values;
    }

    // .env лежит рядом с jar-файлом — тот же приём, что используется в TTSService для tts.py.
    private static Path resolveEnvFilePath() {
        try {
            Path jarDir = Path.of(Env.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            if (jarDir != null) {
                return jarDir.resolve(".env");
            }
        } catch (URISyntaxException | NullPointerException ignored) {
            // падаем обратно на текущую рабочую директорию (например, запуск из IDE)
        }
        return Path.of(".env");
    }
}
