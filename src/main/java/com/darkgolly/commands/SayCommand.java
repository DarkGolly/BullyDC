package com.darkgolly.commands;

import com.darkgolly.audio.PlayerManager;
import com.darkgolly.audio.TTSService;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.File;
import java.util.Objects;

public class SayCommand {

    private final TTSService tts = new TTSService();

    public void execute(MessageReceivedEvent event) throws Exception {
        String text = event.getMessage().getContentRaw().replace("!say ", "");
        System.out.println(text);

        AudioChannel vc = Objects.requireNonNull(event.getMember()).getVoiceState().getChannel();
        File mp3 = tts.generateSpeech(text);


        event.getGuild().getAudioManager().openAudioConnection(vc);

        PlayerManager.getInstance().playVoice(event.getChannel(), mp3.getAbsolutePath());
        event.getMessage().reply("🔊 Произношу").queue();

    }
}
