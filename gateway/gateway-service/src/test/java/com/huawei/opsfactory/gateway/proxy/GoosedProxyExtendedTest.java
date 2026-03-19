package com.huawei.opsfactory.gateway.proxy;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Extended tests for GoosedProxy covering:
 * - copyHeaders: secret key injection
 * - copyUpstreamHeaders: CORS header filtering
 * - fetchJson: returns non-null Mono (construction-level)
 */
public class GoosedProxyExtendedTest {

    private GoosedProxy proxy;

    @Before
    public void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecretKey("my-secret");
        proxy = new GoosedProxy(properties);
    }

    // ====================== copyHeaders ======================

    @Test
    public void testCopyHeaders_injectsSecretKey() throws Exception {
        HttpHeaders source = new HttpHeaders();
        source.add("Content-Type", "application/json");
        source.add("X-Custom", "value");

        HttpHeaders target = new HttpHeaders();

        Method copyHeaders = GoosedProxy.class.getDeclaredMethod("copyHeaders", HttpHeaders.class, HttpHeaders.class, String.class);
        copyHeaders.setAccessible(true);
        copyHeaders.invoke(proxy, source, target, "my-secret");

        assertEquals("application/json", target.getFirst("Content-Type"));
        assertEquals("value", target.getFirst("X-Custom"));
        assertEquals("my-secret", target.getFirst("x-secret-key"));
    }

    @Test
    public void testCopyHeaders_overridesExistingSecretKey() throws Exception {
        HttpHeaders source = new HttpHeaders();
        source.add("x-secret-key", "client-key-should-be-overridden");

        HttpHeaders target = new HttpHeaders();

        Method copyHeaders = GoosedProxy.class.getDeclaredMethod("copyHeaders", HttpHeaders.class, HttpHeaders.class, String.class);
        copyHeaders.setAccessible(true);
        copyHeaders.invoke(proxy, source, target, "my-secret");

        // Should be overridden by gateway's secret key
        assertEquals("my-secret", target.getFirst("x-secret-key"));
    }

    // ====================== copyUpstreamHeaders ======================

    @Test
    public void testCopyUpstreamHeaders_filtersCorsHeaders() throws Exception {
        HttpHeaders source = new HttpHeaders();
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST");
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        source.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Custom");
        source.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        source.add("X-Custom-Header", "keep-this");
        source.add("Content-Type", "application/json");

        HttpHeaders target = new HttpHeaders();

        Method copyUpstream = GoosedProxy.class.getDeclaredMethod("copyUpstreamHeaders", HttpHeaders.class, HttpHeaders.class);
        copyUpstream.setAccessible(true);
        copyUpstream.invoke(proxy, source, target);

        // CORS headers should be filtered out
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_MAX_AGE));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        // Non-CORS headers should be kept
        assertEquals("keep-this", target.getFirst("X-Custom-Header"));
        assertEquals("application/json", target.getFirst("Content-Type"));
    }

    @Test
    public void testCopyUpstreamHeaders_emptySource() throws Exception {
        HttpHeaders source = new HttpHeaders();
        HttpHeaders target = new HttpHeaders();

        Method copyUpstream = GoosedProxy.class.getDeclaredMethod("copyUpstreamHeaders", HttpHeaders.class, HttpHeaders.class);
        copyUpstream.setAccessible(true);
        copyUpstream.invoke(proxy, source, target);

        assertTrue(target.isEmpty());
    }

    // ====================== fetchJson ======================

    @Test
    public void testFetchJson_returnsNonNullMono() {
        // Construction-level test: verifies Mono is created without errors
        assertNotNull(proxy.fetchJson(99999, "/test", "test-secret"));
    }

    // ====================== proxyWithBody ======================

    @Test
    public void testProxyWithBody_returnsNonNullMono() {
        // Construction-level test: verifies Mono is created
        assertNotNull(proxy.proxyWithBody(null, 99999, "/test",
                org.springframework.http.HttpMethod.POST, "{}", "test-secret"));
    }
}
