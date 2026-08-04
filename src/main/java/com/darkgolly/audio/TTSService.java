package com.darkgolly.audio;

import java.io.File;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

public class TTSService {

    private static final long TIMEOUT_SECONDS = 30;
    // "python.exe" зависит от PATH и не всегда зарегистрирован. На Windows штатно есть
    // лаунчер "py", на Linux/macOS обычно "python3" ("python" часто отсутствует или это python2).
    // Переменная окружения TTS_PYTHON_EXECUTABLE позволяет переопределить на любой машине.
    private static final String PYTHON_EXECUTABLE = System.getenv().getOrDefault(
            "TTS_PYTHON_EXECUTABLE",
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "py" : "python3"
    );

    public File generateSpeech(String text) throws Exception {
        // Уникальное имя файла, чтобы параллельные вызовы (разные сервера) не затирали друг друга
        File output = File.createTempFile("say-output-", ".mp3");

        ProcessBuilder pb = new ProcessBuilder(
                PYTHON_EXECUTABLE, resolveScriptPath().getAbsolutePath(), text, output.getAbsolutePath()
        );

        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Ошибка: TTS-процесс не завершился за " + TIMEOUT_SECONDS + " секунд");
        }

        if (!output.exists() || output.length() == 0) {
            throw new RuntimeException("Ошибка: файл TTS не создан");
        }

        return output;
    }

    // tts.py лежит рядом с jar-файлом; ищем его там, чтобы скрипт находился независимо
    // от того, из какой рабочей директории запущен процесс.
    private File resolveScriptPath() {
        try {
            File jarDir = new File(TTSService.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            File script = new File(jarDir, "tts.py");
            if (script.exists()) {
                return script;
            }
        } catch (URISyntaxException | NullPointerException ignored) {
            // падаем обратно на относительный путь (например, запуск из IDE)
        }
        return new File("tts.py");
    }
}
