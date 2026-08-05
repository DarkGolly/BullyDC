package com.darkgolly.listeners;

import com.darkgolly.commands.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(CommandListener.class);

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        try {
            switch (event.getName()) {
                case "join" -> new JoinCommand().execute(event);
                case "leave" -> new LeaveCommand().execute(event);
                case "play" -> new PlayCommand().execute(event);
                case "playlist" -> new PlaylistCommand().execute(event);
                case "stop" -> new StopCommand().execute(event);
                case "skip" -> new SkipCommand().execute(event);
                case "say" -> new SayCommand().execute(event);
            }
        } catch (Exception e) {
            log.error("Ошибка выполнения команды /{}", event.getName(), e);
            String message = "❌ Произошла ошибка при выполнении команды.";
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).queue();
            } else {
                event.reply(message).queue();
            }
        }
    }
}
