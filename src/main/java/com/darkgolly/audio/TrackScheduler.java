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
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    public void nextTrack() {
        AudioTrack next = queue.poll();
        if (next != null) {
            player.startTrack(next, false);
        } else {
            player.stopTrack();
        }
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

    public void clearQueue() {
        queue.clear();
    }

    public void setLastStatusMessage(InteractionHook interactionHook) {
        this.interactionHook = interactionHook;
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        log.error("Ошибка воспроизведения трека: {}", exception.getMessage(), exception);

        if (interactionHook == null) {
            return;
        }

        interactionHook.editOriginal(friendlyErrorMessage(exception.getMessage())).queue();
    }

    // Пользователю в Discord не должен улетать сырой текст исключения (у некоторых ошибок
    // YouTube-плеера он содержит перечисление попыток по всем клиентам и похож на кусок лога).
    private static String friendlyErrorMessage(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("age")) {
            return "⛔ Видео имеет возрастное ограничение (18+)";
        }
        if (lower.contains("login") || lower.contains("sign in")) {
            return "🔒 Видео недоступно без входа в аккаунт YouTube — бот не может его воспроизвести";
        }
        return "❌ Не удалось воспроизвести трек (подробности в логах бота)";
    }
}
