package com.darkgolly.commands;

import com.darkgolly.audio.PlayerManager;
import com.darkgolly.audio.TTSService;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.File;
import java.util.Objects;

public class SayCommand {

    private final TTSService tts = new TTSService();

    public void execute(SlashCommandInteractionEvent event) throws Exception {
        String text = event.getOption("query").getAsString();

        AudioChannel vc = Objects.requireNonNull(event.getMember()).getVoiceState().getChannel();
        if (vc == null) {
            event.reply("Вы не находитесь в голосовом канале!").queue();
            return;
        }

        event.deferReply().queue();

        File mp3 = tts.generateSpeech(text);

        event.getGuild().getAudioManager().openAudioConnection(vc);

        PlayerManager.getInstance().playVoice(event, mp3.getAbsolutePath());
        event.getHook().sendMessage("🔊 Произношу").queue();

    }
}
