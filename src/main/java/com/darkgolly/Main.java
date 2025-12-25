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

        commands = commands.addCommands(
                Commands.slash("say", "Заставляет бота сказать что-то")
                        .addOption(STRING, "query", "То что бот должен сказать", true),
                Commands.slash("leave", "Выгоняет бота из голосового канала")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.slash("join", "Присоединяет бота к голосовому каналу"),
                Commands.slash("skip", "Пропустить трек"),
                Commands.slash("stop", "Прекратить воспроизведение"),
                Commands.slash("play", "Включает музыку")
                        .addOption(STRING, "query", "URL или название трека", true)

        );

        commands.queue();
    }
}
