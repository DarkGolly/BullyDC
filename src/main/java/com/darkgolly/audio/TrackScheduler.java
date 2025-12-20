package com.darkgolly.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
    private Message lastStatusMessage;
    // Discord-контекст
    private MessageChannel channel;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
    }
    public void setLastStatusMessage(Message message) {
        this.lastStatusMessage = message;
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

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        if (lastStatusMessage != null) {
            lastStatusMessage.delete().queue(
                    success -> {},
                    error -> log.warn("Не удалось удалить сообщение", error)
            );
            lastStatusMessage = null;
        }

        if (exception.getMessage().toLowerCase().contains("age")) {
            channel.sendMessage("⛔ Видео имеет возрастное ограничение (18+)").queue();
        } else {
            channel.sendMessage("❌ Ошибка воспроизведения: " + exception.getMessage()).queue();
        }

        player.stopTrack();
    }


    public List<AudioTrack> getQueue() {
        return new ArrayList<>(queue);
    }
}
