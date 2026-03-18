package pro.gravit.launchermodules.deploymodule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.gravit.launcher.base.modules.LauncherInitContext;
import pro.gravit.launcher.base.modules.LauncherModule;
import pro.gravit.launcher.base.modules.LauncherModuleInfo;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.modules.events.LaunchServerFullInitEvent;
import pro.gravit.launchserver.socket.handlers.NettyWebAPIHandler;
import pro.gravit.utils.Version;

import java.io.IOException;
import java.nio.file.Path;

public class DeployModule extends LauncherModule {
    private static final Logger logger = LogManager.getLogger(DeployModule.class);

    private DeployModuleConfig config;

    public DeployModule() {
        super(new LauncherModuleInfo("DeployModule", new Version(1, 0, 0),
                new String[]{"LaunchServerCore"}));
    }

    @Override
    public void init(LauncherInitContext initContext) {
        registerEvent(this::finish, LaunchServerFullInitEvent.class);
    }

    private void finish(LaunchServerFullInitEvent event) {
        LaunchServer server = event.server;
        Path configDir = server.modulesManager.getConfigManager().getModuleConfigDir(moduleInfo.name);
        try {
            loadConfig(configDir);
        } catch (IOException e) {
            logger.error("Failed to load DeployModule config", e);
            return;
        }
        if (!config.enabled) {
            logger.info("DeployModule is disabled in config");
            return;
        }
        NettyWebAPIHandler.addNewSeverlet("upload/profile",
                new UploadProfileHandler(server, config));
        NettyWebAPIHandler.addNewSeverlet("upload/client",
                new UploadClientHandler(server, config));
        logger.info("DeployModule enabled. {} tokens configured", config.tokens.size());
    }

    private void loadConfig(Path configDir) throws IOException {
        Path configFile = configDir.resolve("Config.json");
        DeployModuleConfigurable configurable = new DeployModuleConfigurable(configFile);
        configurable.generateConfigIfNotExists();
        configurable.loadConfig();
        config = configurable.getConfig();
    }
}
