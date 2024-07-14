package github.moriyoshi.velocityresourcesync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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

      InputStream is = exchange.getRequestBody();
      byte[] b = is.readAllBytes();
      is.close();

      if (b.length == 0) {
        sendResponse(exchange, 400, "Bad Request: empty payload");
        return;
      }

      final String payloadBody = new String(b, StandardCharsets.UTF_8);
      System.out.println(payloadBody);
      JsonObject json;
      try {
        json = JsonParser.parseString(payloadBody).getAsJsonObject();
      } catch (JsonSyntaxException ignore) {
        sendResponse(exchange, 400, "Bad Request: JSON syntax error");
        return;
      }

      String repo = json.get("repository").getAsString();
      String ref = json.get("ref").getAsString();
      String event = json.get("event").getAsString();

      if (!"push".equals(event)) {
        sendResponse(exchange, 412, "Only push event");
        return;
      }

      try {
        Headers headers = exchange.getRequestHeaders();
        String signatureHeader = headers.getFirst("X-Hub-Signature-256");
        verifySignature(payloadBody, config.getSecret(), signatureHeader);

        if (!repo.equals(config.getRepo())) {
          sendResponse(exchange, 412, "Only repo " + config.getRepo());
          return;
        }

        if (!ref.equals("refs/heads/" + config.getBranch())) {
          sendResponse(exchange, 412, "Only main branch");
          return;
        }

        val hash = json.getAsJsonObject("data").get("hash").getAsString();
        val oldHash = config.getHash();
        if (hash.equals(oldHash)) {
          sendResponse(exchange, 406, "No new content");
          return;
        }
        config.updateHash(hash);
        config.getServer().sendMessage(config.getUpdateMessage());
        sendResponse(exchange, 200, "{\"success\":\"Updated\"}");
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
        throw new Exception("X-Hub-Signature-256 header is missing!");
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
