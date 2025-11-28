package com.darkgolly.commands;

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class JoinCommand {

    public void execute(MessageReceivedEvent event) {
        AudioChannel channel = event.getMember().getVoiceState().getChannel();

        if (channel == null) {
            event.getChannel().sendMessage("Вы не находитесь в голосовом канале!").queue();
            return;
        }

        event.getGuild().getAudioManager().openAudioConnection(channel);
        event.getChannel().sendMessage("Подключился к голосовому каналу!").queue();
    }
}
