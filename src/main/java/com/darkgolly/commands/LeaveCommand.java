package com.darkgolly.commands;

import com.darkgolly.audio.GuildMusicManager;
import com.darkgolly.audio.PlayerManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class LeaveCommand {

    public void execute(SlashCommandInteractionEvent event) {
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        musicManager.player.stopTrack();
        musicManager.scheduler.clearQueue();

        event.getGuild().getAudioManager().closeAudioConnection();
        event.reply("Отключился.").queue();
    }
}
