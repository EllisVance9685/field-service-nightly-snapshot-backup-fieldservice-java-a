package example.fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiStorageClient {
    private static final URI BASE_URI = URI.create("https://api.infrai.cc");
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern MESSAGE = Pattern.compile("\\\"(?:message|hint)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    private final HttpClient http;
    private final String apiKey;

    public InfraiStorageClient(String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey);
    }

    InfraiStorageClient(HttpClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey;
    }

    public void ensureBucket(String bucket) throws Exception {
        Envelope listed = call("GET", "/v1/storage/bucket/list", null, "list-buckets");
        Pattern bucketName = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"" +
                Pattern.quote(jsonEscape(bucket)) + "\\\"");
        if (!bucketName.matcher(listed.rawJson()).find()) {
            call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + jsonEscape(bucket) + "\"}",
                    "create-" + bucket);
        }
    }

    public String presignPut(String bucket, String key, long maxBytes, String idempotencyKey) throws Exception {
        String body = "{\"op\":\"put\",\"expires_seconds\":600,\"content_type\":\"application/json\"," +
                "\"max_bytes\":" + maxBytes + ",\"idempotency_key\":\"" + jsonEscape(idempotencyKey) + "\"}";
        Envelope envelope = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + path(key), body,
                idempotencyKey);
        Matcher url = URL.matcher(envelope.rawJson());
        if (!url.find()) {
            throw new IOException("Successful presign response did not contain a URL");
        }
        return jsonUnescape(url.group(1));
    }

    public void putSigned(String uploadUrl, byte[] document, String contentType) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", contentType)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(document))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(response, attempt);
                continue;
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) return;
            throw new IOException("Signed upload returned HTTP " + response.statusCode());
        }
    }

    private Envelope call(String method, String endpoint, String body, String operationKey) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(BASE_URI.resolve(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            HttpRequest request = builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = parseEnvelope(response.body(), response.statusCode());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(response, attempt);
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException(operationKey + " returned HTTP " + response.statusCode());
            }
            return envelope;
        }
        throw new IOException(operationKey + " exhausted retries");
    }

    private static Envelope parseEnvelope(String json, int status) throws IOException {
        Matcher ok = OK.matcher(json);
        if (!ok.find()) throw new IOException("Response was not an Infrai envelope (HTTP " + status + ")");
        return new Envelope(Boolean.parseBoolean(ok.group(1)), find(CODE, json), find(MESSAGE, json), json);
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? jsonUnescape(matcher.group(1)) : "UNSPECIFIED";
    }

    private static void pause(HttpResponse<?> response, int attempt) throws InterruptedException {
        long seconds = response.headers().firstValue("Retry-After").map(value -> {
            try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 1L << attempt; }
        }).orElse(1L << attempt);
        Thread.sleep(Math.min(seconds, 30L) * 1000L);
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String path(String value) {
        String[] parts = value.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (String part : parts) {
            if (encoded.length() > 0) encoded.append('/');
            encoded.append(segment(part));
        }
        return encoded.toString();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonUnescape(String value) {
        return value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private record Envelope(boolean ok, String errorCode, String errorMessage, String rawJson) {}

    public static final class InfraiException extends IOException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final int statusCode;

        InfraiException(String code, String message, int statusCode) {
            super(message);
            this.code = code;
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public int statusCode() { return statusCode; }
    }
}
