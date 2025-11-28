package com.darkgolly.commands;

import com.darkgolly.audio.GuildMusicManager;
import com.darkgolly.audio.PlayerManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PlayCommand {

    public void execute(MessageReceivedEvent event) {
        String[] parts = event.getMessage().getContentRaw().split(" ", 2);

        if (parts.length < 2) {
            event.getChannel().sendMessage("Укажите ссылку: !play <url>").queue();
            return;
        }

        String url = parts[1];

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        event.getGuild().getAudioManager().setSendingHandler(musicManager.getSendHandler());

        PlayerManager.getInstance().loadAndPlay(event.getChannel(), url);
    }
}
