package com.darkgolly;

import com.darkgolly.listeners.CommandListener;
import com.darkgolly.util.Env;
import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.slf4j.Logger;


import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

public class Main {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Main.class);

    // При одновременном старте с прокси-контейнером (xray-proxy) его SOCKS-порт может
    // быть ещё не готов принимать соединения, из-за чего верификация токена падает
    // с ErrorResponseException/UnknownHostException. Ретраим вместо мгновенного краша.
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 2000;

    static void main() {
        AudioModuleConfig audioConfig = new AudioModuleConfig()
                .withDaveSessionFactory(new JDaveSessionFactory());

        JDA jda = buildWithRetry(audioConfig);
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
                        .addOption(STRING, "query", "URL или название трека", true),
                Commands.slash("playlist", "Ставит в очередь целый плейлист")
                        .addOption(STRING, "query", "URL плейлиста", true)

        );

        commands.queue();
    }

    private static JDA buildWithRetry(AudioModuleConfig audioConfig) {
        long delay = INITIAL_RETRY_DELAY_MS;
        for (int attempt = 1; ; attempt++) {
            try {
                return JDABuilder.createDefault(Env.get("BOT_TOKEN"))
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_VOICE_STATES)
                        .enableCache(CacheFlag.VOICE_STATE)
                        .setAudioModuleConfig(audioConfig)
                        .addEventListeners(new CommandListener())
                        .build();
            } catch (ErrorResponseException e) {
                if (attempt >= MAX_LOGIN_ATTEMPTS) {
                    throw e;
                }
                log.warn("Не удалось подключиться к Discord (попытка {}/{}), повтор через {} мс: {}",
                        attempt, MAX_LOGIN_ATTEMPTS, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                delay = Math.min(delay * 2, 30000);
            }
        }
    }
}
