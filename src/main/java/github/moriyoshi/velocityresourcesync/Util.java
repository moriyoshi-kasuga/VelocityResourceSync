package github.moriyoshi.velocityresourcesync;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import lombok.val;
import org.slf4j.Logger;

public final class Util {
  private Util() {}

  public static int run(Logger logger, String command) {
    return run(logger, command, null, null);
  }

  public static int run(Logger logger, String command, String[] envp) {
    return run(logger, command, envp, null);
  }

  public static int run(Logger logger, String command, File dir) {
    return run(logger, command, null, dir);
  }

  public static int run(Logger logger, String command, String[] envp, File dir) {
    try {
      val p = Runtime.getRuntime().exec(new String[] {"bash", "-c", command}, envp, dir);

      print(logger, p.getInputStream());
      print(logger, p.getErrorStream());
      p.waitFor();

      return p.exitValue();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to run command of " + command, e);
    }
  }

  public static void print(Logger logger, InputStream input) {
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                BufferedReader bf = new BufferedReader(new InputStreamReader(input));
                String line = null;
                try {
                  while ((line = bf.readLine()) != null) {
                    logger.info(line);
                  }
                } catch (IOException e) {
                  System.out.println("IOException");
                }
              }
            })
        .start();
  }
}
