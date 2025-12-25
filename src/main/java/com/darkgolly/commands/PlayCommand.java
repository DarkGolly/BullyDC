package com.darkgolly.commands;

import com.darkgolly.audio.GuildMusicManager;
import com.darkgolly.audio.PlayerManager;
import com.darkgolly.util.Spliter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;


public class PlayCommand {

    public void execute(SlashCommandInteractionEvent event) {
        String src = event.getOption("query").getAsString();
        String url = Spliter.split(src);

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        event.getGuild().getAudioManager().setSendingHandler(musicManager.getSendHandler());
        musicManager.setChannel(event.getChannel());
        PlayerManager.getInstance().loadAndPlay(event, url);
    }
}
