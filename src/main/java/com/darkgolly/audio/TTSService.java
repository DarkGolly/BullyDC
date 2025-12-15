package com.darkgolly.audio;

import java.io.File;

public class TTSService {

    public File generateSpeech(String text) throws Exception {
        // Путь к файлу
        File output = new File("say-output.mp3");

        ProcessBuilder pb = new ProcessBuilder(
                "python", "tts.py", text, output.getAbsolutePath()
        );

        pb.redirectErrorStream(true);

        Process process = pb.start();
        process.waitFor();

        if (!output.exists()) {
            throw new RuntimeException("Ошибка: файл TTS не создан");
        }

        return output;
    }
}
