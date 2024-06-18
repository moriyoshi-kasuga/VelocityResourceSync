package github.moriyoshi.velocityresourcesync;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

@Getter
public final class ConfigManger {
  private final Logger logger;
  private final Path dataDirectory;

  @NotNull @Getter private String repo;
  @NotNull @Getter private String branch;
  @NotNull @Getter private int port;
  @NotNull @Getter private String secret;
  @NotNull @Getter private Component updateMessage;
  @NotNull @Getter private String hash;

  public ConfigManger(Logger logger, Path dataDirectory) {
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  public void load(ProxyServer server, CommentedConfigurationNode configurationNode) {
    val github = configurationNode.node("github");
    this.repo = github.node("repo").getString();
    this.branch = github.node("branch").getString();
    Preconditions.checkNotNull(repo, "repo can't be null");
    Preconditions.checkNotNull(branch, "branch can't be null");

    Path repoDir = dataDirectory.resolve(repo.split("/")[1]);
    if (Files.notExists(repoDir)) {
      logger.info("cloning " + repo);
      Util.run(
          logger,
          "git clone -b "
              + branch
              + " https://github.com/"
              + repo
              + " "
              + repoDir.toFile().getAbsolutePath());
      logger.info("cloned " + repo);
    }
    File repoFolder = repoDir.toFile();
    Util.run(logger, "git pull", repoFolder);

    val webhook = configurationNode.node("webhook");
    this.port = webhook.node("port").getInt(-1);
    this.secret = webhook.node("secret").getString();
    Preconditions.checkState(port != -1, "port can't be null");
    Preconditions.checkNotNull(secret, "secret can't be null");
    Preconditions.checkState(secret != "your-webhook-secret", "please set your webhook secret");

    val updateMessageRaw = configurationNode.node("update_message").getString();
    Preconditions.checkNotNull(updateMessageRaw, "updateMessage can't be null");
    this.updateMessage = MiniMessage.miniMessage().deserialize(updateMessageRaw);
    logger.info("update message: " + updateMessageRaw);
    logger.info("next line is the preview update message");
    server.getConsoleCommandSource().sendMessage(updateMessage);

    logger.info("listening on " + port + " for " + repo + " branch " + branch);
  }
}
