package com.darkgolly.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class LeaveCommand {

    public void execute(MessageReceivedEvent event) {
        event.getGuild().getAudioManager().closeAudioConnection();
        event.getChannel().sendMessage("Отключился.").queue();
    }
}
