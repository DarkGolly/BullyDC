package com.darkgolly.commands;

import com.darkgolly.audio.GuildMusicManager;
import com.darkgolly.audio.PlayerManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Objects;

public class JoinCommand {

    public void execute(SlashCommandInteractionEvent event) {
        AudioChannel channel = Objects.requireNonNull(Objects.requireNonNull(event.getMember()).getVoiceState()).getChannel();

        if (channel == null) {
            event.getChannel().sendMessage("Вы не находитесь в голосовом канале!").queue();
            return;
        }

        Guild guild = Objects.requireNonNull(event.getGuild());
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(guild);
        if (guild.getAudioManager().getSendingHandler() == null) {
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        }

        guild.getAudioManager().openAudioConnection(channel);
        event.reply("Подключился к голосовому каналу!").queue();
    }
}
