package github.moriyoshi.velocityresourcesync;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.Getter;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

@Getter
@Plugin(
    id = "velocityresourcesync",
    name = "VelocityResourceSync",
    version = BuildConstants.VERSION)
public class VelocityResourceSync {

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final ConfigManger configManger;

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    reloadConfig();
  }

  @Inject
  public VelocityResourceSync(
      ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
    this.configManger = new ConfigManger(logger, dataDirectory);

    saveDefaultConfig();
  }

  private void saveDefaultConfig() {
    try {
      Path dataDirectory = getDataDirectory();
      if (Files.notExists(dataDirectory)) {
        Files.createDirectories(dataDirectory);
      }

      Path configFile = dataDirectory.resolve("config.yml");
      if (Files.exists(configFile)) {
        return;
      }

      Class<?> thisClass = getClass();
      InputStream jarConfigStream = thisClass.getResourceAsStream("/config.yml");
      if (jarConfigStream == null) {
        throw new IOException("Missing file 'config.yml' in jar.");
      }

      Files.copy(jarConfigStream, configFile, StandardCopyOption.REPLACE_EXISTING);
      jarConfigStream.close();
    } catch (IOException ex) {
      Logger logger = getLogger();
      logger.error("An error occurred while saving the default configuration.", ex);
    }
  }

  private void reloadConfig() {
    try {
      Path dataDirectory = getDataDirectory();
      Path configFile = dataDirectory.resolve("config.yml");
      if (Files.notExists(configFile) || !Files.isRegularFile(configFile)) {
        throw new IOException("The 'config.yml' file does not exist.");
      }

      YamlConfigurationLoader.Builder builder = YamlConfigurationLoader.builder();
      builder.indent(2);
      builder.nodeStyle(null);
      builder.path(configFile);

      YamlConfigurationLoader loader = builder.build();
      CommentedConfigurationNode configurationNode = loader.load();

      ConfigManger configuration = getConfigManger();
      configuration.load(configurationNode);

    } catch (IOException ex) {
      Logger logger = getLogger();
      logger.error("An error occurred while reloading the configurations.", ex);
    }
  }
}
