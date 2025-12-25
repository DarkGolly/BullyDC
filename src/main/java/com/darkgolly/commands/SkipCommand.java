package com.darkgolly.commands;

import com.darkgolly.audio.GuildMusicManager;
import com.darkgolly.audio.PlayerManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class SkipCommand{

    public void execute(SlashCommandInteractionEvent event){
        GuildMusicManager musicManager = PlayerManager.getInstance()
                .getMusicManager(event.getGuild());

        musicManager.scheduler.nextTrack();

        event.reply("⏭ Трек пропущен!").queue();
    }
}
