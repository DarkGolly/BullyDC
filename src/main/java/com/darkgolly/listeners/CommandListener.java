package com.darkgolly.listeners;

import com.darkgolly.commands.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CommandListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String msg = event.getMessage().getContentRaw();

        if (msg.startsWith("!join")) new JoinCommand().execute(event);
        else if (msg.startsWith("!leave")) new LeaveCommand().execute(event);
        else if (msg.startsWith("!play")) new PlayCommand().execute(event);
        else if (msg.startsWith("!stop")) new StopCommand().execute(event);
        else if (msg.startsWith("!skip")) new SkipCommand().execute(event);
        else if (msg.startsWith("!say")) {
            try {
                new SayCommand().execute(event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
