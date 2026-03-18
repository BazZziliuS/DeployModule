package pro.gravit.launchermodules.deploymodule;

import pro.gravit.launcher.base.config.JsonConfigurable;

import java.nio.file.Path;

public class DeployModuleConfigurable extends JsonConfigurable<DeployModuleConfig> {

    private DeployModuleConfig config;

    public DeployModuleConfigurable(Path configPath) {
        super(DeployModuleConfig.class, configPath);
    }

    @Override
    public DeployModuleConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(DeployModuleConfig config) {
        this.config = config;
    }

    @Override
    public DeployModuleConfig getDefaultConfig() {
        return DeployModuleConfig.getDefault();
    }
}
