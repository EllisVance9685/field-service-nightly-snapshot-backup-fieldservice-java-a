package example.fieldservice;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.Properties;

public record SnapshotConfig(String apiKey, String bucket, String prefix, ZoneId zone) {
    public static SnapshotConfig load() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = SnapshotConfig.class.getResourceAsStream("/application.properties")) {
            if (input == null) {
                throw new IOException("application.properties is missing");
            }
            properties.load(input);
        }
        String key = requiredEnvironment("INFRAI_API_KEY");
        String bucket = environmentOr("SNAPSHOT_BUCKET", properties.getProperty("snapshot.bucket"));
        String prefix = environmentOr("SNAPSHOT_PREFIX", properties.getProperty("snapshot.prefix"));
        String zone = environmentOr("SNAPSHOT_ZONE", properties.getProperty("snapshot.zone"));
        return new SnapshotConfig(key, bucket, prefix, ZoneId.of(zone));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static String environmentOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

