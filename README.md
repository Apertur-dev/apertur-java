# apertur-sdk

Official Java SDK for the [Apertur](https://apertur.ca) API. Supports API key and OAuth token authentication, session management, image uploads (plain and encrypted), long polling, webhook verification, and full resource CRUD.

## Installation

Requires Java 11+ and is built with Maven.

```xml
<dependency>
    <groupId>ca.apertur</groupId>
    <artifactId>apertur-sdk</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Quick Start

Create a client, open an upload session, and upload an image in a few lines. See the [API documentation](https://docs.apertur.ca) for a full overview.

```java
import ca.apertur.sdk.Apertur;
import java.nio.file.Path;
import java.util.Map;

Apertur client = new Apertur("aptr_live_...");

Map<String, Object> session = client.sessions().create(Map.of("label", "My shoot"));
String uuid = (String) session.get("uuid");

Map<String, Object> image = client.upload().image(uuid, Path.of("/tmp/photo.jpg"), Map.of(
    "filename", "photo.jpg",
    "mimeType", "image/jpeg"
));

System.out.println(image.get("id"));
```

## Authentication

The client accepts either a long-lived API key or a short-lived OAuth bearer token. Only one is required; providing both will result in the API key being used. See [Authentication documentation](https://docs.apertur.ca/authentication).

```java
import ca.apertur.sdk.Apertur;
import ca.apertur.sdk.AperturConfig;

// API key (shorthand)
Apertur client = new Apertur("aptr_live_...");

// API key (full config)
AperturConfig config = new AperturConfig.Builder()
    .apiKey("aptr_live_...")
    .build();
Apertur client = new Apertur(config);

// OAuth token
AperturConfig config = new AperturConfig.Builder()
    .oauthToken(accessToken)
    .build();
Apertur client = new Apertur(config);

// Custom base URL (sandbox)
AperturConfig config = new AperturConfig.Builder()
    .apiKey("aptr_live_...")
    .baseUrl("https://sandbox.api.aptr.ca")
    .build();
Apertur client = new Apertur(config);
```

## Sessions

Upload sessions scope every image upload. You can create a session with optional settings, retrieve it, protect it with a password, and check delivery status. See [Sessions documentation](https://docs.apertur.ca/upload-sessions).

```java
Apertur client = new Apertur("aptr_live_...");

// Create a session
Map<String, Object> session = client.sessions().create(Map.of(
    "label", "Wedding reception",
    "password", "s3cr3t",
    "maxImages", 200
));

// Retrieve session details
String uuid = (String) session.get("uuid");
Map<String, Object> details = client.sessions().get(uuid);

// Verify a password-protected session before uploading
Map<String, Object> result = client.sessions().verifyPassword(uuid, "s3cr3t");

// Check delivery status — snapshot
Map<String, Object> status = client.sessions().deliveryStatus(uuid);
String overall  = (String) status.get("status");        // pending|active|completed|expired
List<Map<String, Object>> files = (List) status.get("files");
String changed  = (String) status.get("lastChanged");

// Long-poll for the next change (server holds up to 5 min; SDK uses a 6-min client timeout)
Map<String, Object> next = client.sessions().deliveryStatus(uuid, changed);

// Generate a QR code
byte[] qr = client.sessions().qr(uuid, Map.of("format", "png", "size", 300));
```

## Uploading Images

Upload a plain image using a file path, byte array, or InputStream. For end-to-end encrypted uploads, use `imageEncrypted` with the server's RSA public key. See [Upload documentation](https://docs.apertur.ca/upload-sessions).

```java
Apertur client = new Apertur("aptr_live_...");
String uuid = "session-uuid-here";

// Upload from a file path
Map<String, Object> image = client.upload().image(uuid, Path.of("/tmp/photo.jpg"), Map.of(
    "filename", "photo.jpg",
    "mimeType", "image/jpeg",
    "source", "my-app"
));

// Upload from a byte array
byte[] data = Files.readAllBytes(Path.of("/tmp/photo.jpg"));
Map<String, Object> image = client.upload().image(uuid, data, "photo.jpg", Map.of(
    "mimeType", "image/jpeg"
));

// Upload to a password-protected session
Map<String, Object> image = client.upload().image(uuid, Path.of("/tmp/photo.jpg"), Map.of(
    "password", "s3cr3t"
));

// Encrypted upload (fetch the server key first)
Map<String, Object> serverKey = client.encryption().getServerKey();
byte[] fileData = Files.readAllBytes(Path.of("/tmp/photo.jpg"));
Map<String, Object> image = client.upload().imageEncrypted(
    uuid, fileData, (String) serverKey.get("publicKey"), Map.of(
        "filename", "photo.jpg",
        "mimeType", "image/jpeg"
    )
);
```

## Long Polling

Poll a session for new images, download each one, and acknowledge receipt to advance the queue. The `pollAndProcess` helper loops automatically and calls your handler for every image. See [Long Polling documentation](https://docs.apertur.ca/long-polling).

```java
Apertur client = new Apertur("aptr_live_...");
String uuid = "session-uuid-here";

// Manual poll / download / ack cycle
Map<String, Object> result = client.polling().list(uuid);
List<Map<String, Object>> images = (List) result.get("images");
for (Map<String, Object> image : images) {
    byte[] data = client.polling().download(uuid, (String) image.get("id"));
    Files.write(Path.of("/tmp/" + image.get("id") + ".jpg"), data);
    client.polling().ack(uuid, (String) image.get("id"));
}

// Automatic loop with 3-second interval (blocks current thread)
client.polling().pollAndProcess(uuid, (image, data) -> {
    try {
        Files.write(Path.of("/tmp/" + image.get("id") + ".jpg"), data);
        System.out.println("Saved " + image.get("id"));
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}, new Polling.PollOptions(3000));
```

## Realtime Events

Subscribe to a session's delivery lifecycle over Socket.IO instead of long polling. The client connects to the anonymous `/upload` namespace and joins the session room — the session UUID is the access token, so no API key is used for the socket. Reconnection is automatic and the room is re-joined on every reconnect. See [Realtime documentation](https://docs.apertur.ca/realtime).

```java
import ca.apertur.sdk.Apertur;
import ca.apertur.sdk.resource.EventSubscription;

Apertur client = new Apertur("aptr_live_...");
String uuid = "session-uuid-here";

EventSubscription sub = client.events().subscribe(uuid);

// connected → payload is a Map containing "sessionId"
sub.on("connected", data -> System.out.println("connected: " + data));

// image lifecycle → payload is the event's JSON (org.json.JSONObject)
sub.on("image:ready",      data -> System.out.println("ready: " + data));
sub.on("image:delivering", data -> System.out.println("delivering: " + data));
sub.on("image:delivered",  data -> System.out.println("delivered: " + data));
sub.on("image:failed",     data -> System.out.println("failed: " + data));

// error → payload is a Throwable; close → payload is null
sub.on("error", err  -> System.err.println("error: " + err));
sub.on("close", data -> System.out.println("disconnected"));

// Stop receiving events and disconnect
sub.close();
```

## Receiving Webhooks

Apertur signs every webhook payload so you can verify it was not tampered with. Three verification methods are available. See [Webhooks documentation](https://docs.apertur.ca/webhooks).

```java
import ca.apertur.sdk.Signature;

// Image delivery webhook
boolean valid = Signature.verifyWebhookSignature(body, signatureHeader, secret);

// Event webhook (HMAC method)
boolean valid = Signature.verifyEventSignature(body, timestampHeader, signatureHeader, secret);

// Event webhook (Svix method)
boolean valid = Signature.verifySvixSignature(body, svixId, svixTimestamp, svixSignature, secret);
```

## Destinations

Destinations define where uploaded images are delivered. See [Destinations documentation](https://docs.apertur.ca/destinations).

```java
Apertur client = new Apertur("aptr_live_...");
String projectId = "proj_...";

Map<String, Object> list = client.destinations().list(projectId);

Map<String, Object> dest = client.destinations().create(projectId, Map.of(
    "type", "s3",
    "label", "Primary S3 bucket",
    "config", Map.of("bucket", "my-bucket", "region", "us-east-1")
));

client.destinations().test(projectId, (String) dest.get("id"));
client.destinations().delete(projectId, (String) dest.get("id"));
```

## API Keys

API keys are scoped to a project and optionally restricted to specific destinations. See [API Keys documentation](https://docs.apertur.ca/api-keys).

```java
Apertur client = new Apertur("aptr_live_...");
String projectId = "proj_...";

Map<String, Object> key = client.keys().create(projectId, Map.of("label", "Mobile app key"));
client.keys().setDestinations((String) key.get("id"), List.of("dest_abc", "dest_def"), true);
client.keys().delete(projectId, (String) key.get("id"));
```

## Event Webhooks

Event webhooks push real-time notifications to your endpoint. See [Event Webhooks documentation](https://docs.apertur.ca/event-webhooks).

```java
Apertur client = new Apertur("aptr_live_...");
String projectId = "proj_...";

Map<String, Object> webhook = client.webhooks().create(projectId, Map.of(
    "url", "https://example.com/webhooks/apertur",
    "events", List.of("image.uploaded", "session.completed")
));

client.webhooks().test(projectId, (String) webhook.get("id"));

Map<String, Object> deliveries = client.webhooks().deliveries(
    projectId, (String) webhook.get("id"), Map.of("page", 1, "limit", 25)
);
```

## Encryption

Apertur supports end-to-end encrypted uploads using RSA-OAEP + AES-256-GCM. See [Encryption documentation](https://docs.apertur.ca/encryption).

```java
Apertur client = new Apertur("aptr_live_...");

Map<String, Object> serverKey = client.encryption().getServerKey();
String publicKeyPem = (String) serverKey.get("publicKey");

byte[] imageData = Files.readAllBytes(Path.of("/tmp/photo.jpg"));
Map<String, Object> image = client.upload().imageEncrypted(
    "session-uuid", imageData, publicKeyPem, Map.of(
        "filename", "photo.jpg",
        "mimeType", "image/jpeg"
    )
);
```

## Error Handling

All API errors throw typed exceptions that extend `AperturException`. See [Error Handling documentation](https://docs.apertur.ca/errors).

```java
import ca.apertur.sdk.exception.*;

try {
    Map<String, Object> session = client.sessions().create(Map.of("label", "My shoot"));
} catch (AuthenticationException e) {
    // 401
    System.out.println("Auth failed: " + e.getMessage());
} catch (NotFoundException e) {
    // 404
    System.out.println("Not found: " + e.getMessage());
} catch (RateLimitException e) {
    // 429
    System.out.println("Rate limited. Retry after " + e.getRetryAfter() + "s");
} catch (ValidationException e) {
    // 400
    System.out.println("Validation error: " + e.getMessage());
} catch (AperturException e) {
    // Any other API error
    System.out.println("API error " + e.getStatusCode() + ": " + e.getMessage());
}
```

## API Reference

Full API reference, guides, and changelog are available at [docs.apertur.ca](https://docs.apertur.ca).

## License

This package is open-source software licensed under the [MIT license](https://opensource.org/licenses/MIT).
