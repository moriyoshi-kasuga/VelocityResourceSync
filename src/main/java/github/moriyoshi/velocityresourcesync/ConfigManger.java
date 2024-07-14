package github.moriyoshi.velocityresourcesync;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.ProxyServer;
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
  @NotNull @Getter private ProxyServer server;
  @NotNull private String hash_command;

  public ConfigManger(Logger logger, Path dataDirectory) {
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  public void updateHash(String newHash) {
    hash = newHash;
    logger.info("updated hash to " + newHash);
  }

  public void load(ProxyServer server, CommentedConfigurationNode configurationNode) {
    this.server = server;
    val github = configurationNode.node("github");
    this.repo = github.node("repo").getString();
    this.branch = github.node("branch").getString();
    Preconditions.checkNotNull(repo, "repo can't be null");
    Preconditions.checkNotNull(branch, "branch can't be null");

    val webhook = configurationNode.node("webhook");
    this.port = webhook.node("port").getInt(-1);
    this.secret = webhook.node("secret").getString();
    Preconditions.checkState(port != -1, "port can't be null");
    Preconditions.checkNotNull(secret, "secret can't be null");
    Preconditions.checkState(secret != "your-webhook-secret", "please set your webhook secret");

    this.hash_command = configurationNode.node("hash_command").getString();
    Preconditions.checkNotNull(hash_command, "hash command can't be null");

    updateHash(Util.runAndGet(hash_command));

    val updateMessageRaw = configurationNode.node("update_message").getString();
    Preconditions.checkNotNull(updateMessageRaw, "updateMessage can't be null");
    this.updateMessage = MiniMessage.miniMessage().deserialize(updateMessageRaw);
    logger.info("update message: " + updateMessageRaw);
    logger.info("next line is the preview update message");
    server.getConsoleCommandSource().sendMessage(updateMessage);

    logger.info("listening on " + port + " for " + repo + " branch " + branch);
  }
}
