package ca.apertur.sdk.resource;

import io.socket.client.Ack;
import io.socket.client.Socket;

import org.json.JSONObject;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A live subscription to the real-time events of a single upload session.
 *
 * <p>Returned by {@link Events#subscribe(String)}. Register listeners with
 * {@link #on(String, Consumer)} and stop receiving events with {@link #close()}.
 * The underlying Socket.IO connection auto-reconnects; the session room is
 * re-joined on every (re)connect so a dropped socket resumes delivery without
 * re-subscribing.
 *
 * <p>Events surfaced:
 * <ul>
 *   <li>{@code connected} — once the server acknowledges {@code session:join};
 *       the payload is a {@code Map} containing {@code sessionId}</li>
 *   <li>{@code image:ready}, {@code image:delivering}, {@code image:delivered},
 *       {@code image:failed} — each with its JSON payload (an
 *       {@link org.json.JSONObject})</li>
 *   <li>{@code error} — on a failed join or transport error; the payload is a
 *       {@link Throwable}</li>
 *   <li>{@code close} — when the socket disconnects; the payload is {@code null}</li>
 * </ul>
 */
public final class EventSubscription {

    /** Session events broadcast by the API on the {@code /upload} namespace. */
    static final String[] SESSION_EVENTS = {
        "image:ready",
        "image:delivering",
        "image:delivered",
        "image:failed",
    };

    private final Socket socket;
    private final Map<String, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();

    /**
     * Creates a subscription over the given socket and wires it to the session
     * room. Package-private: instances are created by {@link Events#subscribe(String)}.
     *
     * @param socket the (not yet connected) Socket.IO client
     * @param uuid   the session UUID to join
     */
    EventSubscription(Socket socket, String uuid) {
        this.socket = socket;
        wire(uuid);
        socket.connect();
    }

    /**
     * Registers a handler for an event. Multiple handlers may be registered for
     * the same event; all are invoked in registration order.
     *
     * @param event   the event name (e.g. {@code "image:ready"}, {@code "connected"})
     * @param handler the handler invoked with the event payload
     * @return this subscription, for chaining
     */
    public EventSubscription on(String event, Consumer<Object> handler) {
        handlers.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(handler);
        return this;
    }

    /**
     * Disconnects the underlying socket and stops receiving events.
     */
    public void close() {
        socket.disconnect();
    }

    private void dispatch(String event, Object data) {
        List<Consumer<Object>> list = handlers.get(event);
        if (list != null) {
            for (Consumer<Object> handler : list) {
                handler.accept(data);
            }
        }
    }

    private void wire(String uuid) {
        // (Re)join the session room on every (re)connect so a dropped socket
        // resumes receiving events without the caller re-subscribing.
        socket.on(Socket.EVENT_CONNECT, args -> socket.emit("session:join", new Object[]{uuid}, (Ack) ackArgs -> {
            Object res = ackArgs != null && ackArgs.length > 0 ? ackArgs[0] : null;
            boolean ok = false;
            String error = null;
            if (res instanceof JSONObject) {
                JSONObject obj = (JSONObject) res;
                ok = obj.optBoolean("ok", false);
                error = obj.has("error") && !obj.isNull("error") ? obj.optString("error", null) : null;
            }
            if (ok) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("sessionId", uuid);
                dispatch("connected", data);
            } else {
                dispatch("error", new RuntimeException(error != null ? error : "session:join rejected"));
            }
        }));

        for (String name : SESSION_EVENTS) {
            socket.on(name, args -> dispatch(name, args != null && args.length > 0 ? args[0] : null));
        }

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Object err = args != null && args.length > 0 ? args[0] : null;
            Throwable cause = err instanceof Throwable
                ? (Throwable) err
                : new RuntimeException(err == null ? "connect_error" : String.valueOf(err));
            dispatch("error", cause);
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> dispatch("close", null));
    }
}
