package ca.apertur.sdk;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Known-answer tests for {@link Signature#signRequest}, matching the vectors
 * produced by the apertur Node reference implementation ({@code signRequest}
 * in {@code packages/client-node/src/signature.ts}).
 */
class SignatureTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void signsAJsonBodyRequest() {
        Map<String, String> headers = Signature.signRequest(SECRET, "POST", "/api/v1/upload-sessions", "{\"a\":1}", 1800000000L);

        assertEquals("sha256=d5bf88c946aa6cf4397749eacd05cf058e6828878f9b1c84830cb7c07a234d3e", headers.get("X-Aptr-Signature"));
        assertEquals("1800000000", headers.get("X-Aptr-Timestamp"));
    }

    @Test
    void signsAnEmptyBodyRequestAsGet() {
        Map<String, String> headers = Signature.signRequest(SECRET, "GET", "/api/v1/uploads/abc", null, 1800000000L);

        assertEquals("sha256=f53cf714f69187170c4fdb22c53e0b53578dcbcb61e63b56948d5e1fd8294a3e", headers.get("X-Aptr-Signature"));
        assertEquals("1800000000", headers.get("X-Aptr-Timestamp"));
    }

    @Test
    void uppercasesTheMethodBeforeSigning() {
        Map<String, String> lower = Signature.signRequest(SECRET, "get", "/api/v1/uploads/abc", null, 1800000000L);
        Map<String, String> upper = Signature.signRequest(SECRET, "GET", "/api/v1/uploads/abc", null, 1800000000L);

        assertEquals(upper.get("X-Aptr-Signature"), lower.get("X-Aptr-Signature"));
    }
}
