package ca.apertur.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ca.apertur.sdk.exception.AperturException;
import ca.apertur.sdk.exception.AuthenticationException;
import ca.apertur.sdk.exception.NotFoundException;
import ca.apertur.sdk.exception.RateLimitException;
import ca.apertur.sdk.exception.ValidationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP transport layer for the Apertur API.
 *
 * <p>Wraps {@link java.net.http.HttpClient} (Java 11+), handles authentication,
 * JSON serialization via Jackson, and maps HTTP error responses to typed exceptions.
 */
public final class AperturHttpClient {

    private final String baseUrl;
    private final String authHeader;
    private final String signingSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new HTTP client with no request signing.
     *
     * @param baseUrl the base URL for API requests
     * @param apiKey  the API key, or {@code null}
     * @param oauthToken the OAuth token, or {@code null}
     */
    public AperturHttpClient(String baseUrl, String apiKey, String oauthToken) {
        this(baseUrl, apiKey, oauthToken, null);
    }

    /**
     * Creates a new HTTP client.
     *
     * @param baseUrl       the base URL for API requests
     * @param apiKey        the API key, or {@code null}
     * @param oauthToken    the OAuth token, or {@code null}
     * @param signingSecret the request-signing secret, or {@code null}/empty to disable signing
     */
    public AperturHttpClient(String baseUrl, String apiKey, String oauthToken, String signingSecret) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.signingSecret = signingSecret != null ? signingSecret : "";

        if (apiKey != null && !apiKey.isEmpty()) {
            this.authHeader = "Bearer " + apiKey;
        } else if (oauthToken != null && !oauthToken.isEmpty()) {
            this.authHeader = "Bearer " + oauthToken;
        } else {
            this.authHeader = null;
        }
    }

    /**
     * Computes {@code X-Aptr-Signature} / {@code X-Aptr-Timestamp} headers for a request,
     * or an empty map when no signing secret is configured.
     *
     * <p>{@code body} must be the exact string that will be sent on the wire (or
     * {@code null} for no body) -- the server hashes whatever it actually receives,
     * so an approximation here would just produce a signature that fails verification.
     *
     * @param method the HTTP method
     * @param path   the exact request path, verbatim
     * @param body   the exact JSON body string sent on the wire, or {@code null}
     * @return the signature headers, or an empty map if signing is disabled
     */
    private Map<String, String> signHeaders(String method, String path, String body) {
        if (signingSecret.isEmpty()) {
            return Collections.emptyMap();
        }
        return Signature.signRequest(signingSecret, method, path, body, Instant.now().getEpochSecond());
    }

    /**
     * Returns the Jackson {@link ObjectMapper} used for JSON serialization.
     *
     * @return the object mapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Sends a JSON API request and returns the parsed response body.
     *
     * @param method the HTTP method (GET, POST, PATCH, PUT, DELETE)
     * @param path   the request path (e.g. {@code "/api/v1/stats"})
     * @param body   the request body map, or {@code null} for no body
     * @return the parsed response as a map, or {@code null} for 204 responses
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> request(String method, String path, Map<String, Object> body) {
        return request(method, path, body, Collections.emptyMap());
    }

    /**
     * Sends a JSON API request with custom headers and returns the parsed response body.
     *
     * @param method  the HTTP method
     * @param path    the request path
     * @param body    the request body map, or {@code null}
     * @param headers additional headers to include
     * @return the parsed response as a map, or {@code null} for 204 responses
     */
    public Map<String, Object> request(String method, String path, Map<String, Object> body, Map<String, String> headers) {
        return request(method, path, body, headers, null);
    }

    /**
     * Sends a JSON API request with a per-request timeout.
     *
     * @param method  the HTTP method
     * @param path    the request path
     * @param body    the request body map, or {@code null}
     * @param headers additional headers to include
     * @param timeout per-request timeout, or {@code null} for the client default
     * @return the parsed response as a map, or {@code null} for 204 responses
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> request(String method, String path, Map<String, Object> body, Map<String, String> headers, Duration timeout) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path));

            if (timeout != null) {
                builder.timeout(timeout);
            }

            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            String json = null;
            if (body != null) {
                json = objectMapper.writeValueAsString(body);
                if (!headers.containsKey("Content-Type")) {
                    builder.header("Content-Type", "application/json");
                }
                builder.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else {
                // No body: do NOT advertise a JSON Content-Type. Some endpoints
                // (e.g. the ack endpoint) attempt to parse a JSON body whenever
                // Content-Type: application/json is present, and 500 on an empty body.
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            for (Map.Entry<String, String> entry : signHeaders(method, path, json).entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            handleErrorStatus(response);

            if (response.statusCode() == 204 || response.body() == null || response.body().isEmpty()) {
                return null;
            }

            return objectMapper.readValue(response.body(), Map.class);
        } catch (AperturException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new AperturException(0, "Failed to serialize request body: " + e.getMessage(), null);
        } catch (IOException e) {
            throw new AperturException(0, "HTTP request failed: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AperturException(0, "HTTP request interrupted", null);
        }
    }

    /**
     * Sends a JSON API request whose response body is a JSON array, and returns the
     * parsed list. Used by endpoints such as {@code /sessions/recent} and
     * {@code /uploads/recent} that return a top-level array rather than an object.
     *
     * @param method the HTTP method
     * @param path   the request path
     * @param body   the request body map, or {@code null}
     * @return the parsed response as a list, or {@code null} for empty/204 responses
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> requestList(String method, String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path));

            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }

            String json = null;
            if (body != null) {
                json = objectMapper.writeValueAsString(body);
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            for (Map.Entry<String, String> entry : signHeaders(method, path, json).entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            handleErrorStatus(response);

            if (response.statusCode() == 204 || response.body() == null || response.body().isEmpty()) {
                return null;
            }

            return objectMapper.readValue(response.body(), List.class);
        } catch (AperturException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new AperturException(0, "Failed to serialize request body: " + e.getMessage(), null);
        } catch (IOException e) {
            throw new AperturException(0, "HTTP request failed: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AperturException(0, "HTTP request interrupted", null);
        }
    }

    /**
     * Sends a JSON string body request with custom headers and returns the parsed response body.
     *
     * @param method  the HTTP method
     * @param path    the request path
     * @param jsonBody the raw JSON string body
     * @param headers additional headers to include
     * @return the parsed response as a map, or {@code null} for 204 responses
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestWithJsonBody(String method, String path, String jsonBody, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path));

            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            if (!headers.containsKey("Content-Type")) {
                builder.header("Content-Type", "application/json");
            }
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

            for (Map.Entry<String, String> entry : signHeaders(method, path, jsonBody).entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            handleErrorStatus(response);

            if (response.statusCode() == 204 || response.body() == null || response.body().isEmpty()) {
                return null;
            }

            return objectMapper.readValue(response.body(), Map.class);
        } catch (AperturException e) {
            throw e;
        } catch (IOException e) {
            throw new AperturException(0, "HTTP request failed: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AperturException(0, "HTTP request interrupted", null);
        }
    }

    /**
     * Sends a request and returns the raw response bytes. Used for binary
     * downloads such as QR codes and images.
     *
     * @param method the HTTP method
     * @param path   the request path
     * @return the raw response bytes
     */
    public byte[] requestRaw(String method, String path) {
        return requestRaw(method, path, Collections.emptyMap());
    }

    /**
     * Sends a request with custom headers and returns the raw response bytes.
     *
     * @param method  the HTTP method
     * @param path    the request path
     * @param headers additional headers to include
     * @return the raw response bytes
     */
    public byte[] requestRaw(String method, String path, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .method(method, HttpRequest.BodyPublishers.noBody());

            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, String> entry : signHeaders(method, path, null).entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            handleErrorStatusRaw(response);

            return response.body();
        } catch (AperturException e) {
            throw e;
        } catch (IOException e) {
            throw new AperturException(0, "HTTP request failed: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AperturException(0, "HTTP request interrupted", null);
        }
    }

    /**
     * Sends a multipart/form-data request. Used for plain image uploads.
     *
     * <p>Multipart bodies are NOT request-signed: the framing (boundary, part
     * headers) is built locally here rather than by the transport, but the
     * server's own encoding of the multipart envelope is not something a
     * client-side signature can usefully cover, and multipart uploads should
     * rely on {@code Authorization} (API key) auth instead. Mirrors the Node SDK.
     *
     * @param path     the request path
     * @param fileData the file content bytes
     * @param filename the filename
     * @param mimeType the MIME type (e.g. {@code "image/jpeg"})
     * @param fields   additional form fields
     * @param headers  additional headers
     * @return the parsed response as a map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestMultipart(String path, byte[] fileData, String filename,
                                                 String mimeType, Map<String, String> fields,
                                                 Map<String, String> headers) {
        try {
            String boundary = "----AperturSDK" + UUID.randomUUID().toString().replace("-", "");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Write form fields
            for (Map.Entry<String, String> field : fields.entrySet()) {
                baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                baos.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                baos.write(field.getValue().getBytes(StandardCharsets.UTF_8));
                baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }

            // Write file part
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(fileData);
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

            // Closing boundary
            baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            byte[] bodyBytes = baos.toByteArray();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));

            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            handleErrorStatus(response);

            if (response.statusCode() == 204 || response.body() == null || response.body().isEmpty()) {
                return null;
            }

            return objectMapper.readValue(response.body(), Map.class);
        } catch (AperturException e) {
            throw e;
        } catch (IOException e) {
            throw new AperturException(0, "HTTP request failed: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AperturException(0, "HTTP request interrupted", null);
        }
    }

    /**
     * Builds a query string from a map of parameters. Null values are skipped.
     *
     * @param params the query parameters
     * @return the query string including the leading {@code ?}, or empty string if no params
     */
    public static String buildQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                parts.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return parts.isEmpty() ? "" : "?" + String.join("&", parts);
    }

    @SuppressWarnings("unchecked")
    private void handleErrorStatus(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }

        String message;
        String code = null;
        try {
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            message = body.containsKey("message") ? String.valueOf(body.get("message")) : "HTTP " + status;
            code = body.containsKey("code") ? String.valueOf(body.get("code")) : null;
        } catch (Exception e) {
            message = "HTTP " + status;
        }

        throwForStatus(status, message, code, response);
    }

    private void handleErrorStatusRaw(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }

        String message;
        String code = null;
        try {
            String body = new String(response.body(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            message = parsed.containsKey("message") ? String.valueOf(parsed.get("message")) : "HTTP " + status;
            code = parsed.containsKey("code") ? String.valueOf(parsed.get("code")) : null;
        } catch (Exception e) {
            message = "HTTP " + status;
        }

        throwForStatus(status, message, code, response);
    }

    private void throwForStatus(int status, String message, String code, HttpResponse<?> response) {
        switch (status) {
            case 400:
                throw new ValidationException(message);
            case 401:
                throw new AuthenticationException(message);
            case 404:
                throw new NotFoundException(message);
            case 429:
                Integer retryAfter = null;
                try {
                    String retryHeader = response.headers().firstValue("Retry-After").orElse(null);
                    if (retryHeader != null) {
                        retryAfter = Integer.parseInt(retryHeader);
                    }
                } catch (NumberFormatException ignored) {
                }
                throw new RateLimitException(message, retryAfter);
            default:
                throw new AperturException(status, message, code);
        }
    }
}
