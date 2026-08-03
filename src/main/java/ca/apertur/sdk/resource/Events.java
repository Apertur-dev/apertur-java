package ca.apertur.sdk.resource;

import io.socket.client.IO;
import io.socket.client.Socket;

import java.net.URISyntaxException;

/**
 * Real-time session events over Socket.IO.
 *
 * <p>Connects to the anonymous {@code /upload} namespace and joins a session
 * room — the session UUID itself is the access token, so no API key is required
 * for the socket. Use {@link #subscribe(String)} to open a subscription and
 * register listeners.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 *
 * EventSubscription sub = client.events().subscribe(uuid);
 * sub.on("connected",       data -> System.out.println("connected"));
 * sub.on("image:ready",     data -> System.out.println("ready: " + data));
 * sub.on("image:delivered", data -> System.out.println("delivered: " + data));
 * sub.on("error",           err  -> System.err.println("error: " + err));
 * sub.on("close",           data -> System.out.println("closed"));
 *
 * // later
 * sub.close();
 * }</pre>
 */
public final class Events {

    private final String baseUrl;

    /**
     * Creates a new Events resource.
     *
     * @param baseUrl the API base URL (e.g. {@code "https://api.aptr.ca"})
     */
    public Events(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /**
     * Subscribes to real-time events for an upload session.
     *
     * <p>Opens a Socket.IO connection to the anonymous {@code /upload} namespace
     * and joins the session room. Reconnection is automatic; the room is
     * re-joined on every reconnect. Register listeners on the returned
     * {@link EventSubscription} and call {@link EventSubscription#close()} to
     * disconnect.
     *
     * @param uuid the session UUID
     * @return a live subscription
     * @throws IllegalArgumentException if the resolved socket URL is invalid
     */
    public EventSubscription subscribe(String uuid) {
        IO.Options options = new IO.Options();
        options.path = "/socket.io";
        options.reconnection = true;
        options.transports = new String[]{"websocket", "polling"};

        try {
            Socket socket = IO.socket(baseUrl + "/upload", options);
            return new EventSubscription(socket, uuid);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Apertur socket URL: " + baseUrl + "/upload", e);
        }
    }
}
