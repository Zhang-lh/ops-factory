/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.service.proactive.ProactiveDeliveryMarkers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test coverage for Agent Config Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class AgentConfigServiceTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private AgentConfigService service;

    private GatewayProperties properties;

    private Path gatewayRoot;

    private String previousGatewayConfigPath;

    /**
     * Sets the up.
     *
     * @throws IOException if the operation fails
     */
    @Before
    public void setUp() throws IOException {
        gatewayRoot = tempFolder.getRoot().toPath().resolve("gateway");
        Files.createDirectories(gatewayRoot.resolve("config"));
        Files.createDirectories(gatewayRoot.resolve("agents"));
        Files.createDirectories(gatewayRoot.resolve("users"));

        String configYaml =
            "port: 3000\n" + "residentInstances:\n" + "  enabled: true\n" + "  entries:\n" + "    - userId: admin\n"
                + "      agentIds: ['*']\n" + "    - userId: robby\n" + "      agentIds: ['test-agent']\n" + "agents:\n"
                + "  - id: test-agent\n" + "    name: Test Agent\n" + "  - id: kb-agent\n" + "    name: KB Agent\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        properties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(tempFolder.getRoot().getAbsolutePath());
        properties.setPaths(paths);
        previousGatewayConfigPath = System.getProperty("GATEWAY_CONFIG_PATH");
        System.setProperty("GATEWAY_CONFIG_PATH", gatewayRoot.resolve("config.yaml").toString());

        service = new AgentConfigService(properties);
        service.loadRegistry();
    }

    /**
     * Executes the tear down operation.
     */
    @After
    public void tearDown() {
        if (previousGatewayConfigPath == null) {
            System.clearProperty("GATEWAY_CONFIG_PATH");
        } else {
            System.setProperty("GATEWAY_CONFIG_PATH", previousGatewayConfigPath);
        }
    }

    /**
     * Tests load registry.
     */
    @Test
    public void testLoadRegistry() {
        List<AgentRegistryEntry> registry = service.getRegistry();
        assertEquals(2, registry.size());
        assertEquals("test-agent", registry.get(0).id());
        assertEquals("Test Agent", registry.get(0).name());
        assertEquals("kb-agent", registry.get(1).id());
        assertEquals("KB Agent", registry.get(1).name());
    }

    /**
     * Tests load registry when project-root points to external runtime.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistryWhenGatewayConfigPathPointsToGatewayConfig() throws IOException {
        Path externalRoot = tempFolder.getRoot().toPath().resolve("external-runtime");
        Path externalGatewayRoot = externalRoot.resolve("gateway");
        Files.createDirectories(externalGatewayRoot.resolve("agents"));
        Files.createDirectories(externalGatewayRoot.resolve("users"));
        Files.writeString(externalGatewayRoot.resolve("config.yaml"),
            "agents:\n" + "  - id: external-agent\n" + "    name: External Agent\n");

        GatewayProperties externalProperties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(externalRoot.toString());
        externalProperties.setPaths(paths);

        AgentConfigService externalService = new AgentConfigService(externalProperties);
        externalService.loadRegistry();

        assertEquals(1, externalService.getRegistry().size());
        assertEquals("external-agent", externalService.getRegistry().get(0).id());
        assertEquals(externalGatewayRoot.normalize(), externalService.getGatewayRoot());
    }

    /**
     * Tests load resident instances expands wildcard and specific agent.
     */
    @Test
    public void testLoadResidentInstances_expandsWildcardAndSpecificAgent() {
        assertTrue(service.isResidentInstance("test-agent", "admin"));
        assertTrue(service.isResidentInstance("kb-agent", "admin"));
        assertTrue(service.isResidentInstance("test-agent", "robby"));
        assertFalse(service.isResidentInstance("kb-agent", "robby"));
        assertEquals(3, service.getResidentInstances().size());
    }

    /**
     * Tests load resident instances ignores unknown and duplicate agents.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadResidentInstances_ignoresUnknownAndDuplicateAgents() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-a\n    name: Agent A\n"
            + "  - id: agent-b\n    name: Agent B\n" + "residentInstances:\n" + "  enabled: true\n" + "  entries:\n"
            + "    - userId: admin\n" + "      agentIds: ['agent-a', 'missing-agent', 'agent-a']\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertTrue(freshService.isResidentInstance("agent-a", "admin"));
        assertFalse(freshService.isResidentInstance("missing-agent", "admin"));
        assertEquals(1, freshService.getResidentInstances().size());
    }

    /**
     * Tests find agent exists.
     */
    @Test
    public void testFindAgent_exists() {
        AgentRegistryEntry entry = service.findAgent("test-agent");
        assertNotNull(entry);
        assertEquals("Test Agent", entry.name());
    }

    /**
     * Tests find agent not found.
     */
    @Test
    public void testFindAgent_notFound() {
        assertNull(service.findAgent("nonexistent"));
    }

    /**
     * Tests load agent config yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadAgentConfigYaml() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"), "GOOSE_PROVIDER: openai\nGOOSE_MODEL: gpt-4o\n");

        Map<String, Object> config = service.loadAgentConfigYaml("test-agent");
        assertEquals("openai", config.get("GOOSE_PROVIDER"));
        assertEquals("gpt-4o", config.get("GOOSE_MODEL"));
    }

    /**
     * Tests load agent config yaml no file.
     */
    @Test
    public void testLoadAgentConfigYaml_noFile() {
        Map<String, Object> config = service.loadAgentConfigYaml("nonexistent");
        assertTrue(config.isEmpty());
    }

    /**
     * An out-of-band config.yaml edit (changed mtime) is picked up without an explicit invalidateCache — the cache
     * self-heals, so a hand-edited provider/model takes effect without a gateway restart.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadAgentConfigYaml_picksUpOutOfBandFileEdit() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config.yaml");
        Files.writeString(configFile, "GOOSE_PROVIDER: custom_minimax-m2.7\n");
        Files.setLastModifiedTime(configFile, FileTime.fromMillis(1_000_000L));

        assertEquals("custom_minimax-m2.7", service.loadAgentConfigYaml("test-agent").get("GOOSE_PROVIDER"));

        // Hand-edit the file (new content + newer mtime), bypassing the gateway and invalidateCache.
        Files.writeString(configFile, "GOOSE_PROVIDER: custom_minimax-m3\n");
        Files.setLastModifiedTime(configFile, FileTime.fromMillis(2_000_000L));

        assertEquals("custom_minimax-m3", service.loadAgentConfigYaml("test-agent").get("GOOSE_PROVIDER"));
    }

    /**
     * When the file's mtime is unchanged, the cache is served without re-parsing (preserves the caching benefit).
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadAgentConfigYaml_servesCacheWhenMtimeUnchanged() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config.yaml");
        Files.writeString(configFile, "GOOSE_PROVIDER: openai\n");
        FileTime mtime = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(configFile, mtime);

        assertEquals("openai", service.loadAgentConfigYaml("test-agent").get("GOOSE_PROVIDER"));

        // Change content but restore the same mtime: the cache must still serve the prior parse (no re-read).
        Files.writeString(configFile, "GOOSE_PROVIDER: anthropic\n");
        Files.setLastModifiedTime(configFile, mtime);

        assertEquals("openai", service.loadAgentConfigYaml("test-agent").get("GOOSE_PROVIDER"));
    }

    /**
     * Tests list custom providers parses provider json files.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListCustomProviders_parsesProviderJson() throws IOException {
        Path providersDir =
            gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("custom_providers");
        Files.createDirectories(providersDir);
        Files.writeString(providersDir.resolve("custom_local.json"),
            "{\n" + "  \"name\": \"custom_local\",\n" + "  \"display_name\": \"Local\",\n"
                + "  \"engine\": \"openai\",\n"
                + "  \"models\": [{ \"name\": \"qwen-local\", \"context_limit\": 32768 }]\n" + "}\n");
        Files.writeString(providersDir.resolve("custom_remote.json"),
            "{\n" + "  \"name\": \"custom_remote\",\n" + "  \"display_name\": \"Remote\",\n"
                + "  \"engine\": \"openai\",\n"
                + "  \"models\": [{ \"name\": \"kimi-remote\", \"context_limit\": 128000 }]\n" + "}\n");

        List<Map<String, Object>> providers = service.listCustomProviders("test-agent");

        assertEquals(2, providers.size());
        List<String> names = providers.stream().map(provider -> String.valueOf(provider.get("name"))).toList();
        assertTrue(names.contains("custom_local"));
        assertTrue(names.contains("custom_remote"));
        Map<String,
            Object> local = providers.stream()
                .filter(provider -> "custom_local".equals(provider.get("name")))
                .findFirst()
                .orElseThrow();
        assertEquals("custom_local.json", local.get("fileName"));
    }

    /**
     * Tests extract agent config summary counts extension states.
     */
    @Test
    public void testExtractAgentConfigSummary_countsExtensions() {
        Map<String,
            Object> summary = service.extractAgentConfigSummary(Map.of("GOOSE_MODE", "auto", "GOOSE_DISABLE_KEYRING",
                "1", "GOOSE_TELEMETRY_ENABLED", false, "extensions", Map.of("developer", Map.of("enabled", true),
                    "memory", Map.of("enabled", false), "summarize", Map.of("enabled", "true"))));

        assertEquals("auto", summary.get("mode"));
        assertEquals("1", summary.get("disableKeyring"));
        assertEquals(false, summary.get("telemetryEnabled"));
        assertEquals(2, summary.get("enabledExtensions"));
        assertEquals(1, summary.get("disabledExtensions"));
    }

    /**
     * Tests update model config writes model keys to config yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateModelConfig_writesModelKeys() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir.resolve("custom_providers"));
        Files.writeString(configDir.resolve("custom_providers").resolve("custom_local.json"),
            "{\"name\":\"custom_local\",\"models\":[{\"name\":\"model-a\"}]}\n");
        Files.writeString(configDir.resolve("config.yaml"),
            "GOOSE_PROVIDER: old_provider\n" + "GOOSE_MODEL: old_model\n" + "GOOSE_TEMPERATURE: '0.2'\n");

        service.updateModelConfig("test-agent", Map.of("GOOSE_PROVIDER", "custom_local", "GOOSE_MODEL", "model-a",
            "GOOSE_TEMPERATURE", "0.4", "GOOSE_MAX_TOKENS", "4096"));

        Map<String, Object> config = service.loadAgentConfigYaml("test-agent");
        assertEquals("custom_local", config.get("GOOSE_PROVIDER"));
        assertEquals("model-a", config.get("GOOSE_MODEL"));
        assertEquals("0.4", config.get("GOOSE_TEMPERATURE"));
        assertEquals("4096", config.get("GOOSE_MAX_TOKENS"));
    }

    /**
     * Tests create custom provider writes normalized json.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateCustomProvider_writesProviderJson() throws IOException {
        Map<String,
            Object> provider = service.createCustomProvider("test-agent",
                Map.of("name", "custom_new", "display_name", "Custom New", "engine", "openai", "base_url",
                    "http://127.0.0.1:11434/v1/chat/completions", "api_key", "test-key", "models",
                    List.of(Map.of("name", "model-new", "context_limit", "32768")), "requires_auth", false,
                    "supports_streaming", true));

        assertEquals("custom_new", provider.get("name"));
        assertEquals("CUSTOM_NEW_API_KEY", provider.get("api_key_env"));
        assertEquals("openai", provider.get("engine"));
        assertEquals(true, provider.get("requires_auth"));
        assertEquals(true, provider.get("supports_streaming"));
        assertTrue(Files.exists(gatewayRoot.resolve("agents")
            .resolve("test-agent")
            .resolve("config")
            .resolve("custom_providers")
            .resolve("custom_new.json")));
        assertEquals("test-key", service.loadAgentSecretsYaml("test-agent").get("CUSTOM_NEW_API_KEY"));
        assertEquals(1, service.listCustomProviders("test-agent").size());
    }

    /**
     * Tests update custom provider preserves identity fields and updates editable values.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateCustomProvider_preservesIdentityFields() throws IOException {
        Path providersDir =
            gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("custom_providers");
        Files.createDirectories(providersDir);
        Files.writeString(providersDir.resolve("custom_existing.json"),
            "{\n" + "  \"name\": \"custom_existing\",\n" + "  \"display_name\": \"Existing Display\",\n"
                + "  \"engine\": \"anthropic\",\n" + "  \"description\": \"old description\",\n"
                + "  \"api_key_env\": \"CUSTOM_EXISTING_API_KEY\",\n"
                + "  \"base_url\": \"https://old.example.com/v1\",\n"
                + "  \"models\": [{ \"name\": \"old-model\", \"context_limit\": 1000 }]\n" + "}\n");

        Map<String,
            Object> provider = service.updateCustomProvider("test-agent", "custom_existing",
                Map.of("name", "custom_ignored", "display_name", "Ignored Display", "description", "new description",
                    "base_url", "https://new.example.com/v1", "api_key", "new-key", "models",
                    List.of(Map.of("name", "new-model", "context_limit", "64000"))));

        assertEquals("custom_existing", provider.get("name"));
        assertEquals("Existing Display", provider.get("display_name"));
        assertEquals("openai", provider.get("engine"));
        assertEquals("new description", provider.get("description"));
        assertEquals("https://new.example.com/v1", provider.get("base_url"));
        assertEquals("new-key", service.loadAgentSecretsYaml("test-agent").get("CUSTOM_EXISTING_API_KEY"));

        List<Map<String, Object>> providers = service.listCustomProviders("test-agent");
        assertEquals(1, providers.size());
        assertEquals("custom_existing.json", providers.get(0).get("fileName"));
    }

    /**
     * Tests create provider rejects name exceeding max length.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateCustomProvider_rejectsNameTooLong() throws IOException {
        String longName = "a".repeat(201);
        IllegalArgumentException ex = org.junit.Assert.assertThrows(
            IllegalArgumentException.class,
            () -> service.createCustomProvider("test-agent",
                Map.of("name", longName, "base_url", "http://localhost/v1", "models",
                    List.of(Map.of("name", "model-1")))));
        assertTrue(ex.getMessage().contains("must not exceed 200 characters"));
    }

    /**
     * Tests create provider rejects display name exceeding max length.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateCustomProvider_rejectsDisplayNameTooLong() throws IOException {
        String longDisplayName = "b".repeat(256);
        IllegalArgumentException ex = org.junit.Assert.assertThrows(
            IllegalArgumentException.class,
            () -> service.createCustomProvider("test-agent",
                Map.of("name", "custom-toolong-display", "display_name", longDisplayName,
                    "base_url", "http://localhost/v1", "models",
                    List.of(Map.of("name", "model-1")))));
        assertTrue(ex.getMessage().contains("must not exceed 255 characters"));
    }

    /**
     * Tests create provider rejects base URL exceeding max length.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateCustomProvider_rejectsBaseUrlTooLong() throws IOException {
        String longUrl = "http://localhost/" + "c".repeat(490);
        IllegalArgumentException ex = org.junit.Assert.assertThrows(
            IllegalArgumentException.class,
            () -> service.createCustomProvider("test-agent",
                Map.of("name", "custom-toolong-url", "base_url", longUrl, "models",
                    List.of(Map.of("name", "model-1")))));
        assertTrue(ex.getMessage().contains("must not exceed 500 characters"));
    }

    /**
     * Tests create provider rejects description exceeding max length.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateCustomProvider_rejectsDescriptionTooLong() throws IOException {
        String longDescription = "d".repeat(1001);
        IllegalArgumentException ex = org.junit.Assert.assertThrows(
            IllegalArgumentException.class,
            () -> service.createCustomProvider("test-agent",
                Map.of("name", "custom-toolong-desc", "description", longDescription,
                    "base_url", "http://localhost/v1", "models",
                    List.of(Map.of("name", "model-1")))));
        assertTrue(ex.getMessage().contains("must not exceed 1000 characters"));
    }

    /**
     * Tests create provider accepts fields at exactly max length.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateCustomProvider_acceptsFieldAtMaxLength() throws IOException {
        String maxName = "n".repeat(200);
        String maxDisplayName = "d".repeat(255);
        String maxUrl = "http://localhost/" + "u".repeat(483); // "http://localhost/" = 17 chars + 483 = 500
        String maxDescription = "x".repeat(1000);
        Map<String, Object> provider = service.createCustomProvider("test-agent",
            Map.of("name", maxName, "display_name", maxDisplayName, "description", maxDescription,
                "base_url", maxUrl, "models", List.of(Map.of("name", "model-1"))));

        assertEquals(maxName, provider.get("name"));
        assertEquals(maxDisplayName, provider.get("display_name"));
        assertEquals(maxDescription, provider.get("description"));
        assertTrue(provider.get("base_url").toString().startsWith("http://localhost/"));
    }

    /**
     * Tests update provider rejects fields exceeding max length.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateCustomProvider_rejectsFieldTooLong() throws IOException {
        Path providersDir =
            gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("custom_providers");
        Files.createDirectories(providersDir);
        Files.writeString(providersDir.resolve("custom_len_test.json"),
            "{\n  \"name\": \"custom_len_test\",\n  \"engine\": \"openai\",\n  \"api_key_env\": \"CUSTOM_LEN_TEST_API_KEY\",\n  \"base_url\": \"http://localhost/v1\",\n  \"models\": []\n}\n");

        String longDescription = "z".repeat(1001);
        IllegalArgumentException ex = org.junit.Assert.assertThrows(
            IllegalArgumentException.class,
            () -> service.updateCustomProvider("test-agent", "custom_len_test",
                Map.of("description", longDescription, "base_url", "http://localhost/v1", "models",
                    List.of(Map.of("name", "model-1")))));
        assertTrue(ex.getMessage().contains("must not exceed 1000 characters"));
    }

    /**
     * Tests read write agents md.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testReadWriteAgentsMd() throws IOException {
        Path agentDir = gatewayRoot.resolve("agents").resolve("test-agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("AGENTS.md"), "# Test Agent\n");

        String md = service.readAgentsMd("test-agent");
        assertEquals("# Test Agent\n", md);

        service.writeAgentsMd("test-agent", "# Updated\nNew content\n");
        String updated = service.readAgentsMd("test-agent");
        assertEquals("# Updated\nNew content\n", updated);
    }

    /**
     * Tests read agents md no file.
     */
    @Test
    public void testReadAgentsMd_noFile() {
        String md = service.readAgentsMd("nonexistent");
        assertEquals("", md);
    }

    /**
     * Tests list skills.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListSkills() throws IOException {
        Path skillsDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("skills");
        Files.createDirectories(skillsDir.resolve("skill-a"));
        Files.createDirectories(skillsDir.resolve("skill-b"));
        Files.writeString(skillsDir.resolve("readme.txt"), "not a skill");

        // Add SKILL.md with frontmatter to skill-a
        Files.writeString(skillsDir.resolve("skill-a").resolve("SKILL.md"),
            "---\nname: Skill A\ndescription: Description of skill A\npinned: true\ndisplay-order: "
                + "-100\n---\n# Skill A\n");

        List<Map<String, String>> skills = service.listSkills("test-agent");
        assertEquals(2, skills.size());

        List<String> names = skills.stream().map(s -> s.get("name")).toList();
        // parsed from frontmatter
        assertTrue(names.contains("Skill A"));
        // fallback to dir name
        assertTrue(names.contains("skill-b"));

        // Verify skill-a has parsed description
        Map<String, String> skillA =
            skills.stream().filter(s -> "Skill A".equals(s.get("name"))).findFirst().orElseThrow();
        assertEquals("Description of skill A", skillA.get("description"));
        assertEquals("skills/skill-a", skillA.get("path"));
        assertEquals("true", skillA.get("pinned"));
        assertEquals("-100", skillA.get("displayOrder"));

        // Verify skill-b has empty description (no SKILL.md)
        Map<String, String> skillB =
            skills.stream().filter(s -> "skill-b".equals(s.get("name"))).findFirst().orElseThrow();
        assertEquals("", skillB.get("description"));
    }

    /**
     * Tests list skills no skills dir.
     */
    @Test
    public void testListSkills_noSkillsDir() {
        List<Map<String, String>> skills = service.listSkills("nonexistent");
        assertTrue(skills.isEmpty());
    }

    /**
     * Tests create agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent() throws IOException {
        Path templateDir = gatewayRoot.resolve("agents").resolve("universal-agent").resolve("config");
        Files.createDirectories(templateDir);
        Files.writeString(templateDir.resolve("config.yaml"), "GOOSE_PROVIDER: anthropic\nGOOSE_MODEL: claude-3\n");

        Map<String, Object> result = service.createAgent("new-agent", "New Agent");
        assertEquals("new-agent", result.get("id"));
        assertEquals("New Agent", result.get("name"));
        assertEquals("anthropic", result.get("provider"));

        assertNotNull(service.findAgent("new-agent"));

        assertTrue(Files.exists(gatewayRoot.resolve("agents").resolve("new-agent").resolve("AGENTS.md")));
        assertTrue(
            Files.exists(gatewayRoot.resolve("agents").resolve("new-agent").resolve("config").resolve("config.yaml")));
        assertTrue(
            Files.exists(gatewayRoot.resolve("agents").resolve("new-agent").resolve("config").resolve("secrets.yaml")));
    }

    /**
     * Tests create agent duplicate id.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_duplicateId() throws IOException {
        service.createAgent("test-agent", "Duplicate");
    }

    /**
     * Tests create agent invalid id.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_invalidId() throws IOException {
        service.createAgent("INVALID!", "Bad ID");
    }

    /**
     * Tests delete agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteAgent() throws IOException {
        Path agentDir = gatewayRoot.resolve("agents").resolve("test-agent");
        Files.createDirectories(agentDir.resolve("config"));
        Files.writeString(agentDir.resolve("AGENTS.md"), "# Test\n");

        service.deleteAgent("test-agent");

        assertNull(service.findAgent("test-agent"));
        assertFalse(Files.exists(agentDir));
    }

    /**
     * Tests delete agent not found.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteAgent_notFound() throws IOException {
        service.deleteAgent("nonexistent");
    }

    /**
     * Tests load agent secrets yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadAgentSecretsYaml() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("secrets.yaml"), "OPENAI_API_KEY: sk-test123\nANTHROPIC_KEY: ak-test456\n");

        Map<String, Object> secrets = service.loadAgentSecretsYaml("test-agent");
        assertEquals("sk-test123", secrets.get("OPENAI_API_KEY"));
        assertEquals("ak-test456", secrets.get("ANTHROPIC_KEY"));
    }

    /**
     * Tests load agent secrets yaml no file.
     */
    @Test
    public void testLoadAgentSecretsYaml_noFile() {
        Map<String, Object> secrets = service.loadAgentSecretsYaml("nonexistent");
        assertTrue(secrets.isEmpty());
    }

    /**
     * Tests create agent duplicate name.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_duplicateName() throws IOException {
        service.createAgent("another-agent", "Test Agent");
    }

    /**
     * Tests create agent no template.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent_noTemplate() throws IOException {
        Map<String, Object> result = service.createAgent("new-agent", "New Agent");
        assertEquals("new-agent", result.get("id"));
        assertEquals("New Agent", result.get("name"));
        assertEquals("openai", result.get("provider"));
    }

    /**
     * Tests getters resolve correct paths.
     */
    @Test
    public void testGettersResolveCorrectPaths() {
        Path agentsDir = service.getAgentsDir();
        assertTrue(agentsDir.endsWith(Path.of("gateway", "agents")));

        Path usersDir = service.getUsersDir();
        assertTrue(usersDir.endsWith(Path.of("gateway", "users")));
    }

    /**
     * Tests get agent config dir.
     */
    @Test
    public void testGetAgentConfigDir() {
        Path configDir = service.getAgentConfigDir("test-agent");
        assertTrue(configDir.endsWith(Path.of("agents", "test-agent", "config")));
    }

    /**
     * Tests delete agent removes from yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteAgent_removesFromYaml() throws IOException {
        Path agentDir = gatewayRoot.resolve("agents").resolve("test-agent");
        Files.createDirectories(agentDir.resolve("config"));
        Files.writeString(agentDir.resolve("AGENTS.md"), "# Test\n");

        int sizeBefore = service.getRegistry().size();
        service.deleteAgent("test-agent");
        assertEquals(sizeBefore - 1, service.getRegistry().size());

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertNull(freshService.findAgent("test-agent"));
    }

    /**
     * Tests registry is unmodifiable.
     */
    @Test
    public void testRegistryIsUnmodifiable() {
        List<AgentRegistryEntry> registry = service.getRegistry();
        try {
            registry.add(new AgentRegistryEntry("illegal", "Illegal"));
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    /**
     * Tests create agent updates agents yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent_updatesAgentsYaml() throws IOException {
        Path templateDir = gatewayRoot.resolve("agents").resolve("universal-agent").resolve("config");
        Files.createDirectories(templateDir);
        Files.writeString(templateDir.resolve("config.yaml"), "GOOSE_PROVIDER: anthropic\nGOOSE_MODEL: claude-3\n");

        service.createAgent("created-agent", "Created Agent");

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertNotNull(freshService.findAgent("created-agent"));
    }

    /**
     * Tests create agent single char id.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_singleCharId() throws IOException {
        service.createAgent("a", "Single Char");
    }

    /**
     * Tests create agent skills directory created.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent_skillsDirectoryCreated() throws IOException {
        service.createAgent("new-agent", "New Agent");
        Path skillsDir = gatewayRoot.resolve("agents").resolve("new-agent").resolve("config").resolve("skills");
        assertTrue(Files.isDirectory(skillsDir));
    }

    /**
     * Tests load registry empty agents yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_emptyAgentsYaml() throws IOException {
        Files.writeString(gatewayRoot.resolve("config.yaml"), "");
        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertTrue(freshService.getRegistry().isEmpty());
    }

    /**
     * Tests load registry no agents key.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_noAgentsKey() throws IOException {
        Files.writeString(gatewayRoot.resolve("config.yaml"), "other: value\n");
        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertTrue(freshService.getRegistry().isEmpty());
    }

    /**
     * Tests load registry enabled false excludes agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_enabledFalseExcludesAgent() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-a\n    name: Agent A\n"
            + "  - id: agent-b\n    name: Agent B\n    enabled: false\n" + "  - id: agent-c\n    name: Agent C\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        List<AgentRegistryEntry> registry = freshService.getRegistry();
        assertEquals(2, registry.size());
        assertEquals("agent-a", registry.get(0).id());
        assertEquals("agent-c", registry.get(1).id());
        assertNull(freshService.findAgent("agent-b"));
    }

    /**
     * Tests load registry enabled true includes agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_enabledTrueIncludesAgent() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-a\n    name: Agent A\n    enabled: true\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertEquals(1, freshService.getRegistry().size());
        assertNotNull(freshService.findAgent("agent-a"));
    }

    /**
     * Tests load registry enabled omitted defaults to true.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_enabledOmittedDefaultsToTrue() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-no-enabled\n    name: No Enabled Field\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertEquals(1, freshService.getRegistry().size());
        assertNotNull(freshService.findAgent("agent-no-enabled"));
    }

    /**
     * Tests load registry all disabled results in empty registry.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_allDisabledResultsInEmptyRegistry() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-x\n    name: Agent X\n    enabled: false\n"
            + "  - id: agent-y\n    name: Agent Y\n    enabled: false\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertTrue(freshService.getRegistry().isEmpty());
    }

    // ── Memory file tests (per-user) ────────────────────────────────

    private static final String USER_A = "user-a";

    private static final String USER_B = "user-b";

    /** Per-user memory dir, matching goose's {@code XDG_CONFIG_HOME/goose/memory} (XDG = data/config). */
    private Path userMemoryDir(String userId, String agentId) {
        return gatewayRoot.resolve("users")
            .resolve(userId)
            .resolve("agents")
            .resolve(agentId)
            .resolve("data")
            .resolve("config")
            .resolve("goose")
            .resolve("memory");
    }

    /**
     * Tests list memory files empty.
     */
    @Test
    public void testListMemoryFiles_empty() {
        List<Map<String, String>> files = service.listMemoryFiles(USER_A, "test-agent");
        assertTrue(files.isEmpty());
    }

    /**
     * Tests list memory files with files.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListMemoryFiles_withFiles() throws IOException {
        Path memoryDir = userMemoryDir(USER_A, "test-agent");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("development.txt"), "# tools\nuse black for formatting");
        Files.writeString(memoryDir.resolve("personal.txt"), "prefer Chinese replies");

        List<Map<String, String>> files = service.listMemoryFiles(USER_A, "test-agent");
        assertEquals(2, files.size());

        List<String> categories = files.stream().map(f -> f.get("category")).toList();
        assertTrue(categories.contains("development"));
        assertTrue(categories.contains("personal"));

        Map<String, String> dev =
            files.stream().filter(f -> "development".equals(f.get("category"))).findFirst().orElseThrow();
        assertEquals("# tools\nuse black for formatting", dev.get("content"));
    }

    /**
     * Tests list memory files ignores non txt.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListMemoryFiles_ignoresNonTxt() throws IOException {
        Path memoryDir = userMemoryDir(USER_A, "test-agent");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("valid.txt"), "content");
        Files.writeString(memoryDir.resolve("ignored.md"), "markdown");

        List<Map<String, String>> files = service.listMemoryFiles(USER_A, "test-agent");
        assertEquals(1, files.size());
        assertEquals("valid", files.get(0).get("category"));
    }

    /**
     * Tests read memory file exists.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testReadMemoryFile_exists() throws IOException {
        Path memoryDir = userMemoryDir(USER_A, "test-agent");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("dev.txt"), "hello world");

        String content = service.readMemoryFile(USER_A, "test-agent", "dev");
        assertEquals("hello world", content);
    }

    /**
     * Tests read memory file not found.
     */
    @Test
    public void testReadMemoryFile_notFound() {
        String content = service.readMemoryFile(USER_A, "test-agent", "nonexistent");
        assertNull(content);
    }

    /**
     * Tests write memory file creates directory and file.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteMemoryFile_createsDirectoryAndFile() throws IOException {
        service.writeMemoryFile(USER_A, "test-agent", "new-category", "some content");

        Path file = userMemoryDir(USER_A, "test-agent").resolve("new-category.txt");
        assertTrue(Files.exists(file));
        assertEquals("some content", Files.readString(file));
    }

    /**
     * Tests write memory file updates existing.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteMemoryFile_updatesExisting() throws IOException {
        service.writeMemoryFile(USER_A, "test-agent", "cat", "v1");
        service.writeMemoryFile(USER_A, "test-agent", "cat", "v2");

        assertEquals("v2", service.readMemoryFile(USER_A, "test-agent", "cat"));
    }

    /**
     * Tests write memory file too large.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWriteMemoryFile_tooLarge() throws IOException {
        String largeContent = "x".repeat(101 * 1024);
        service.writeMemoryFile(USER_A, "test-agent", "big", largeContent);
    }

    /**
     * Tests delete memory file success.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteMemoryFile_success() throws IOException {
        Path memoryDir = userMemoryDir(USER_A, "test-agent");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("toDelete.txt"), "bye");

        service.deleteMemoryFile(USER_A, "test-agent", "toDelete");
        assertFalse(Files.exists(memoryDir.resolve("toDelete.txt")));
    }

    /**
     * Tests delete memory file not found.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMemoryFile_notFound() throws IOException {
        service.deleteMemoryFile(USER_A, "test-agent", "nonexistent");
    }

    /**
     * Tests write and read round trip.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteAndReadRoundTrip() throws IOException {
        String content = "# formatting tools\nuse black\n\n# deployment\nuse k8s";
        service.writeMemoryFile(USER_A, "test-agent", "dev", content);
        assertEquals(content, service.readMemoryFile(USER_A, "test-agent", "dev"));
    }

    /**
     * Tests list memory files after write and delete.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListMemoryFiles_afterWriteAndDelete() throws IOException {
        service.writeMemoryFile(USER_A, "test-agent", "a", "content-a");
        service.writeMemoryFile(USER_A, "test-agent", "b", "content-b");
        assertEquals(2, service.listMemoryFiles(USER_A, "test-agent").size());

        service.deleteMemoryFile(USER_A, "test-agent", "a");
        List<Map<String, String>> remaining = service.listMemoryFiles(USER_A, "test-agent");
        assertEquals(1, remaining.size());
        assertEquals("b", remaining.get(0).get("category"));
    }

    /**
     * Memory is per-user: two users of the same agent must not see each other's memory.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testMemoryIsolatedPerUser() throws IOException {
        service.writeMemoryFile(USER_A, "test-agent", "shared-name", "from A");
        service.writeMemoryFile(USER_B, "test-agent", "shared-name", "from B");

        assertEquals("from A", service.readMemoryFile(USER_A, "test-agent", "shared-name"));
        assertEquals("from B", service.readMemoryFile(USER_B, "test-agent", "shared-name"));

        // A deletes its copy; B's copy is untouched.
        service.deleteMemoryFile(USER_A, "test-agent", "shared-name");
        assertNull(service.readMemoryFile(USER_A, "test-agent", "shared-name"));
        assertEquals("from B", service.readMemoryFile(USER_B, "test-agent", "shared-name"));
    }

    /**
     * The per-user memory dir must resolve under {@code data/config/goose/memory}, which is what
     * goose loads via {@code XDG_CONFIG_HOME/goose/memory} (XDG_CONFIG_HOME = runtimeRoot/data/config).
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteMemoryFile_landsAtXdgConfigHomePath() throws IOException {
        service.writeMemoryFile(USER_A, "test-agent", "dev", "x");

        Path expected = gatewayRoot.resolve("users")
            .resolve(USER_A)
            .resolve("agents")
            .resolve("test-agent")
            .resolve("data")
            .resolve("config")
            .resolve("goose")
            .resolve("memory")
            .resolve("dev.txt");
        assertTrue(Files.exists(expected));
    }

    private Path writeSeed(String agentId, String category, String content) throws IOException {
        Path seedDir =
            gatewayRoot.resolve("agents").resolve(agentId).resolve("config").resolve("goose").resolve("memory");
        Files.createDirectories(seedDir);
        Path file = seedDir.resolve(category + ".txt");
        Files.writeString(file, content);
        return file;
    }

    private void writeScheduleSeed(String agentId) throws IOException {
        Path seedDir = gatewayRoot.resolve("agents").resolve(agentId).resolve("config").resolve("seed-schedules");
        Files.createDirectories(seedDir);
        Files.writeString(seedDir.resolve("seed.json"),
            "[{\"id\":\"ticket-watch-loop\",\"cron\":\"0 */30 * * * *\",\"recipe\":\"watch.yaml\"},"
                + "{\"id\":\"mem-maint\",\"cron\":\"0 0 12 * * *\",\"recipe\":\"mem.yaml\"}]");
        Files.writeString(seedDir.resolve("watch.yaml"), "version: 1.0.0\ntitle: watch\ninstructions: run watch\n");
        Files.writeString(seedDir.resolve("mem.yaml"), "version: 1.0.0\ntitle: mem\ninstructions: maintain memory\n");
    }

    private void writeScheduleSeedWithDeliver(String agentId) throws IOException {
        Path seedDir = gatewayRoot.resolve("agents").resolve(agentId).resolve("config").resolve("seed-schedules");
        Files.createDirectories(seedDir);
        Files.writeString(seedDir.resolve("seed.json"),
            "[{\"id\":\"ticket-watch-loop\",\"cron\":\"0 */30 * * * *\",\"recipe\":\"watch.yaml\",\"deliver\":\"im\"},"
                + "{\"id\":\"mem-maint\",\"cron\":\"0 0 12 * * *\",\"recipe\":\"mem.yaml\"}]");
        Files.writeString(seedDir.resolve("watch.yaml"), "version: 1.0.0\ntitle: watch\ninstructions: run watch\n");
        Files.writeString(seedDir.resolve("mem.yaml"), "version: 1.0.0\ntitle: mem\ninstructions: maintain memory\n");
    }

    private Path scheduleJsonPath(String userId, String agentId) {
        return service.getUserAgentDir(userId, agentId).resolve("data").resolve("schedule.json");
    }

    private List<Map<String, Object>> readScheduledJobs(String userId, String agentId) throws IOException {
        Path path = scheduleJsonPath(userId, agentId);
        if (Files.notExists(path)) {
            return List.of();
        }
        return new ObjectMapper().readValue(Files.readString(path), new TypeReference<List<Map<String, Object>>>() { });
    }

    /**
     * On first access, the user's memory is seeded from the shared agent seed; the seeded entries
     * are visible through the memory tab (listMemoryFiles).
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureMemorySeeded_seedsOnFirstAccess() throws IOException {
        writeSeed("test-agent", "sla-criteria", "P1 responds in 15m");
        writeSeed("test-agent", "ops-preferences", "no changes during freeze");

        List<Map<String, String>> files = service.listMemoryFiles(USER_A, "test-agent");

        List<String> categories = files.stream().map(f -> f.get("category")).toList();
        assertEquals(2, files.size());
        assertTrue(categories.contains("sla-criteria"));
        assertTrue(categories.contains("ops-preferences"));
        assertTrue(Files.exists(userMemoryDir(USER_A, "test-agent").resolve("sla-criteria.txt")));
    }

    /**
     * Seeding is one-time: after a user clears all seeded memory, it is NOT re-seeded on later
     * access. Guarded by the {@code data/.memory-seeded} marker, not by emptiness.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureMemorySeeded_isOneTime_notReseededAfterClear() throws IOException {
        writeSeed("test-agent", "sla-criteria", "P1 responds in 15m");

        // First access seeds.
        assertEquals(1, service.listMemoryFiles(USER_A, "test-agent").size());

        // User deletes the seeded entry.
        service.deleteMemoryFile(USER_A, "test-agent", "sla-criteria");
        assertTrue(service.listMemoryFiles(USER_A, "test-agent").isEmpty());

        // A brand-new seed file appearing later must NOT resurrect into this user.
        writeSeed("test-agent", "late-seed", "added after first seed");
        assertTrue(service.listMemoryFiles(USER_A, "test-agent").isEmpty());
    }

    /**
     * Seeding never overwrites a user's pre-existing memory of the same category.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureMemorySeeded_doesNotOverwriteExisting() throws IOException {
        writeSeed("test-agent", "sla-criteria", "seed value");
        // Pre-populate the user's memory with a file of the SAME category as a seed file.
        Path userMemory = userMemoryDir(USER_A, "test-agent");
        Files.createDirectories(userMemory);
        Files.writeString(userMemory.resolve("sla-criteria.txt"), "user edit");

        service.ensureMemorySeeded(USER_A, "test-agent");

        // The user's existing entry is kept verbatim; the seed does not clobber it.
        assertEquals("user edit", service.readMemoryFile(USER_A, "test-agent", "sla-criteria"));
    }

    /**
     * Seeding is a no-op (but still marks done) when the agent has no shared seed.
     */
    @Test
    public void testEnsureMemorySeeded_noSeedSource() {
        service.ensureMemorySeeded(USER_A, "test-agent");
        assertTrue(service.listMemoryFiles(USER_A, "test-agent").isEmpty());
    }

    /**
     * Writing a memory before any seed exists still seeds first, so the user keeps both the agent's
     * presets and their new entry (covers the tab-before-spawn ordering).
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteMemoryFile_seedsBeforeWrite() throws IOException {
        writeSeed("test-agent", "sla-criteria", "P1 responds in 15m");

        service.writeMemoryFile(USER_A, "test-agent", "my-note", "user edit");

        List<String> categories =
            service.listMemoryFiles(USER_A, "test-agent").stream().map(f -> f.get("category")).toList();
        assertTrue(categories.contains("sla-criteria"));
        assertTrue(categories.contains("my-note"));
    }

    /**
     * On first spawn, the agent's default scheduled tasks are seeded: each recipe is copied to
     * {@code data/scheduled_recipes/<id>.yaml} and an active job is appended to {@code data/schedule.json}.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureSchedulesSeeded_seedsOnFirstSpawn() throws IOException {
        writeScheduleSeed("test-agent");

        service.ensureSchedulesSeeded(USER_A, "test-agent");

        Path recipesDir = service.getUserAgentDir(USER_A, "test-agent").resolve("data").resolve("scheduled_recipes");
        assertTrue(Files.exists(recipesDir.resolve("ticket-watch-loop.yaml")));
        assertTrue(Files.exists(recipesDir.resolve("mem-maint.yaml")));

        List<Map<String, Object>> jobs = readScheduledJobs(USER_A, "test-agent");
        assertEquals(2, jobs.size());
        Map<String, Object> watch =
            jobs.stream().filter(j -> "ticket-watch-loop".equals(j.get("id"))).findFirst().orElseThrow();
        assertEquals("0 */30 * * * *", watch.get("cron"));
        assertEquals(Boolean.FALSE, watch.get("paused"));
        String source = (String) watch.get("source");
        assertTrue("Source path should contain scheduled_recipes/ticket-watch-loop.yaml",
            source.contains("scheduled_recipes") && source.contains("ticket-watch-loop.yaml"));
        assertTrue(Files.exists(
            service.getUserAgentDir(USER_A, "test-agent").resolve("data").resolve(".schedules-seeded")));
    }

    /**
     * Seeding is one-time: after a user deletes a seeded task, a later spawn does NOT resurrect it.
     * Guarded by the {@code data/.schedules-seeded} marker, not by emptiness.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureSchedulesSeeded_isOneTime_notReseededAfterDelete() throws IOException {
        writeScheduleSeed("test-agent");
        service.ensureSchedulesSeeded(USER_A, "test-agent");
        assertEquals(2, readScheduledJobs(USER_A, "test-agent").size());

        // User removes every task via the Scheduler tab (goosed rewrites schedule.json).
        Files.writeString(scheduleJsonPath(USER_A, "test-agent"), "[]");

        service.ensureSchedulesSeeded(USER_A, "test-agent");
        assertTrue(readScheduledJobs(USER_A, "test-agent").isEmpty());
    }

    /**
     * Seeding preserves schedules a user (or goosed) already created — it appends only the missing ids.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureSchedulesSeeded_mergesWithExistingSchedules() throws IOException {
        writeScheduleSeed("test-agent");
        Path dataDir = service.getUserAgentDir(USER_A, "test-agent").resolve("data");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("schedule.json"),
            "[{\"id\":\"say-hello\",\"source\":\"/x/say-hello.yaml\",\"cron\":\"0 0 9 * * *\",\"paused\":true}]");

        service.ensureSchedulesSeeded(USER_A, "test-agent");

        List<String> ids = readScheduledJobs(USER_A, "test-agent").stream().map(j -> (String) j.get("id")).toList();
        assertEquals(3, ids.size());
        assertTrue(ids.contains("say-hello"));
        assertTrue(ids.contains("ticket-watch-loop"));
        assertTrue(ids.contains("mem-maint"));
    }

    /**
     * Re-running the seed never duplicates jobs (idempotent within a single seeded lifetime).
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureSchedulesSeeded_idempotentNoDuplicates() throws IOException {
        writeScheduleSeed("test-agent");

        service.ensureSchedulesSeeded(USER_A, "test-agent");
        service.ensureSchedulesSeeded(USER_A, "test-agent");

        assertEquals(2, readScheduledJobs(USER_A, "test-agent").size());
    }

    /**
     * A seed entry's optional {@code deliver} field is materialized into the per-user delivery marker: the
     * opted-in task (ticket-watch-loop) is marked for IM push, while a task without {@code deliver} is not.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureSchedulesSeeded_seedsDeliverMarkerForOptedInTasks() throws IOException {
        writeScheduleSeedWithDeliver("test-agent");

        service.ensureSchedulesSeeded(USER_A, "test-agent");

        Path deliveryFile = service.getUserAgentDir(USER_A, "test-agent")
            .resolve(ProactiveDeliveryMarkers.DIR).resolve(ProactiveDeliveryMarkers.DELIVERY_FILE);
        assertEquals(ProactiveDeliveryMarkers.DELIVER_IM,
            ProactiveDeliveryMarkers.getDeliver(deliveryFile, "ticket-watch-loop"));
        assertNull("a task without a deliver field must not be marked for IM",
            ProactiveDeliveryMarkers.getDeliver(deliveryFile, "mem-maint"));
    }

    /**
     * Seeding is a no-op (but still marks done) when the agent ships no seed-schedules manifest.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testEnsureSchedulesSeeded_noSeedSource() throws IOException {
        service.ensureSchedulesSeeded(USER_A, "test-agent");

        assertTrue(readScheduledJobs(USER_A, "test-agent").isEmpty());
        assertTrue(Files.exists(
            service.getUserAgentDir(USER_A, "test-agent").resolve("data").resolve(".schedules-seeded")));
    }

    /**
     * Guards the SHIPPED fo-copilot seed manifest itself: it must parse, every entry must have a
     * filename-safe id + cron, ids must be unique, and each referenced recipe file must exist. Catches a
     * typo'd recipe reference or unsafe id that the synthetic-fixture tests above cannot — that would
     * otherwise ship a permanently dead scheduled task.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testShippedFoCopilotSeed_parsesAndReferencesExistingRecipes() throws IOException {
        Path seedDir = locateShippedSeedDir("fo-copilot");
        assertNotNull("shipped fo-copilot seed-schedules dir not found from " + System.getProperty("user.dir"),
            seedDir);

        List<Map<String, String>> seeds = new ObjectMapper().readValue(
            Files.readString(seedDir.resolve("seed.json")), new TypeReference<List<Map<String, String>>>() { });
        assertFalse("shipped seed.json must declare at least one task", seeds.isEmpty());

        Set<String> ids = new HashSet<>();
        for (Map<String, String> seed : seeds) {
            String id = seed.get("id");
            assertNotNull("seed entry missing id", id);
            assertTrue("id not filename-safe: " + id, id.matches("[A-Za-z0-9._-]+"));
            assertNotNull("seed entry missing cron: " + id, seed.get("cron"));
            assertTrue("duplicate seed id: " + id, ids.add(id));
            String recipe = seed.get("recipe");
            assertNotNull("seed entry missing recipe: " + id, recipe);
            assertTrue("recipe file missing for " + id + ": " + recipe, Files.isRegularFile(seedDir.resolve(recipe)));
        }
    }

    private Path locateShippedSeedDir(String agentId) {
        Path cwd = Path.of(System.getProperty("user.dir"));
        for (Path base : List.of(cwd, cwd.getParent() == null ? cwd : cwd.getParent())) {
            for (String prefix : List.of("agents", "gateway/agents")) {
                Path candidate = base.resolve(prefix).resolve(agentId).resolve("config").resolve("seed-schedules");
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Tests write knowledge cli settings stores source id and relative artifacts root.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteKnowledgeCliSettings_storesSourceIdAndRelArtifactsRoot() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("qa-cli-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"), "extensions:\n" + "  knowledge-cli:\n"
            + "    x-opsfactory:\n" + "      scope:\n" + "        rootDir: ../data\n");

        service.writeMcpSettings("qa-cli-agent", "knowledge-cli", Map.of("sourceId", "src_123"));

        Map<String, Object> settings = service.readMcpSettings("qa-cli-agent", "knowledge-cli");
        assertEquals("src_123", settings.get("sourceId"));
        assertEquals("../../../../knowledge-service/data/artifacts/src_123", settings.get("rootDir"));
        assertEquals(configDir.resolve("../../../../knowledge-service/data/artifacts/src_123").normalize(),
            service.getKnowledgeCliRootDir("qa-cli-agent"));
    }

    /**
     * Tests write knowledge cli settings uses configured artifacts root.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteKnowledgeCliSettings_usesConfiguredArtifactsRoot() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("qa-cli-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"), "extensions:\n" + "  knowledge-cli:\n"
            + "    x-opsfactory:\n" + "      scope:\n" + "        rootDir: ../data\n");
        Path externalArtifactsRoot = tempFolder.getRoot().toPath().getParent().resolve("external-artifacts");
        properties.getKnowledge().setArtifactsRoot(externalArtifactsRoot.toString());

        service.writeMcpSettings("qa-cli-agent", "knowledge-cli", Map.of("sourceId", "src_external"));

        Map<String, Object> settings = service.readMcpSettings("qa-cli-agent", "knowledge-cli");
        assertEquals("src_external", settings.get("sourceId"));
        assertEquals(externalArtifactsRoot.resolve("src_external").normalize().toString(), settings.get("rootDir"));
    }

    /**
     * Tests write knowledge cli settings clear resets default root.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteKnowledgeCliSettings_clearResetsDefaultRoot() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("qa-cli-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"),
            "extensions:\n" + "  knowledge-cli:\n" + "    x-opsfactory:\n" + "      scope:\n"
                + "        sourceId: src_old\n"
                + "        rootDir: ../../../../knowledge-service/data/artifacts/src_old\n");

        service.writeMcpSettings("qa-cli-agent", "knowledge-cli", Map.of("sourceId", ""));

        Map<String, Object> settings = service.readMcpSettings("qa-cli-agent", "knowledge-cli");
        assertNull(settings.get("sourceId"));
        assertEquals("../data", settings.get("rootDir"));
    }

    /**
     * Tests load registry disabled agent is excluded from resident expansion.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_disabledAgentIsExcludedFromResidentExpansion() throws IOException {
        String configYaml = "agents:\n" + "  - id: visible-agent\n    name: Visible Agent\n"
            + "  - id: hidden-agent\n    name: Hidden Agent\n    enabled: false\n" + "residentInstances:\n"
            + "  enabled: true\n" + "  entries:\n" + "    - userId: admin\n" + "      agentIds: ['*']\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertEquals(1, freshService.getRegistry().size());
        assertNotNull(freshService.findAgent("visible-agent"));
        assertNull(freshService.findAgent("hidden-agent"));
        assertTrue(freshService.isResidentInstance("visible-agent", "admin"));
        assertFalse(freshService.isResidentInstance("hidden-agent", "admin"));
    }
}
