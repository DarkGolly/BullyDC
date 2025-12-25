package com.darkgolly.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class LeaveCommand {

    public void execute(SlashCommandInteractionEvent event) {
        event.getGuild().getAudioManager().closeAudioConnection();
        event.reply("Отключился.").queue();
    }
}
