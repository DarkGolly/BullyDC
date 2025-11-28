package com.darkgolly;

import com.darkgolly.listeners.CommandListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;

public class Main {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        JDABuilder.createDefault(System.getenv("BOT_TOKEN"))
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new CommandListener())
                .build();
        log.info("Bot started");
    }
}
