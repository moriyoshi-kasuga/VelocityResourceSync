package github.moriyoshi.velocityresourcesync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.val;

public final class WebHookServer implements Runnable {

  private static class MyHandler implements HttpHandler {

    private final ConfigManger config;

    public MyHandler(ConfigManger config) {
      this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"POST".equals(exchange.getRequestMethod())) {
        sendResponse(exchange, 405, "Method Not Allowed");
        return;
      }

      Headers headers = exchange.getRequestHeaders();
      String event = headers.getFirst("x-github-event");
      if (!"push".equals(event)) {
        sendResponse(exchange, 412, "Only push event");
        return;
      }

      InputStream inputStream = exchange.getRequestBody();
      val buf = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      String payloadBody = buf.lines().collect(Collectors.joining("\n"));
      buf.close();

      try {
        String signatureHeader = headers.getFirst("x-hub-signature-256");
        verifySignature(payloadBody, config.getSecret(), signatureHeader);

        JsonObject json = JsonParser.parseString(payloadBody).getAsJsonObject();
        String repo = json.getAsJsonObject("repository").get("full_name").getAsString();
        String ref = json.get("ref").getAsString();

        if (!repo.equals(config.getRepo())) {
          sendResponse(exchange, 412, "Only repo " + config.getRepo());
          return;
        }

        if (!ref.equals("refs/heads/" + config.getBranch())) {
          sendResponse(exchange, 412, "Only main branch");
          return;
        }

        val hash = config.getHash();
        config.updateHash();
        if (hash.equals(config.getHash())) {
          sendResponse(exchange, 406, "No new content");
          return;
        }
        config.getServer().sendMessage(config.getUpdateMessage());
        sendResponse(exchange, 200, "{\"greeting\":\"Hello world\"}");
      } catch (Exception e) {
        sendResponse(exchange, 403, "Unauthorized: " + e.getMessage());
      }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response)
        throws IOException {
      exchange.sendResponseHeaders(statusCode, response.length());
      OutputStream os = exchange.getResponseBody();
      os.write(response.getBytes());
      os.close();
    }

    private void verifySignature(String payloadBody, String secretToken, String signatureHeader)
        throws Exception {
      if (signatureHeader == null || signatureHeader.isEmpty()) {
        throw new Exception("x-hub-signature-256 header is missing!");
      }

      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec secretKeySpec =
          new SecretKeySpec(secretToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      mac.init(secretKeySpec);
      byte[] hash = mac.doFinal(payloadBody.getBytes(StandardCharsets.UTF_8));
      String expectedSignature = "sha256=" + HexFormat.of().formatHex(hash).toLowerCase();

      if (!expectedSignature.equals(signatureHeader)) {
        throw new Exception("Request signatures didn't match!");
      }
    }
  }

  private HttpServer server;
  private final ConfigManger config;

  public WebHookServer(ConfigManger config) {
    this.config = config;
  }

  @Override
  public void run() {
    try {
      server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
    } catch (IOException e) {
      throw new RuntimeException("Failed to start resource pack host server", e);
    }
    server.createContext("/webhook", new MyHandler(config));
    System.out.println("WebHookServer started on port " + config.getPort());
    server.start();
  }

  public void stop() {
    server.stop(0);
  }
}
