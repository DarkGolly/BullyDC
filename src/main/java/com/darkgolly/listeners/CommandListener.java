package com.darkgolly.listeners;

import com.darkgolly.commands.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        switch (event.getName()) {
            case "join" -> new JoinCommand().execute(event);
            case "leave" -> new LeaveCommand().execute(event);
            case "play" -> new PlayCommand().execute(event);
            case "stop" -> new StopCommand().execute(event);
            case "skip" -> new SkipCommand().execute(event);
            case "say" -> {
                try {
                    new SayCommand().execute(event);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
