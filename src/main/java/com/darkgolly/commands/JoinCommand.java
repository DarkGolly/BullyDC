package com.darkgolly.commands;

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class JoinCommand {

    public void execute(SlashCommandInteractionEvent event) {
        AudioChannel channel = event.getMember().getVoiceState().getChannel();

        if (channel == null) {
            event.getChannel().sendMessage("Вы не находитесь в голосовом канале!").queue();
            return;
        }

        event.getGuild().getAudioManager().openAudioConnection(channel);
        event.reply("Подключился к голосовому каналу!").queue();
    }
}
