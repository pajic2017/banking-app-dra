import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Dependency-free container readiness check, separate from application contracts. */
public final class HealthCheck {
    public static void main(String[] args) {
        try {
            var connection = (HttpURLConnection) URI.create(args[0]).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            if (connection.getResponseCode() != 200) System.exit(1);
            try (var body = connection.getInputStream()) {
                var json = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                if (!json.contains("\"status\":\"UP\"")) System.exit(1);
            }
            connection.disconnect();
        } catch (Exception failure) {
            System.err.println("Readiness request failed: " + failure.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
