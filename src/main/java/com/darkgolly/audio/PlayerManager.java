package com.darkgolly.audio;

import com.darkgolly.util.Env;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PlayerManager {
    private static final Logger log = LoggerFactory.getLogger(PlayerManager.class);

    private static PlayerManager INSTANCE;
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public PlayerManager(){
        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();

        // Аудио реально льётся через xray-proxy (см. -DsocksProxyHost в Dockerfile), а не напрямую —
        // при просадках скорости/задержках на прокси штатного 5-секундного буфера LavaPlayer не
        // хватает, и воспроизведение обрывается паузами. Держим больший запас на такой случай.
        playerManager.setFrameBufferDuration(Integer.parseInt(Env.get("AUDIO_BUFFER_MS", "15000")));

        // Локальный разбор плеерного скрипта YouTube (извлечение sig-функции) регулярно ломается
        // из-за обфускации на их стороне — переключаем расшифровку подписи на внешний сервис
        // yt-cipher (https://github.com/kikkia/yt-cipher), который обновляется отдельно от нашей
        // зависимости. По умолчанию — публичный инстанс без пароля (лимит 10 запросов/сек, для
        // личного бота с запасом хватает); можно переопределить на свой через .env.
        YoutubeSourceOptions options = new YoutubeSourceOptions().setRemoteCipher(
                Env.get("YOUTUBE_CIPHER_URL", "https://cipher.kikkia.dev/"),
                Env.get("YOUTUBE_CIPHER_PASSWORD", ""),
                "BullyDC-discord-bot"
        );

        // Из клиентов по умолчанию (ANDROID_VR/WEB/WEB_EMBEDDED_PLAYER) ни один не поддерживает
        // OAuth — токен просто не к чему было бы применить. TV — единственный OAuth-совместимый
        // клиент, добавляем его первым, остальные оставляем как фолбэк на случай, если OAuth выключен.
        dev.lavalink.youtube.YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(
                options, new Tv(), new AndroidVr(), new Web(), new WebEmbedded()
        );
        configureOauth(yt);
        playerManager.registerSourceManager(yt);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        playerManager.registerSourceManager(new HttpAudioSourceManager());
    }


    // YouTube требует авторизации почти для всех видео без входа в аккаунт (антибот-защита).
    // Явный opt-in через .env, т.к. это привязка Google-аккаунта — разработчики youtube-source
    // прямо предупреждают использовать запасной (burner) аккаунт, не основной.
    //   YOUTUBE_OAUTH_REFRESH_TOKEN — уже полученный refresh-токен, применяется сразу.
    //   YOUTUBE_OAUTH_ENABLED=true  — первый запуск: в логах появится ссылка+код для входа,
    //                                 после успешного входа туда же будет выведен refresh-токен,
    //                                 который нужно сохранить в YOUTUBE_OAUTH_REFRESH_TOKEN.
    private static void configureOauth(YoutubeAudioSourceManager yt) {
        String refreshToken = Env.get("YOUTUBE_OAUTH_REFRESH_TOKEN");
        if (refreshToken != null && !refreshToken.isBlank()) {
            yt.useOauth2(refreshToken, true);
            log.info("YouTube OAuth включён (используется сохранённый refresh-токен)");
            return;
        }

        if (Boolean.parseBoolean(Env.get("YOUTUBE_OAUTH_ENABLED", "false"))) {
            log.info("YouTube OAuth: refresh-токен не задан, запускаю флоу входа — ссылка и код появятся в логах ниже");
            yt.useOauth2(null, false);
        }
    }

    public static synchronized PlayerManager getInstance(){
        if (INSTANCE == null) INSTANCE = new PlayerManager();
        return INSTANCE;
    }

    public GuildMusicManager getMusicManager(Guild guild){
        return musicManagers.computeIfAbsent(guild.getIdLong(),
                id -> new GuildMusicManager(playerManager));
    }

    public void playVoice(SlashCommandInteractionEvent event, String mp3Path){
        if (!(event.getChannel() instanceof GuildMessageChannel)){
            log.warn("Канал не является GuildMessageChannel.");
            return;
        }

        Guild guild = ((GuildMessageChannel) event.getChannel()).getGuild();
        GuildMusicManager musicManager = getMusicManager(guild);

        if (guild.getAudioManager().getSendingHandler() == null){
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        }

        playerManager.loadItemOrdered(musicManager, mp3Path, new AudioLoadResultHandler(){
            @Override
            public void trackLoaded(AudioTrack track){
                musicManager.scheduler.clearQueue();
                if (musicManager.player.getPlayingTrack() != null){
                    musicManager.player.stopTrack();
                }

                musicManager.scheduler.queue(track);
                log.info("Воспроизводим голосовое сообщение: {}", track.getInfo().title);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist){
                AudioTrack track = playlist.getTracks().get(0);
                trackLoaded(track);
            }

            @Override
            public void noMatches(){
                event.getHook().sendMessage("❌ Не удалось найти или воспроизвести аудиофайл.").queue();
                log.warn("Аудиофайл не найден: {}", mp3Path);
            }

            @Override
            public void loadFailed(FriendlyException exception){
                event.getHook().sendMessage("❌ Ошибка воспроизведения аудиофайла (подробности в логах бота)").queue();
                log.error("Ошибка загрузки аудиофайла {}: {}", mp3Path, exception.getMessage(), exception);
            }
        });
    }

    public void loadAndPlay(SlashCommandInteractionEvent event, String trackUrl) {
        GuildMusicManager musicManager = getMusicManager(Objects.requireNonNull(event.getGuild()));

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                String response = addQueueAndPlay(track, musicManager);
                event.getHook().sendMessage(response).queue();
                musicManager.scheduler.setLastStatusMessage(event.getHook());
                log.info(response);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getTracks().get(0);
                String response = addQueueAndPlay(track, musicManager);
                event.getHook().sendMessage(response).queue();
                musicManager.scheduler.setLastStatusMessage(event.getHook());
                log.info(response);
            }

            @Override
            public void noMatches() {
                event.getHook().sendMessage("Не найдено.").queue();
                log.info("Не найдено.");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getHook().sendMessage("❌ Не удалось загрузить трек (подробности в логах бота)").queue();
                log.error("Ошибка загрузки трека: {}", exception.getMessage(), exception);
            }
        });
    }

    public void loadAndPlayPlaylist(SlashCommandInteractionEvent event, String trackUrl) {
        GuildMusicManager musicManager = getMusicManager(Objects.requireNonNull(event.getGuild()));

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                String response = addQueueAndPlay(track, musicManager);
                event.getHook().sendMessage(response).queue();
                musicManager.scheduler.setLastStatusMessage(event.getHook());
                log.info(response);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                for (AudioTrack track : playlist.getTracks()) {
                    musicManager.scheduler.queue(track);
                }
                String response = "🎶 Добавлен плейлист `" + playlist.getName() + "` (" + playlist.getTracks().size() + " треков)";
                event.getHook().sendMessage(response).queue();
                musicManager.scheduler.setLastStatusMessage(event.getHook());
                log.info(response);
            }

            @Override
            public void noMatches() {
                event.getHook().sendMessage("Не найдено.").queue();
                log.info("Не найдено.");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getHook().sendMessage("❌ Не удалось загрузить плейлист (подробности в логах бота)").queue();
                log.error("Ошибка загрузки плейлиста: {}", exception.getMessage(), exception);
            }
        });
    }

    private String addQueueAndPlay(AudioTrack track, GuildMusicManager musicManager) {
        if (musicManager.scheduler.getQueue().isEmpty() && musicManager.player.getPlayingTrack() == null) {
            musicManager.scheduler.queue(track);
            return "▶ Играю: " + track.getInfo().title;
        }else {
            musicManager.scheduler.queue(track);
            return "🎶 Добавлено в очередь: `" + track.getInfo().title + "`";
        }
    }
}
