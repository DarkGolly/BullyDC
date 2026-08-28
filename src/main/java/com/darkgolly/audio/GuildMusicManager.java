package com.darkgolly.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class GuildMusicManager {

    public final AudioPlayer player;
    public final TrackScheduler scheduler;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player);
        this.player.addListener(this.scheduler);
        // Архивация теперь пишет тот же PCM-поток, что уже играет в Discord (см. MusicArchiver),
        // а не качает трек заново через yt-dlp.
        this.player.setFilterFactory(MusicArchiver.getInstance());
    }

    public AudioSendHandler getSendHandler() {
        return new AudioPlayerSendHandler(player);
    }

    public void setChannel(MessageChannel channel) {
        this.scheduler.setChannel(channel);
    }
}
