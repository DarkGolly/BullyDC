package com.darkgolly.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue = new LinkedBlockingQueue<>();
    private InteractionHook interactionHook;
    // Discord-контекст
    private MessageChannel channel;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
    }

    public void setChannel(MessageChannel channel) {
        this.channel = channel;
    }

    public void queue(AudioTrack track) {
        if (player.getPlayingTrack() == null) {
            player.startTrack(track, false);
        } else {
            queue.offer(track);
        }
    }

    public void nextTrack() {
        player.startTrack(queue.poll(), false);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }

    public List<AudioTrack> getQueue() {
        return new ArrayList<>(queue);
    }

    public void setLastStatusMessage(InteractionHook interactionHook) {
        this.interactionHook = interactionHook;
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        if (exception.getMessage().toLowerCase().contains("age")) {
            interactionHook.editOriginal("⛔ Видео имеет возрастное ограничение (18+)").queue();
        } else {
            interactionHook.editOriginal("❌ Ошибка воспроизведения: " + exception.getMessage()).queue();
        }
    }
}
