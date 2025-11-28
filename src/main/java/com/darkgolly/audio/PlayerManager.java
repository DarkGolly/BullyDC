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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PlayerManager {
    private static final Logger log = LoggerFactory.getLogger(PlayerManager.class);

    private static PlayerManager INSTANCE;
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();

        dev.lavalink.youtube.YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager();
        playerManager.registerSourceManager(yt);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        playerManager.registerSourceManager(new HttpAudioSourceManager());
    }


    public static synchronized PlayerManager getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerManager();
        return INSTANCE;
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        return musicManagers.computeIfAbsent(guild.getIdLong(),
                id -> new GuildMusicManager(playerManager));
    }

    public void loadAndPlay(MessageChannel channel, String trackUrl) {
        GuildMusicManager musicManager = getMusicManager(((GuildMessageChannel) channel).getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                addQueueAndPlay(track, musicManager, channel);
                log.info("Трек загружен в аудио плеер.");
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getTracks().get(0);
                addQueueAndPlay(track, musicManager, channel);
                log.info("Трек загружен из плейлиста.");
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Не найдено.").queue();
                log.info("Не найдено.");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Ошибка загрузки трека: " + exception.getMessage()).queue();
                log.error("Ошибка загрузки трека: {}", exception.getMessage(), exception);
            }
        });
    }

    private void addQueueAndPlay(AudioTrack track, GuildMusicManager musicManager, MessageChannel channel) {
        if (musicManager.scheduler.getQueue().isEmpty() && musicManager.player.getPlayingTrack() == null) {
            musicManager.scheduler.queue(track);
            channel.sendMessage("▶ Играю: " + track.getInfo().title).queue();
            log.info("Играю: {}", track.getInfo().title);
        }else {
            musicManager.scheduler.queue(track);
            channel.sendMessage("🎶 Добавлено в очередь: `" + track.getInfo().title + "`").queue();
            log.info("Добавлено в очередь: {}", track.getInfo().title);
        }
    }
}
