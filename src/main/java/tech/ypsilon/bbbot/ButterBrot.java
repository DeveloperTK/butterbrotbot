package tech.ypsilon.bbbot;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sedmelluq.discord.lavaplayer.tools.PlayerLibrary;
import lombok.Getter;
import net.dv8tion.jda.api.JDAInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.ypsilon.bbbot.config.ButterbrotConfig;
import tech.ypsilon.bbbot.config.DefaultConfigFactory;
import tech.ypsilon.bbbot.console.ConsoleController;
import tech.ypsilon.bbbot.database.MongoController;
import tech.ypsilon.bbbot.discord.*;
import tech.ypsilon.bbbot.discord.command.text.CreateInviteCommand;
import tech.ypsilon.bbbot.discord.command.text.StudiengangCommand;
import tech.ypsilon.bbbot.discord.command.text.VerifyCommand;
import tech.ypsilon.bbbot.stats.StatsController;
import tech.ypsilon.bbbot.util.LogUtil;
import tech.ypsilon.bbbot.voice.AudioController;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Main class from the ButterBrot Bot
 *
 * @author Julian
 * @author Niklas
 * @author Gregyyy
 * @author Julian²
 * @author Christian
 *
 * @version 1.1
 */
public class ButterBrot {

    public static final Logger LOGGER = LoggerFactory.getLogger("tech.ypsilon.bbbot");

    private static final int MISCONFIGURATION_EXIT_CODE = 78;

    public static boolean DEBUG_MODE;

    private static boolean MISSING_CONFIGURATION;
    private static @Deprecated(forRemoval = true) ButterbrotConfig STATIC_CONFIG;

    private final @Getter ButterbrotConfig config;

    private final @Getter DiscordController discordController;
    private final @Getter StatsController statsController;
    private final @Getter @Nullable MongoController mongoController;
    private final @Getter TextCommandManager textCommandManager;
    private final @Getter AudioController audioController;
    private final @Getter ListenerController listenerController;
    private final @Getter SlashCommandController slashCommandController;
    private final @Getter ConsoleController consoleController;
    private final @Getter ServiceController serviceController;

    public ButterBrot(ButterbrotConfig config) throws Exception {
        this.config = config;

        this.discordController = new DiscordController(this);
        this.statsController = new StatsController(this);
        this.mongoController = new MongoController(this);
        this.textCommandManager = new TextCommandManager(this);
        this.audioController = new AudioController(this);
        this.listenerController = new ListenerController(this);
        this.slashCommandController = new SlashCommandController(this);
        this.consoleController = new ConsoleController(this);
        this.serviceController = new ServiceController(this);

        LOGGER.info("Starting pre-init state");
        preInit();
        LOGGER.info("Passed pre-init state");

        addShutdownHook();

        LOGGER.info("Starting init state");
        init();
        LOGGER.info("Passed init state");

        LOGGER.info("Starting post-init state");
        postInit();
        LOGGER.info("Passed post-init state");

        LOGGER.info("Startup complete");
    }

    private void preInit() {
        statsController.safeInit();

    }

    private void init() throws InterruptedException {
        discordController.safeInit();
        textCommandManager.safeInit();
        audioController.safeInit();
        slashCommandController.safeInit();
        listenerController.safeInit();
        discordController.getJda().awaitReady();
    }

    private void postInit() {
        LogUtil.init();

        if (mongoController == null) throw new IllegalStateException("MongoController is null!");
        if(!ButterBrot.DEBUG_MODE) {
            mongoController.safeInit();
            TextCommandManager.getInstance().registerFunction(new StudiengangCommand());
            TextCommandManager.getInstance().registerFunction(new CreateInviteCommand());
            TextCommandManager.getInstance().registerFunction(new VerifyCommand());
        } else {
            mongoController.disable();
        }

        consoleController.safeInit();
        serviceController.safeInit();
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopBot(false)));
    }

    static boolean shutdown = false;
    public static void stopBot(boolean systemExit) {
        if (shutdown) return;

        DiscordController.getJDAStatic().shutdown();
        shutdown = true;

        LOGGER.info("Good Bye! :c");
        if (systemExit) System.exit(0);
    }

    @Deprecated(forRemoval = true)
    public static ButterbrotConfig getConfigStatic() {
        LOGGER.error("\u001b[33m", new RuntimeException("### WARNING: DEPRECATED STATIC ACCESS "
                + "TO ButterBrot#getConfigStatic()"));
        LOGGER.warn("###\u001b[0m");
        return STATIC_CONFIG;
    }

    /**
     * Program entry point
     *
     * @param args command line args
     * @throws Exception when something goes wrong during initialization
     */
    public static void main(String[] args) throws Exception {
        System.out.println("__________        __    __              __________                __   \n" +
                "\\______   \\__ ___/  |__/  |_  __________\\______   \\_______  _____/  |_ \n" +
                " |    |  _/  |  \\   __\\   __\\/ __ \\_  __ \\    |  _/\\_  __ \\/  _ \\   __\\\n" +
                " |    |   \\  |  /|  |  |  | \\  ___/|  | \\/    |   \\ |  | \\(  <_> )  |  \n" +
                " |______  /____/ |__|  |__|  \\___  >__|  |______  / |__|   \\____/|__|  \n" +
                "        \\/                       \\/             \\/                     ");
        System.out.println(BotInfo.NAME + " v" + BotInfo.VERSION);
        System.out.println("JDA v" + JDAInfo.VERSION + " | LavaPlayer v" + PlayerLibrary.VERSION);

        DEBUG_MODE = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("--debug"));

        final File dataDirectory = initAndGetDataDirectory();
        ButterbrotConfig butterbrotConfig;

        try {
            butterbrotConfig = loadConfiguration(
                    new File(dataDirectory, "config.yml"),
                    ButterbrotConfig.class,
                    new YAMLFactory()
            );
            STATIC_CONFIG = butterbrotConfig; // TODO: remove this
        } catch (ReflectiveOperationException exception) {
            LOGGER.error("Reflective Error while initializing configuration", exception);
            System.exit(MISCONFIGURATION_EXIT_CODE);
            return;
        }

        if (MISSING_CONFIGURATION) {
            System.exit(MISCONFIGURATION_EXIT_CODE);
        }

        new ButterBrot(butterbrotConfig);
    }

    private static File initAndGetDataDirectory() {
        File data = new File("data");

        if (!data.exists()) {
            LOGGER.info("Data directory could not be found, trying to create a new one.");
            if (data.mkdir()) {
                LOGGER.info("Successfully created data directory.");
            } else {
                throw new IllegalStateException("Could not create data directory! Path: " + data.getAbsolutePath());
            }
        }

        File settingsFile = new File(data, "settings.yml");

        if (settingsFile.exists() && !new File(data, "butterbrot.yml").exists()) {
            new RuntimeException("WARNING: Please migrate from settings.yml to butterbrot.yml !!").printStackTrace();
        }

        return data;
    }

    @SuppressWarnings({"unchecked", "SameParameterValue"})
    private static <T extends DefaultConfigFactory> T loadConfiguration(File configFile, Class<T> configClass, JsonFactory factory)
            throws IOException, ReflectiveOperationException {

        ObjectMapper objectMapper = new ObjectMapper(factory);
        T config;

        try {
            LOGGER.info("Loading configuration {}, mapped by {}", configFile.getName(), configClass.getName());
            config = objectMapper.readValue(configFile, configClass);
            objectMapper.writeValue(configFile, config);
        } catch (FileNotFoundException exception) {
            LOGGER.info("Creating empty configuration...");
            config = (T) configClass.getDeclaredMethod("createDefault").invoke(null);
            objectMapper.writeValue(configFile, config);
            LOGGER.warn("Please configure file {}, " +
                    "the bot will stop after initializing all configs...", configFile.getName());
            MISSING_CONFIGURATION = true;
        } catch (IOException exception) {
            config = null;
            exception.printStackTrace();
            LOGGER.warn("Please configure file {}, " +
                    "the bot will stop after initializing all configs...", configFile.getName());
            System.exit(MISCONFIGURATION_EXIT_CODE);
        }

        T defaultConfig = (T) configClass.getDeclaredMethod("createDefault").invoke(null);

        if (defaultConfig.equals(config)) {
            LOGGER.warn("\u001b[33mConfig file {} is still the default config " +
                    "and might need to be configured!\u001b[0m", configFile.getName());
        }

        return config;
    }

}
