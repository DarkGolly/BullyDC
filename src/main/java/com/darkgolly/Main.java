package com.darkgolly;

import com.darkgolly.listeners.CommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.slf4j.Logger;


import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public class Main {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        JDA jda = JDABuilder.createDefault(System.getenv("BOT_TOKEN"))
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new CommandListener())
                .build();
        log.info("Bot started");

        CommandListUpdateAction commands = jda.updateCommands();

        commands.addCommands(
                Commands.slash("say", "Makes the bot say what you tell it to")
                        .addOption(STRING, "content", "What the bot should say", true), // Accepting a user input
                Commands.slash("leave", "Makes the bot leave the server")
                        .setContexts(InteractionContextType.GUILD) // this doesn't make sense in DMs
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED), // only admins should be able to use this command.
                Commands.slash("join", "Makes the bot join the server"),
                Commands.slash("play", "Play a song")
        );

        commands.queue();
    }

}
