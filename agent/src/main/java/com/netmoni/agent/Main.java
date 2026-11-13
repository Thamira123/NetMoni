package com.netmoni.agent;

import com.netmoni.agent.config.AgentConfig;
import com.netmoni.agent.service.AgentRuntime;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path configFile = args.length == 0 ? Path.of("config", "agent.properties") : Path.of(args[0]);
        AgentRuntime runtime = new AgentRuntime(AgentConfig.load(configFile));
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "netmoni-agent-shutdown"));
        runtime.start();
        try {
            Thread.currentThread().join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.info("Agent main thread interrupted");
        }
    }
}
