package com.darkgolly.audio;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Настоящий e2e-тест MusicArchiver: гоняет реальный ffmpeg-процесс через настоящий PCM-поток
// (без моков), как это происходит при живом проигрывании трека в PlayerManager, и проверяет
// итоговый mp3-файл и индекс архива на диске. MUSIC_ARCHIVE_PATH на тестовый JVM выставляется
// из build.gradle (см. tasks.named('test')), т.к. MusicArchiver читает переменную окружения один
// раз при загрузке класса и внутри теста её уже не переопределить.
class MusicArchiverE2ETest {

    private static final AudioDataFormat FORMAT = StandardAudioDataFormats.DISCORD_PCM_S16_LE;
    // Достаточно сконструировать источник напрямую — MusicArchiver проверяет только
    // instanceof YoutubeAudioSourceManager, реальный HTTP ему для этого не нужен.
    private static final YoutubeAudioSourceManager YOUTUBE_SOURCE = new YoutubeAudioSourceManager(false, false, false);

    @BeforeAll
    static void ensureFfmpegAvailable() {
        String ffmpegExecutable = System.getenv().getOrDefault("FFMPEG_EXECUTABLE", "ffmpeg");
        try {
            Process probe = new ProcessBuilder(ffmpegExecutable, "-version").redirectErrorStream(true).start();
            boolean exited = probe.waitFor(10, TimeUnit.SECONDS);
            assumeTrue(exited && probe.exitValue() == 0, "ffmpeg недоступен в этом окружении — пропускаю e2e тест архивирования");
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "ffmpeg недоступен в этом окружении — пропускаю e2e тест архивирования");
        }
    }

    @AfterAll
    static void cleanupArchiveDir() throws IOException {
        String libraryPath = System.getenv("MUSIC_ARCHIVE_PATH");
        if (libraryPath == null) {
            return;
        }
        Path dir = Path.of(libraryPath);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @Test
    void archivesFullyPlayedYoutubeTrackDirectlyIntoLibraryRoot() throws Exception {
        String libraryPath = System.getenv("MUSIC_ARCHIVE_PATH");
        assumeTrue(libraryPath != null, "MUSIC_ARCHIVE_PATH не задан для тестового JVM");
        Path libraryRoot = Path.of(libraryPath);

        String identifier = "e2e-" + UUID.randomUUID();
        AudioTrackInfo info = new AudioTrackInfo(
                "E2E Test Title", "E2E Test Artist", 1000, identifier, false,
                "https://youtube.com/watch?v=" + identifier, null, null);
        FakeYoutubeTrack track = new FakeYoutubeTrack(info);
        RecordingDownstream downstream = new RecordingDownstream();

        List<AudioFilter> chain = MusicArchiver.getInstance().buildChain(track, FORMAT, downstream);
        assertEquals(1, chain.size(), "для нового YouTube-трека должен добавляться фильтр архивирования");
        FloatPcmAudioFilter recorder = (FloatPcmAudioFilter) chain.get(0);

        // Пишем ~1 секунду синусоиды 440 Гц одним куском — так же, как это делает LavaPlayer,
        // прогоняя PCM через цепочку фильтров перед кодированием в Opus для Discord.
        int sampleRate = FORMAT.sampleRate;
        int channels = FORMAT.channelCount;
        float[][] samples = new float[channels][sampleRate];
        for (int i = 0; i < sampleRate; i++) {
            float value = (float) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 0.2);
            for (int c = 0; c < channels; c++) {
                samples[c][i] = value;
            }
        }
        recorder.process(samples, 0, sampleRate);
        recorder.close();

        Path expectedFile = libraryRoot.resolve("E2E Test Artist - E2E Test Title.mp3");
        Path archiveIndex = libraryRoot.resolve(".archive-index.txt");
        waitUntil(() -> Files.isRegularFile(expectedFile) && indexContains(archiveIndex, identifier), 15);

        assertTrue(Files.isRegularFile(expectedFile), "mp3 должен появиться прямо в корне библиотеки");
        assertTrue(Files.size(expectedFile) > 0, "сохранённый файл не должен быть пустым");
        assertFalse(Files.isDirectory(libraryRoot.resolve("E2E Test Artist")),
                "не должно создаваться подпапки по имени исполнителя — Synology Music её не индексирует");
        assertTrue(downstream.receivedSamples.get(), "поток должен пройти дальше по цепочке фильтров без изменений");
        assertTrue(indexContains(archiveIndex, identifier), "идентификатор трека должен попасть в индекс архива");

        // Повторное построение цепочки для того же identifier не должно архивировать заново.
        List<AudioFilter> secondChain = MusicArchiver.getInstance().buildChain(track, FORMAT, downstream);
        assertTrue(secondChain.isEmpty(), "уже заархивированный трек не должен архивироваться повторно");
    }

    private static boolean indexContains(Path archiveIndex, String identifier) {
        if (!Files.isRegularFile(archiveIndex)) {
            return false;
        }
        try {
            return Files.readAllLines(archiveIndex).contains(identifier);
        } catch (IOException e) {
            return false;
        }
    }

    private interface Condition {
        boolean check();
    }

    private static void waitUntil(Condition condition, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.check()) {
                return;
            }
            Thread.sleep(200);
        }
    }

    private static class RecordingDownstream implements UniversalPcmAudioFilter {
        final AtomicBoolean receivedSamples = new AtomicBoolean(false);

        @Override
        public void process(float[][] samples, int offset, int length) {
            receivedSamples.set(true);
        }

        @Override
        public void process(short[] samples, int offset, int length) {
        }

        @Override
        public void process(ShortBuffer buffer) {
        }

        @Override
        public void process(short[][] samples, int offset, int length) {
        }

        @Override
        public void seekPerformed(long currentTime, long newTime) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    // Реализует AudioTrack напрямую (это интерфейс) — нужны только те методы, которые
    // действительно дёргает MusicArchiver: getInfo/getDuration/getSourceManager.
    private static class FakeYoutubeTrack implements AudioTrack {
        private final AudioTrackInfo info;

        FakeYoutubeTrack(AudioTrackInfo info) {
            this.info = info;
        }

        @Override
        public AudioTrackInfo getInfo() {
            return info;
        }

        @Override
        public String getIdentifier() {
            return info.identifier;
        }

        @Override
        public AudioTrackState getState() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isSeekable() {
            return false;
        }

        @Override
        public long getPosition() {
            return 0;
        }

        @Override
        public void setPosition(long position) {
        }

        @Override
        public void setMarker(TrackMarker marker) {
        }

        @Override
        public void addMarker(TrackMarker marker) {
        }

        @Override
        public void removeMarker(TrackMarker marker) {
        }

        @Override
        public long getDuration() {
            return info.length;
        }

        @Override
        public AudioTrack makeClone() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioSourceManager getSourceManager() {
            return YOUTUBE_SOURCE;
        }

        @Override
        public void setUserData(Object userData) {
        }

        @Override
        public Object getUserData() {
            return null;
        }

        @Override
        public <T> T getUserData(Class<T> klass) {
            return null;
        }
    }
}
