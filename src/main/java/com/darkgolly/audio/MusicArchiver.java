package com.darkgolly.audio;

import com.darkgolly.util.Env;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Сохраняет проигранные YouTube-треки в mp3 во внешнюю музыкальную библиотеку (например,
// смонтированную папку Synology Audio Station), записывая тот же PCM-поток, который LavaPlayer
// уже успешно тянет через OAuth (см. PlayerManager) и отдаёт в Discord — вместо повторного
// скачивания через yt-dlp, который упирается в PoToken-стену YouTube (см. issue #17348).
// Кодированием в mp3 и тегами занимается ffmpeg (уже в образе для tts.py); сеть/прокси ему
// не нужны — исходные байты уже пришли из JVM-сокета, который проксируется на уровне
// -DsocksProxyHost.
public class MusicArchiver implements PcmFilterFactory {
    private static final Logger log = LoggerFactory.getLogger(MusicArchiver.class);
    private static final long FFMPEG_EXIT_TIMEOUT_SECONDS = 60;
    private static final long MAX_DURATION_MS = TimeUnit.MINUTES.toMillis(30);
    // Если из-за /skip или обрыва соединения записано заметно меньше, чем длится трек — файл,
    // скорее всего, огрызок, а не полноценный трек. Не оставляем его в коллекции.
    private static final double MIN_COMPLETE_RATIO = 0.9;

    // Если переменная не задана — архивирование выключено (например, локальный запуск без NAS).
    private static final String LIBRARY_PATH = Env.get("MUSIC_ARCHIVE_PATH");
    private static final String FFMPEG_EXECUTABLE = Env.get("FFMPEG_EXECUTABLE", "ffmpeg");

    private static final MusicArchiver INSTANCE = new MusicArchiver();

    // Индекс уже сохранённых видео (по identifier из AudioTrackInfo) — аналог --download-archive
    // из yt-dlp, чтобы не перезаписывать один и тот же трек при каждом повторном проигрывании.
    private final Path archiveIndexFile;
    private final Set<String> archivedIds = ConcurrentHashMap.newKeySet();

    // process() дёргается с потока плеера LavaPlayer, а finish() умеет блокироваться на закрытии
    // ffmpeg — не хотим стопорить воспроизведение/переключение треков, поэтому уносим в отдельный пул.
    private final ExecutorService finishExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "music-archiver-finish");
        t.setDaemon(true);
        return t;
    });

    private MusicArchiver() {
        archiveIndexFile = (LIBRARY_PATH == null || LIBRARY_PATH.isBlank())
                ? null : Path.of(LIBRARY_PATH, ".archive-index.txt");
        loadArchiveIndex();
    }

    public static MusicArchiver getInstance() {
        return INSTANCE;
    }

    private void loadArchiveIndex() {
        if (archiveIndexFile == null || !Files.isRegularFile(archiveIndexFile)) {
            return;
        }
        try {
            archivedIds.addAll(Files.readAllLines(archiveIndexFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Не удалось прочитать индекс архива {}: {}", archiveIndexFile, e.getMessage());
        }
    }

    // Список не может быть null — вызывающий код (LavaPlayer) сразу оборачивает результат
    // в new ArrayList<>(...) без проверки на null.
    @Override
    public List<AudioFilter> buildChain(AudioTrack track, AudioDataFormat format, UniversalPcmAudioFilter downstream) {
        if (!shouldArchive(track)) {
            return Collections.emptyList();
        }
        try {
            return List.of(new TrackRecordingFilter(track, format, downstream));
        } catch (IOException e) {
            log.warn("Не удалось запустить ffmpeg для архивирования «{}»: {}", track.getInfo().title, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean shouldArchive(AudioTrack track) {
        if (archiveIndexFile == null) {
            return false;
        }
        // Не пытаемся архивировать TTS-озвучку и прочие не-YouTube источники.
        if (!(track.getSourceManager() instanceof YoutubeAudioSourceManager)) {
            return false;
        }

        AudioTrackInfo info = track.getInfo();
        // У стримов длительность либо неизвестна, либо не имеет смысла — записывать нечего.
        if (info.isStream || info.identifier == null) {
            return false;
        }
        if (track.getDuration() > MAX_DURATION_MS) {
            log.info("Пропускаю архивирование «{}» — длиннее 30 минут ({} мин)", info.title, track.getDuration() / 60000);
            return false;
        }
        return !archivedIds.contains(info.identifier);
    }

    private void markArchived(String identifier) {
        if (!archivedIds.add(identifier)) {
            return;
        }
        try {
            Files.writeString(archiveIndexFile, identifier + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Не удалось обновить индекс архива: {}", e.getMessage());
        }
    }

    // yt-dlp раньше сам санитизировал имена файлов/папок — теперь это на нас; вырезаем то, что
    // недопустимо в путях Windows/Linux.
    private static String sanitize(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    // Перехватывает PCM прямо из цепочки фильтров плеера (до кодирования в Opus для Discord),
    // параллельно скармливает его ffmpeg через stdin и пропускает без изменений дальше по цепочке —
    // на само воспроизведение в Discord это никак не влияет.
    private class TrackRecordingFilter implements FloatPcmAudioFilter {
        private final UniversalPcmAudioFilter downstream;
        private final AudioTrack track;
        private final int channelCount;
        private final long expectedSamples;
        private final Process ffmpeg;
        private final OutputStream ffmpegStdin;
        private final Path tempFile;
        private final Path finalFile;
        private final StringBuilder ffmpegOutput = new StringBuilder();
        private volatile boolean writeFailed = false;
        private long samplesWritten = 0;
        private boolean finished = false;

        TrackRecordingFilter(AudioTrack track, AudioDataFormat format, UniversalPcmAudioFilter downstream) throws IOException {
            this.track = track;
            this.downstream = downstream;
            this.channelCount = format.channelCount;
            this.expectedSamples = format.sampleRate * track.getDuration() / 1000;

            AudioTrackInfo info = track.getInfo();
            String artist = info.author == null || info.author.isBlank() ? "Unknown" : info.author;
            String title = info.title == null || info.title.isBlank() ? info.identifier : info.title;

            // Synology Audio Station индексирует только то, что лежит прямо в папке music
            // (без вложенных папок по автору) — поэтому кладём файлы сразу в LIBRARY_PATH.
            Path libraryDir = Path.of(LIBRARY_PATH);
            Files.createDirectories(libraryDir);
            this.finalFile = libraryDir.resolve(sanitize(artist) + " - " + sanitize(title) + ".mp3");
            this.tempFile = libraryDir.resolve("." + sanitize(artist) + " - " + sanitize(title) + ".part.mp3");

            List<String> command = List.of(
                    FFMPEG_EXECUTABLE, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "f32le", "-ar", String.valueOf(format.sampleRate), "-ac", String.valueOf(format.channelCount),
                    "-i", "pipe:0",
                    "-metadata", "title=" + title,
                    "-metadata", "artist=" + artist,
                    "-codec:a", "libmp3lame", "-q:a", "0",
                    tempFile.toString()
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            this.ffmpeg = pb.start();
            this.ffmpegStdin = ffmpeg.getOutputStream();

            // Читаем вывод параллельно, иначе при заполнении буфера трубы ffmpeg зависнет —
            // тот же приём, что и в TTSService/старом MusicArchiver.
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(ffmpeg.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ffmpegOutput.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // поток закрывается при завершении процесса
                }
            }, "music-archiver-ffmpeg-output");
            outputReader.setDaemon(true);
            outputReader.start();
        }

        @Override
        public void process(float[][] samples, int offset, int length) throws InterruptedException {
            if (!writeFailed) {
                writeSamples(samples, offset, length);
            }
            downstream.process(samples, offset, length);
        }

        private void writeSamples(float[][] samples, int offset, int length) {
            ByteBuffer buffer = ByteBuffer.allocate(length * channelCount * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < length; i++) {
                for (int c = 0; c < channelCount; c++) {
                    buffer.putFloat(samples[c][offset + i]);
                }
            }
            try {
                ffmpegStdin.write(buffer.array());
                samplesWritten += length;
            } catch (IOException e) {
                writeFailed = true;
                log.warn("Запись в ffmpeg для «{}» прервана: {}", track.getInfo().title, e.getMessage());
            }
        }

        @Override
        public void seekPerformed(long currentTime, long newTime) {
            // Перемотка ломает линейность записи — проще оборвать архивирование этого трека
            // (он попробует записаться заново при следующем полном проигрывании), чем сохранить
            // файл с "разрывом" в середине. У бота сейчас нет команды перемотки, это подстраховка.
            finish(false);
        }

        @Override
        public void flush() {
            // ffmpeg сам буферизует и пишет по готовности; сбрасывать здесь нечего.
        }

        @Override
        public void close() {
            finish(true);
        }

        private void finish(boolean keepOutput) {
            if (finished) {
                return;
            }
            finished = true;
            finishExecutor.submit(() -> {
                try {
                    ffmpegStdin.close();
                } catch (IOException ignored) {
                }

                boolean exited;
                try {
                    exited = ffmpeg.waitFor(FFMPEG_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exited = false;
                }
                if (!exited) {
                    ffmpeg.destroyForcibly();
                }

                boolean complete = keepOutput && exited && ffmpeg.exitValue() == 0 && !writeFailed
                        && samplesWritten >= expectedSamples * MIN_COMPLETE_RATIO;

                if (!complete) {
                    if (keepOutput && (!exited || ffmpeg.exitValue() != 0)) {
                        log.warn("ffmpeg не смог сохранить «{}» (код {}): {}",
                                track.getInfo().title, exited ? ffmpeg.exitValue() : "timeout", ffmpegOutput);
                    }
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                    }
                    return;
                }

                try {
                    Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
                    markArchived(track.getInfo().identifier);
                    log.info("Сохранено в коллекцию: {}", track.getInfo().title);
                } catch (IOException e) {
                    log.warn("Не удалось переместить архив «{}» в {}: {}", track.getInfo().title, finalFile, e.getMessage());
                }
            });
        }
    }
}
