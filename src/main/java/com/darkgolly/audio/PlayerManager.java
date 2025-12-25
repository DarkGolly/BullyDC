package com.darkgolly.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
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

        dev.lavalink.youtube.YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager();
        playerManager.registerSourceManager(yt);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        playerManager.registerSourceManager(new HttpAudioSourceManager());
    }


    public static synchronized PlayerManager getInstance(){
        if (INSTANCE == null) INSTANCE = new PlayerManager();
        return INSTANCE;
    }

    public GuildMusicManager getMusicManager(Guild guild){
        return musicManagers.computeIfAbsent(guild.getIdLong(),
                id -> new GuildMusicManager(playerManager, guild));
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
                musicManager.scheduler.getQueue().clear();
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
                event.reply("❌ Не удалось найти или воспроизвести аудиофайл.").queue();
                log.warn("Аудиофайл не найден: {}", mp3Path);
            }

            @Override
            public void loadFailed(FriendlyException exception){
                event.reply("❌ Ошибка воспроизведения аудиофайла: " + exception.getMessage()).queue();
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
                event.reply(response).queue(musicManager.scheduler::setLastStatusMessage);
                log.info(response);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getTracks().get(0);
                String response = addQueueAndPlay(track, musicManager);
                event.reply(response).queue(musicManager.scheduler::setLastStatusMessage);
                log.info(response);
            }

            @Override
            public void noMatches() {
                event.reply("Не найдено.").queue();
                log.info("Не найдено.");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.reply("Ошибка загрузки трека: " + exception.getMessage()).queue();
                log.error("Ошибка загрузки трека: {}", exception.getMessage(), exception);
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
