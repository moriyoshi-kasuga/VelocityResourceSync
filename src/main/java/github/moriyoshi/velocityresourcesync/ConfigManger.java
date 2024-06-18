package github.moriyoshi.velocityresourcesync;

import com.google.common.base.Preconditions;
import java.nio.file.Path;
import lombok.Getter;
import lombok.val;
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

  public ConfigManger(Logger logger, Path dataDirectory) {
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  public void load(CommentedConfigurationNode configurationNode) {
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
    logger.info("listening on " + port + " for " + repo + " branch " + branch);
  }
}
