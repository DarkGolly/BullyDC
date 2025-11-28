package com.darkgolly.commands;

import com.darkgolly.audio.GuildMusicManager;
import com.darkgolly.audio.PlayerManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SkipCommand {
    public void execute(MessageReceivedEvent event) {
        GuildMusicManager musicManager = PlayerManager.getInstance()
                .getMusicManager(event.getGuild());

        musicManager.scheduler.nextTrack();

        event.getMessage().reply("⏭ Трек пропущен!").queue();
    }
}
