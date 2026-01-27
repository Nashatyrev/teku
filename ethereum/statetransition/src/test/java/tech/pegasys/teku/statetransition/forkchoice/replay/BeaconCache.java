package tech.pegasys.teku.statetransition.forkchoice.replay;

import org.apache.tuweni.bytes.Bytes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public class BeaconCache {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final String cachePath = "./work.dir/http.cache";

  public static Bytes getCachedContent(String url) {
    try {
      Path file = getCachedFile(url, Path.of(cachePath));
      byte[] bytes = Files.readAllBytes(file);
      return Bytes.wrap(bytes);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Downloads a URL once and caches it in the given directory. Subsequent calls return the same
   * cached file.
   *
   * @param url Full HTTP/HTTPS URL of the resource (e.g. SSZ block)
   * @param cacheDir Local directory for cached files
   * @return Path to the cached file
   */
  public static Path getCachedFile(String url, Path cacheDir) throws Exception {
    Files.createDirectories(cacheDir);

    // Create a deterministic filename from SHA-256 of the URL
    String hash =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes()));
    Path target = cacheDir.resolve(hash);

    if (Files.notExists(target)) {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .header("Accept", "application/octet-stream") // or application/json
              .build();
      byte[] body = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
      Files.write(target, body);
    }
    return target;
  }
}
