package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.util.List;
import java.util.Map;

/**
 * Long-polling for new images in a session.
 *
 * <p>Poll a session, download each image, and acknowledge receipt to advance the queue.
 * The {@link #pollAndProcess} helper automates this loop.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 *
 * // Manual cycle
 * Map<String, Object> result = client.polling().list(uuid);
 * List<Map<String, Object>> images = (List) result.get("images");
 * for (Map<String, Object> image : images) {
 *     byte[] data = client.polling().download(uuid, (String) image.get("id"));
 *     Files.write(Path.of("/tmp/" + image.get("id") + ".jpg"), data);
 *     client.polling().ack(uuid, (String) image.get("id"));
 * }
 *
 * // Automatic loop
 * client.polling().pollAndProcess(uuid, (image, data) -> {
 *     Files.write(Path.of("/tmp/" + image.get("id") + ".jpg"), data);
 * }, new PollOptions(3000));
 * }</pre>
 */
public final class Polling {

    private final AperturHttpClient http;

    /**
     * Creates a new Polling resource.
     *
     * @param http the HTTP client
     */
    public Polling(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Polls the session for new images.
     *
     * @param uuid the session UUID
     * @return the poll result containing an images list
     */
    public Map<String, Object> list(String uuid) {
        return http.request("GET", "/api/v1/upload-sessions/" + uuid + "/poll", null);
    }

    /**
     * Downloads an image by ID from a session.
     *
     * @param uuid    the session UUID
     * @param imageId the image ID
     * @return the raw image bytes
     */
    public byte[] download(String uuid, String imageId) {
        return http.requestRaw("GET", "/api/v1/upload-sessions/" + uuid + "/images/" + imageId);
    }

    /**
     * Acknowledges receipt of an image, removing it from the polling queue.
     *
     * @param uuid    the session UUID
     * @param imageId the image ID
     * @return the acknowledgement result
     */
    public Map<String, Object> ack(String uuid, String imageId) {
        return http.request("POST", "/api/v1/upload-sessions/" + uuid + "/images/" + imageId + "/ack", null);
    }

    /**
     * Continuously polls for new images, downloads each one, calls the handler,
     * and acknowledges receipt. The loop blocks the calling thread and respects
     * {@link Thread#interrupt()}.
     *
     * @param uuid    the session UUID
     * @param handler the handler invoked for each image
     * @param options polling options (interval in milliseconds)
     */
    @SuppressWarnings("unchecked")
    public void pollAndProcess(String uuid, PollHandler handler, PollOptions options) {
        long interval = options != null ? options.getInterval() : 3000;

        while (!Thread.currentThread().isInterrupted()) {
            Map<String, Object> result = list(uuid);

            List<Map<String, Object>> images = (List<Map<String, Object>>) result.get("images");
            if (images != null) {
                for (Map<String, Object> image : images) {
                    if (Thread.currentThread().isInterrupted()) return;
                    String imageId = (String) image.get("id");
                    byte[] data = download(uuid, imageId);
                    handler.handle(image, data);
                    ack(uuid, imageId);
                }
            }

            if (Thread.currentThread().isInterrupted()) return;
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Functional interface for handling polled images.
     */
    @FunctionalInterface
    public interface PollHandler {
        /**
         * Called for each image received during polling.
         *
         * @param image the image metadata map
         * @param data  the raw image bytes
         */
        void handle(Map<String, Object> image, byte[] data);
    }

    /**
     * Options for the {@link #pollAndProcess} loop.
     */
    public static final class PollOptions {

        private final long interval;

        /**
         * Creates poll options with the specified interval.
         *
         * @param intervalMs the polling interval in milliseconds (default 3000)
         */
        public PollOptions(long intervalMs) {
            this.interval = intervalMs;
        }

        /**
         * Creates poll options with the default 3-second interval.
         */
        public PollOptions() {
            this(3000);
        }

        /**
         * Returns the polling interval in milliseconds.
         *
         * @return the interval
         */
        public long getInterval() {
            return interval;
        }
    }
}
