package com.darkgolly.commands;

import com.darkgolly.audio.GuildMusicManager;
import com.darkgolly.audio.PlayerManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class PlaylistCommand {

    public void execute(SlashCommandInteractionEvent event) {
        String url = event.getOption("query").getAsString();

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        event.getGuild().getAudioManager().setSendingHandler(musicManager.getSendHandler());
        musicManager.setChannel(event.getChannel());

        event.deferReply().queue();
        PlayerManager.getInstance().loadAndPlayPlaylist(event, url);
    }
}
