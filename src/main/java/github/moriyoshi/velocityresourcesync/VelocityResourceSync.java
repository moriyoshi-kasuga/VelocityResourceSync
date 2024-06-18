package github.moriyoshi.velocityresourcesync;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.UUID;
import lombok.Getter;
import lombok.val;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

@Getter
@Plugin(
    id = "velocityresourcesync",
    name = "VelocityResourceSync",
    authors = "moriyoshi-kasuga",
    url = "https://github.com/moriyoshi-kasuga/VelocityResourceSync",
    version = BuildConstants.VERSION)
public class VelocityResourceSync {

  private final UUID uuid = HashUUID.v5("VelocityResourceSync");

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final ConfigManger configManger;

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    loadConfig();
    server.getChannelRegistrar().register(IDENTIFIER);
  }

  public static final MinecraftChannelIdentifier IDENTIFIER =
      MinecraftChannelIdentifier.from("velocityresourcesync:main");

  @Subscribe
  public void onPluginMessageFromPlayer(PluginMessageEvent event) {
    if (!event.getIdentifier().equals(IDENTIFIER)) {
      return;
    }

    if (!(event.getSource() instanceof final ServerConnection conn)) {
      return;
    }

    val player = conn.getPlayer();

    ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
    val type = in.readUTF();
    if (type.equalsIgnoreCase("load")) {
      logger.info("loading resourcepacks for {}", player.getUsername());
      val resource =
          server
              .createResourcePackBuilder(configManger.getRepoFolder().getPath())
              .setHash(HexFormat.of().parseHex(configManger.getHash()))
              .setId(uuid)
              .build();
      // TODO: なぜかプレイヤーに送られない
      player.sendResourcePackOffer(resource);
    } else if (type.equalsIgnoreCase("unload")) {
      logger.info("unloading resourcepacks for {}", player.getUsername());
      player.removeResourcePacks(uuid);
    }
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
      if (Files.notExists(dataDirectory)) {
        Files.createDirectories(dataDirectory);
      }

      Path configFile = dataDirectory.resolve("config.yml");
      if (Files.exists(configFile)) {
        return;
      }

      InputStream jarConfigStream = getClass().getResourceAsStream("/config.yml");
      if (jarConfigStream == null) {
        throw new IOException("Missing file 'config.yml' in jar.");
      }

      Files.copy(jarConfigStream, configFile, StandardCopyOption.REPLACE_EXISTING);
      jarConfigStream.close();
    } catch (IOException ex) {
      logger.error("An error occurred while saving the default configuration.", ex);
    }
  }

  private void loadConfig() {
    try {
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

      configManger.load(server, configurationNode);

    } catch (IOException ex) {
      logger.error("An error occurred while reloading the configurations.", ex);
    }
  }
}
