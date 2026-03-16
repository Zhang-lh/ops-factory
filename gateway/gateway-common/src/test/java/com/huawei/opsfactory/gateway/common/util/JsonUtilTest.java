package com.huawei.opsfactory.gateway.common.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JsonUtilTest {

    // ---- extractStringField ----

    @Test
    public void testExtractStringField_simpleField() {
        String json = "{\"name\": \"alice\", \"age\": 30}";
        assertEquals("alice", JsonUtil.extractStringField(json, "name"));
    }

    @Test
    public void testExtractStringField_fieldNotFound() {
        String json = "{\"name\": \"alice\"}";
        assertNull(JsonUtil.extractStringField(json, "missing"));
    }

    @Test
    public void testExtractStringField_multipleFieldNames_firstMatch() {
        String json = "{\"session_id\": \"abc123\"}";
        assertEquals("abc123", JsonUtil.extractStringField(json, "session_id", "sessionId"));
    }

    @Test
    public void testExtractStringField_multipleFieldNames_secondMatch() {
        String json = "{\"sessionId\": \"def456\"}";
        assertEquals("def456", JsonUtil.extractStringField(json, "session_id", "sessionId"));
    }

    @Test
    public void testExtractStringField_emptyValue() {
        String json = "{\"key\": \"\"}";
        assertEquals("", JsonUtil.extractStringField(json, "key"));
    }

    @Test
    public void testExtractStringField_noFieldNames() {
        String json = "{\"key\": \"value\"}";
        assertNull(JsonUtil.extractStringField(json));
    }

    @Test
    public void testExtractStringField_multipleFields() {
        String json = "{\"a\": \"1\", \"b\": \"2\", \"c\": \"3\"}";
        assertEquals("2", JsonUtil.extractStringField(json, "b"));
    }

    @Test
    public void testExtractStringField_noColonAfterKey() {
        // Malformed JSON - key present but no colon
        String json = "{\"key\" \"value\"}";
        assertNull(JsonUtil.extractStringField(json, "key"));
    }

    @Test
    public void testExtractStringField_noQuoteAfterColon() {
        // Numeric value - no string quotes
        String json = "{\"count\": 42}";
        assertNull(JsonUtil.extractStringField(json, "count"));
    }

    // ---- extractSessionId ----

    @Test
    public void testExtractSessionId_snakeCase() {
        String json = "{\"session_id\": \"sess-001\", \"message\": \"hello\"}";
        assertEquals("sess-001", JsonUtil.extractSessionId(json));
    }

    @Test
    public void testExtractSessionId_camelCase() {
        String json = "{\"sessionId\": \"sess-002\", \"message\": \"hello\"}";
        assertEquals("sess-002", JsonUtil.extractSessionId(json));
    }

    @Test
    public void testExtractSessionId_notPresent() {
        String json = "{\"message\": \"hello\"}";
        assertNull(JsonUtil.extractSessionId(json));
    }

    @Test
    public void testExtractSessionId_prefersSnakeCase() {
        // When both present, snake_case is checked first
        String json = "{\"session_id\": \"snake\", \"sessionId\": \"camel\"}";
        assertEquals("snake", JsonUtil.extractSessionId(json));
    }

    @Test
    public void testExtractStringField_withWhitespace() {
        String json = "{ \"key\" :  \"value with spaces\" }";
        assertEquals("value with spaces", JsonUtil.extractStringField(json, "key"));
    }
}
