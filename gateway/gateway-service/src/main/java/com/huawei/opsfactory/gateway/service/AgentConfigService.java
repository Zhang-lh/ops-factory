/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.common.model.ResidentInstanceTarget;
import com.huawei.opsfactory.gateway.common.util.FileUtil;
import com.huawei.opsfactory.gateway.common.util.YamlLoader;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.service.proactive.ProactiveDeliveryMarkers;
import com.huawei.opsfactory.gateway.service.proactive.ProactiveStorage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Manages agent configuration, registry, skills, memory files, and MCP settings.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class AgentConfigService {
    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String KNOWLEDGE_SERVICE_MCP = "knowledge-service";

    private static final String KNOWLEDGE_CLI_MCP = "knowledge-cli";

    private static final String DEFAULT_KNOWLEDGE_CLI_ROOT_DIR = "../data";

    private static final Pattern PROVIDER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    // Provider field length limits — must stay in sync with CreateProviderModal.tsx maxLength
    private static final int PROVIDER_NAME_MAX_LENGTH = 200;
    private static final int PROVIDER_DISPLAY_NAME_MAX_LENGTH = 255;
    private static final int PROVIDER_BASE_URL_MAX_LENGTH = 500;
    private static final int PROVIDER_API_KEY_MAX_LENGTH = 5000;
    private static final int PROVIDER_MODEL_NAME_MAX_LENGTH = 255;
    private static final int PROVIDER_DESCRIPTION_MAX_LENGTH = 1000;
    
    private static final List<String> MODEL_CONFIG_KEYS = List.of("GOOSE_PROVIDER", "GOOSE_MODEL", "GOOSE_FAST_MODEL",
        "GOOSE_MODE", "GOOSE_CONTEXT_LIMIT", "GOOSE_MAX_TOKENS", "GOOSE_TEMPERATURE", "GOOSE_CONTEXT_STRATEGY",
        "GOOSE_AUTO_COMPACT_THRESHOLD", "GOOSE_MAX_TURNS");

    private final GatewayProperties properties;

    private final CopyOnWriteArrayList<AgentRegistryEntry> registry = new CopyOnWriteArrayList<>();

    private final CopyOnWriteArrayList<ResidentInstanceTarget> residentInstances = new CopyOnWriteArrayList<>();

    // Parsed YAML cached together with the source file's last-modified time. The cache is validated against the
    // current mtime on every read, so out-of-band edits (e.g. hand-editing config.yaml while the gateway runs, not
    // just gateway-mediated changes) are picked up automatically — while still avoiding a re-parse when unchanged.
    private final ConcurrentHashMap<String, CachedYaml> configCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, CachedYaml> secretsCache = new ConcurrentHashMap<>();

    private final Set<String> residentInstanceKeys = ConcurrentHashMap.newKeySet();

    private Path gatewayRoot;

    /**
     * Creates the agent config service instance.
     *
     * @param properties gateway configuration properties
     */
    public AgentConfigService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Loads the agent registry from the gateway config.yaml at startup.
     */
    @PostConstruct
    public void loadRegistry() {
        registry.clear();
        residentInstances.clear();
        residentInstanceKeys.clear();

        this.gatewayRoot = properties.getGatewayRootPath();
        Path configYaml = gatewayRoot.resolve("config.yaml");
        Map<String, Object> data = YamlLoader.load(configYaml);

        Object agentsObj = data.get("agents");
        if (agentsObj instanceof List<?> agentsList) {
            for (Object item : agentsList) {
                if (item instanceof Map<?, ?> rawMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) rawMap;
                    boolean enabled = !Boolean.FALSE.equals(map.get("enabled"));
                    if (!enabled) {
                        log.info("Skipping disabled agent: {}", map.get("id"));
                        continue;
                    }
                    String id = YamlLoader.getString(map, "id", "");
                    String name = YamlLoader.getString(map, "name", "");
                    registry.add(new AgentRegistryEntry(id, name));
                }
            }
        }
        loadResidentInstances(data);
        log.info("Loaded {} agents from registry", registry.size());
        log.info("Loaded {} resident instance targets", residentInstances.size());
    }

    /**
     * Returns an unmodifiable view of the current agent registry.
     *
     * @return unmodifiable list of registered agent entries
     */
    public List<AgentRegistryEntry> getRegistry() {
        return Collections.unmodifiableList(registry);
    }

    /**
     * Returns an unmodifiable view of the configured resident instance targets.
     *
     * @return unmodifiable list of resident instance targets
     */
    public List<ResidentInstanceTarget> getResidentInstances() {
        return Collections.unmodifiableList(residentInstances);
    }

    /**
     * Checks whether the given agent-user pair is a resident instance.
     *
     * @param agentId agent instance identifier
     * @param userId user identifier
     * @return {@code true} if the agent-user pair is configured as a resident instance
     */
    public boolean isResidentInstance(String agentId, String userId) {
        return residentInstanceKeys.contains(ManagedInstance.buildKey(agentId, userId));
    }

    /**
     * Finds an agent registry entry by its ID.
     *
     * @param agentId agent instance identifier
     * @return the matching registry entry, or {@code null} if not found
     */
    public AgentRegistryEntry findAgent(String agentId) {
        for (AgentRegistryEntry entry : registry) {
            if (entry.id().equals(agentId)) {
                return entry;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void loadResidentInstances(Map<String, Object> data) {
        Object residentObj = data.get("residentInstances");
        if (!(residentObj instanceof Map<?, ?> residentMapObj)) {
            return;
        }
        Map<String, Object> residentMap = (Map<String, Object>) residentMapObj;
        if (Boolean.FALSE.equals(residentMap.get("enabled"))) {
            return;
        }

        Object entriesObj = residentMap.get("entries");
        if (!(entriesObj instanceof List<?> entries)) {
            return;
        }

        List<String> configuredAgentIds = registry.stream().map(AgentRegistryEntry::id).toList();
        for (Object entryObj : entries) {
            ResidentEntry residentEntry = parseResidentEntry(entryObj);
            if (residentEntry == null) {
                continue;
            }
            if (residentEntry.agentIds().contains("*")) {
                addResidentTargets(residentEntry.userId(), configuredAgentIds);
                continue;
            }
            addResidentTargets(residentEntry.userId(), filterValidResidentAgentIds(residentEntry.userId(),
                residentEntry.agentIds(), configuredAgentIds));
        }
    }

    @SuppressWarnings("unchecked")
    private ResidentEntry parseResidentEntry(Object entryObj) {
        if (!(entryObj instanceof Map<?, ?> rawEntry)) {
            return null;
        }
        Map<String, Object> entry = (Map<String, Object>) rawEntry;
        String userId = YamlLoader.getString(entry, "userId", "").trim();
        if (userId.isEmpty()) {
            log.warn("Skipping residentInstances entry with blank userId");
            return null;
        }
        Object agentIdsObj = entry.get("agentIds");
        if (!(agentIdsObj instanceof List<?> rawAgentIds) || rawAgentIds.isEmpty()) {
            log.warn("Skipping residentInstances entry for user {} without agentIds", userId);
            return null;
        }
        List<String> agentIds = rawAgentIds.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .toList();
        return new ResidentEntry(userId, agentIds);
    }

    private List<String> filterValidResidentAgentIds(String userId, List<String> agentIds, List<String> configuredAgentIds) {
        return agentIds.stream().filter(agentId -> {
            boolean exists = configuredAgentIds.contains(agentId);
            if (!exists) {
                log.warn("Skipping unknown resident agent {} for user {}", agentId, userId);
            }
            return exists;
        }).toList();
    }

    private void addResidentTargets(String userId, List<String> agentIds) {
        for (String agentId : agentIds) {
            String key = ManagedInstance.buildKey(agentId, userId);
            if (!residentInstanceKeys.add(key)) {
                continue;
            }
            residentInstances.add(new ResidentInstanceTarget(userId, agentId));
        }
    }

    /**
     * Load the agent's config.yaml as a Map (cached).
     *
     * @param agentId agent instance identifier
     * @return cached YAML config map for the agent
     */
    public Map<String, Object> loadAgentConfigYaml(String agentId) {
        return loadYamlCached(configCache, agentId, getAgentConfigDir(agentId).resolve("config.yaml"));
    }

    /**
     * Load the agent's secrets.yaml as a Map (cached, mtime-validated).
     *
     * @param agentId agent instance identifier
     * @return cached YAML secrets map for the agent
     */
    public Map<String, Object> loadAgentSecretsYaml(String agentId) {
        return loadYamlCached(secretsCache, agentId, getAgentConfigDir(agentId).resolve("secrets.yaml"));
    }

    /**
     * Returns the cached parsed YAML for {@code key} when the file is unchanged since it was cached (by last-modified
     * time); otherwise re-reads, refreshes the cache, and returns the new map. This keeps the parse-avoidance benefit
     * of caching while self-healing on out-of-band file edits, so a config.yaml change does not require a gateway
     * restart to take effect.
     */
    private Map<String, Object> loadYamlCached(ConcurrentHashMap<String, CachedYaml> cache, String key, Path path) {
        FileTime mtime = currentMtime(path);
        CachedYaml cached = cache.get(key);
        if (cached != null && Objects.equals(cached.mtime(), mtime)) {
            return cached.config();
        }
        Map<String, Object> loaded = YamlLoader.load(path);
        cache.put(key, new CachedYaml(mtime, loaded));
        return loaded;
    }

    private FileTime currentMtime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            // Missing/unreadable file: distinct from any real mtime, so a cached value for a since-deleted file is
            // not reused, and a since-created file is detected on the next read.
            return null;
        }
    }

    /**
     * Parsed YAML cached together with the source file's last-modified time for staleness detection.
     *
     * @author x00000000
     * @since 2026-06-06
     */
    private record CachedYaml(FileTime mtime, Map<String, Object> config) {
    }

    /**
     * Invalidate cached config/secrets for an agent.
     *
     * @param agentId agent instance identifier
     */
    public void invalidateCache(String agentId) {
        configCache.remove(agentId);
        secretsCache.remove(agentId);
    }

    /**
     * Returns model-related config values from an agent config.
     *
     * @param config the config parameter
     * @return the result
     */
    public Map<String, Object> extractModelConfig(Map<String, Object> config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : MODEL_CONFIG_KEYS) {
            result.put(key, config.getOrDefault(key, ""));
        }
        return result;
    }

    /**
     * Returns a compact summary of the agent config.yaml for overview rendering.
     *
     * @param config the parsed config.yaml content
     * @return the result
     */
    public Map<String, Object> extractAgentConfigSummary(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", config.getOrDefault("GOOSE_MODE", ""));
        result.put("disableKeyring", config.getOrDefault("GOOSE_DISABLE_KEYRING", ""));
        result.put("telemetryEnabled", config.getOrDefault("GOOSE_TELEMETRY_ENABLED", ""));

        int enabledExtensions = 0;
        int disabledExtensions = 0;
        Object extensionsObj = config.get("extensions");
        if (extensionsObj instanceof Map<?, ?> extensions) {
            for (Object extensionObj : extensions.values()) {
                if (extensionObj instanceof Map<?, ?> extension) {
                    if (isEnabled(extension.get("enabled"))) {
                        enabledExtensions++;
                    } else {
                        disabledExtensions++;
                    }
                }
            }
        }
        result.put("enabledExtensions", enabledExtensions);
        result.put("disabledExtensions", disabledExtensions);
        return result;
    }

    /**
     * Lists custom provider JSON definitions for an agent.
     *
     * @param agentId the agentId parameter
     * @return the result
     */
    public List<Map<String, Object>> listCustomProviders(String agentId) {
        Path providersDir = getCustomProvidersDir(agentId);
        if (!Files.isDirectory(providersDir)) {
            return List.of();
        }
        List<Map<String, Object>> providers = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(providersDir, "*.json")) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                try {
                    Map<String, Object> provider =
                        OBJECT_MAPPER.readValue(entry.toFile(), new TypeReference<Map<String, Object>>() {});
                    provider.put("fileName", entry.getFileName().toString());
                    providers.add(provider);
                } catch (IOException | IllegalArgumentException e) {
                    log.warn("Failed to parse custom provider {} for {}: {}", entry.getFileName(), agentId,
                        e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list custom providers for {}: {}", agentId, e.getMessage());
        }
        providers.sort((left, right) -> String.valueOf(left.getOrDefault("display_name", left.get("name")))
            .compareToIgnoreCase(String.valueOf(right.getOrDefault("display_name", right.get("name")))));
        return providers;
    }

    /**
     * Updates model-related keys in config.yaml.
     *
     * @param agentId the agentId parameter
     * @param modelConfig the modelConfig parameter
     * @throws IllegalStateException if the configuration cannot be written
     */
    public void updateModelConfig(String agentId, Map<String, String> modelConfig) {
        try {
            doUpdateModelConfig(agentId, modelConfig);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update model config for " + agentId, e);
        }
    }

    private void doUpdateModelConfig(String agentId, Map<String, String> modelConfig) throws IOException {
        Path configPath = getAgentConfigDir(agentId).resolve("config.yaml");
        Map<String, Object> config = new LinkedHashMap<>(YamlLoader.load(configPath));
        String provider = trimToNull(modelConfig.get("GOOSE_PROVIDER"));
        String model = trimToNull(modelConfig.get("GOOSE_MODEL"));
        if (provider == null) {
            throw new IllegalArgumentException("GOOSE_PROVIDER is required");
        }
        if (model == null) {
            throw new IllegalArgumentException("GOOSE_MODEL is required");
        }
        // Custom providers resolve against custom_providers/*.json at spawn time; saving a name
        // without a matching definition would break every future instance start (resume 500),
        // so reject it here even when it matches the currently configured value.
        if (provider.startsWith("custom_") && !customProviderExists(agentId, provider)) {
            throw new IllegalArgumentException("Provider '" + provider + "' not found for agent '" + agentId
                + "': no matching definition in custom_providers/");
        }

        for (String key : MODEL_CONFIG_KEYS) {
            if (modelConfig.containsKey(key)) {
                String value = trimToNull(modelConfig.get(key));
                if (value == null) {
                    config.remove(key);
                } else {
                    config.put(key, value);
                }
            }
        }

        Yaml yaml = createBlockYaml();
        Files.writeString(configPath, yaml.dump(config), StandardCharsets.UTF_8);
        invalidateCache(agentId);
    }

    /**
     * Creates a custom provider JSON definition for an agent.
     *
     * @param agentId the agentId parameter
     * @param provider the provider parameter
     * @return the created provider definition
     * @throws IllegalStateException if the provider cannot be written
     */
    public Map<String, Object> createCustomProvider(String agentId, Map<String, Object> provider) {
        try {
            return doCreateCustomProvider(agentId, provider);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create custom provider for " + agentId, e);
        }
    }

    private Map<String, Object> doCreateCustomProvider(String agentId, Map<String, Object> provider)
        throws IOException {
        String name = trimToNull(asString(provider.get("name")));
        List<String> errors = new ArrayList<>();
        if (name == null) {
            errors.add("Provider name is required");
        } else {
            if (!PROVIDER_NAME_PATTERN.matcher(name).matches()) {
                errors.add("Provider name contains unsupported characters");
            }
            if (name.length() > PROVIDER_NAME_MAX_LENGTH) {
                errors.add("Provider name must not exceed " + PROVIDER_NAME_MAX_LENGTH + " characters");
            }
        }
        errors.addAll(collectProviderFieldLengthErrors(provider));
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        Path providersDir = getCustomProvidersDir(agentId);
        Files.createDirectories(providersDir);
        Path providerPath = providersDir.resolve(name + ".json");
        if (Files.exists(providerPath)) {
            throw new IllegalArgumentException("Provider '" + name + "' already exists");
        }

        Map<String, Object> normalized = normalizeProvider(provider, name);
        Files.writeString(providerPath, OBJECT_MAPPER.writeValueAsString(normalized) + System.lineSeparator(),
            StandardCharsets.UTF_8);
        writeProviderSecret(agentId, String.valueOf(normalized.get("api_key_env")),
            trimToNull(asString(provider.get("api_key"))));
        normalized.put("fileName", providerPath.getFileName().toString());
        return normalized;
    }

    /**
     * Updates editable fields in an existing custom provider JSON definition.
     *
     * @param agentId the agentId parameter
     * @param providerName the providerName parameter
     * @param provider the provider parameter
     * @return the updated provider definition
     * @throws IllegalStateException if the provider cannot be written
     */
    public Map<String, Object> updateCustomProvider(String agentId, String providerName, Map<String, Object> provider) {
        try {
            return doUpdateCustomProvider(agentId, providerName, provider);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update custom provider for " + agentId, e);
        }
    }

    private Map<String, Object> doUpdateCustomProvider(String agentId, String providerName,
        Map<String, Object> provider) throws IOException {
        String name = trimToNull(providerName);
        List<String> errors = new ArrayList<>();
        if (name == null) {
            errors.add("Provider name is required");
        } else {
            if (!PROVIDER_NAME_PATTERN.matcher(name).matches()) {
                errors.add("Provider name contains unsupported characters");
            }
            if (name.length() > PROVIDER_NAME_MAX_LENGTH) {
                errors.add("Provider name must not exceed " + PROVIDER_NAME_MAX_LENGTH + " characters");
            }
        }
        errors.addAll(collectProviderFieldLengthErrors(provider));
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        Path providerPath = getCustomProvidersDir(agentId).resolve(name + ".json");
        if (!Files.exists(providerPath)) {
            throw new IllegalArgumentException("Provider '" + name + "' not found");
        }

        Map<String, Object> existing =
            OBJECT_MAPPER.readValue(providerPath.toFile(), new TypeReference<Map<String, Object>>() {});
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("name", defaultString(existing.get("name"), name));
        updated.put("engine", "openai");
        updated.put("display_name", defaultString(existing.get("display_name"), name));
        updated.put("description", defaultString(provider.get("description"), ""));
        updated.put("api_key_env", defaultString(existing.get("api_key_env"), buildApiKeyEnv(name)));
        updated.put("base_url", defaultString(provider.get("base_url"), ""));
        updated.put("models", normalizeProviderModels(provider.get("models")));
        updated.put("supports_streaming", true);
        updated.put("requires_auth", true);

        Files.writeString(providerPath, OBJECT_MAPPER.writeValueAsString(updated) + System.lineSeparator(),
            StandardCharsets.UTF_8);
        writeProviderSecret(agentId, String.valueOf(updated.get("api_key_env")),
            trimToNull(asString(provider.get("api_key"))));
        updated.put("fileName", providerPath.getFileName().toString());
        return updated;
    }

    /**
     * Validates string field lengths in a provider request body.
     * These limits mirror the frontend maxLength values in CreateProviderModal.tsx.
     * Collects all errors and throws them together so the caller can report every issue.
     */
    private List<String> collectProviderFieldLengthErrors(Map<String, Object> provider) {
        List<String> errors = new ArrayList<>();
        collectMaxLengthError(provider, "display_name", PROVIDER_DISPLAY_NAME_MAX_LENGTH, "Display name", errors);
        collectMaxLengthError(provider, "base_url", PROVIDER_BASE_URL_MAX_LENGTH, "Base URL", errors);
        collectMaxLengthError(provider, "api_key", PROVIDER_API_KEY_MAX_LENGTH, "API key", errors);
        collectMaxLengthError(provider, "description", PROVIDER_DESCRIPTION_MAX_LENGTH, "Description", errors);

        // Validate model names inside the "models" array
        Object modelsObj = provider.get("models");
        if (modelsObj instanceof List<?> models) {
            for (Object item : models) {
                if (item instanceof Map<?, ?> model) {
                    String modelName = trimToNull(asString(model.get("name")));
                    if (modelName != null && modelName.length() > PROVIDER_MODEL_NAME_MAX_LENGTH) {
                        errors.add("Model name must not exceed " + PROVIDER_MODEL_NAME_MAX_LENGTH + " characters");
                    }
                }
            }
        }
        return errors;
    }

    private void collectMaxLengthError(Map<String, Object> map, String field, int maxLength, String label, List<String> errors) {
        String value = trimToNull(asString(map.get(field)));
        if (value != null && value.length() > maxLength) {
            errors.add(label + " must not exceed " + maxLength + " characters");
        }
    }

    private Map<String, Object> normalizeProvider(Map<String, Object> provider, String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("engine", "openai");
        result.put("display_name", defaultString(provider.get("display_name"), name));
        result.put("description", defaultString(provider.get("description"), ""));
        result.put("api_key_env", buildApiKeyEnv(name));
        result.put("base_url", defaultString(provider.get("base_url"), ""));
        result.put("models", normalizeProviderModels(provider.get("models")));
        result.put("supports_streaming", true);
        result.put("requires_auth", true);
        return result;
    }

    private void writeProviderSecret(String agentId, String apiKeyEnv, String apiKey) throws IOException {
        if (apiKey == null || apiKeyEnv == null || apiKeyEnv.isBlank()) {
            return;
        }
        Path secretsPath = getAgentConfigDir(agentId).resolve("secrets.yaml");
        Map<String, Object> secrets = new LinkedHashMap<>(YamlLoader.load(secretsPath));
        secrets.put(apiKeyEnv, apiKey);
        Yaml yaml = createBlockYaml();
        Files.writeString(secretsPath, yaml.dump(secrets), StandardCharsets.UTF_8);
        invalidateCache(agentId);
    }

    private String buildApiKeyEnv(String providerName) {
        return "CUSTOM_" + providerName.replaceFirst("^custom[_-]?", "")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("^_+|_+$", "")
            .toUpperCase(java.util.Locale.ROOT) + "_API_KEY";
    }

    private List<Map<String, Object>> normalizeProviderModels(Object modelsObj) {
        if (!(modelsObj instanceof List<?> rawModels)) {
            throw new IllegalArgumentException("At least one model is required");
        }
        List<Map<String, Object>> models = new ArrayList<>();
        for (Object rawModel : rawModels) {
            if (!(rawModel instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String name = defaultString(rawMap.get("name"), "");
            Map<String, Object> model = new HashMap<>();
            model.put("name", name);
            Object contextLimit = rawMap.get("context_limit");
            if (contextLimit instanceof Number || contextLimit instanceof String) {
                model.put("context_limit", contextLimit);
            }
            models.add(model);
        }
        if (models.isEmpty()) {
            throw new IllegalArgumentException("At least one model is required");
        }
        return models;
    }

    private boolean customProviderExists(String agentId, String providerName) {
        return listCustomProviders(agentId).stream().anyMatch(provider -> providerName.equals(provider.get("name")));
    }

    private Path getCustomProvidersDir(String agentId) {
        return getAgentConfigDir(agentId).resolve("custom_providers");
    }

    private String defaultString(Object value, String fallback) {
        String result = trimToNull(asString(value));
        return result != null ? result : fallback;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private boolean isEnabled(Object value) {
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * List skills for an agent, parsing SKILL.md frontmatter for metadata.
     *
     * @param agentId agent instance identifier
     * @return list of skill metadata maps, each containing id, name, description, and path
     */
    public List<Map<String, String>> listSkills(String agentId) {
        Path skillsDir = getAgentConfigDir(agentId).resolve("skills");
        List<Map<String, String>> skills = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return skills;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    skills.add(buildSkillEntry(agentId, entry));
                }
            }
        } catch (IOException e) {
            log.error("Failed to list skills for {}", agentId, e);
        }
        return skills;
    }

    /**
     * Builds a skill metadata map from a skill directory, parsing SKILL.md frontmatter if present.
     *
     * @param agentId agent instance identifier (for logging)
     * @param skillDir path to the skill directory
     * @return skill metadata map containing id, name, description, and path
     */
    private Map<String, String> buildSkillEntry(String agentId, Path skillDir) {
        String dirName = skillDir.getFileName().toString();
        Map<String, String> skill = new HashMap<>();
        skill.put("id", dirName);
        skill.put("name", dirName);
        skill.put("description", "");
        skill.put("path", "skills/" + dirName);

        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) {
            return skill;
        }
        try {
            Map<String, String> frontmatter = parseMarkdownFrontmatter(skillMd);
            if (frontmatter.containsKey("name")) {
                skill.put("name", frontmatter.get("name"));
            }
            if (frontmatter.containsKey("description")) {
                skill.put("description", frontmatter.get("description"));
            }
            putFrontmatterAlias(skill, frontmatter, "pinned", "pinned");
            putFrontmatterAlias(skill, frontmatter, "displayOrder", "displayOrder", "display-order", "x-display-order");
        } catch (IOException e) {
            log.warn("Failed to parse SKILL.md for skill {}/{}", agentId, dirName, e);
        }
        return skill;
    }

    private void putFrontmatterAlias(Map<String, String> target, Map<String, String> frontmatter, String targetKey,
        String... aliases) {
        for (String alias : aliases) {
            String value = frontmatter.get(alias);
            if (value != null && !value.isBlank()) {
                target.put(targetKey, value);
                return;
            }
        }
    }

    /**
     * Parse YAML frontmatter (between --- delimiters) from a Markdown file.
     *
     * @param mdPath path to the markdown file to parse
     * @return map of frontmatter key-value pairs, empty if no valid frontmatter found
     * @throws IOException if an I/O error occurs reading the markdown file
     */
    private Map<String, String> parseMarkdownFrontmatter(Path mdPath) throws IOException {
        Map<String, String> result = new HashMap<>();
        String content = Files.readString(mdPath);
        if (!content.startsWith("---")) {
            return result;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex < 0) {
            return result;
        }
        String yamlBlock = content.substring(3, endIndex).trim();
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
            new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
        Object parsed;
        try {
            parsed = yaml.load(yamlBlock);
        } catch (YAMLException e) {
            log.warn("Invalid YAML frontmatter in {}: {}", mdPath, e.getMessage());
            return result;
        }
        if (parsed instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    result.put(e.getKey().toString(), e.getValue().toString());
                }
            }
        }
        return result;
    }

    /**
     * Read AGENTS.md content for an agent.
     *
     * @param agentId agent instance identifier
     * @return AGENTS.md file content, or empty string if not found
     */
    public String readAgentsMd(String agentId) {
        Path mdPath = getAgentsDir().resolve(agentId).resolve("AGENTS.md");
        if (!Files.exists(mdPath)) {
            return "";
        }
        try {
            return Files.readString(mdPath);
        } catch (IOException e) {
            log.error("Failed to read AGENTS.md for {}", agentId, e);
            return "";
        }
    }

    /**
     * Write AGENTS.md content for an agent.
     *
     * @param agentId agent instance identifier
     * @param content markdown content to write
     */
    public void writeAgentsMd(String agentId, String content) {
        Path mdPath = getAgentsDir().resolve(agentId).resolve("AGENTS.md");
        try {
            Files.writeString(mdPath, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write AGENTS.md for agent: " + agentId, e);
        }
    }

    // ── Memory file management ──────────────────────────────────────────

    // 100KB
    private static final int MAX_MEMORY_CONTENT_SIZE = 100 * 1024;

    /**
     * Returns the per-user goose config home — the value Gateway sets as {@code XDG_CONFIG_HOME} for
     * this instance, from which goose resolves memory at {@code <this>/goose/memory}. This is the
     * single source of that path: {@link InstanceManager} reads it for the env var and
     * {@link #getGooseMemoryDir} resolves {@code goose/memory} onto it, so the two readers (goosed
     * and the memory tab) cannot drift. It is a real directory, distinct from the {@code config}
     * symlink that points at the shared agent config.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @return path to the per-user XDG config home ({@code runtimeRoot/data/config})
     */
    public Path getGooseConfigHomeDir(String userId, String agentId) {
        return getUserAgentDir(userId, agentId).resolve("data").resolve("config");
    }

    /**
     * Returns the per-user goose memory directory. Memory is per-user state (sibling of the
     * per-user {@code data/schedule.json}); it matches the {@code XDG_CONFIG_HOME/goose/memory}
     * path goose loads from.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @return path to the per-user memory directory
     */
    private Path getGooseMemoryDir(String userId, String agentId) {
        return getGooseConfigHomeDir(userId, agentId).resolve("goose").resolve("memory");
    }

    /**
     * Returns the shared agent-level seed memory directory, copied into a user's memory once on
     * first access (see {@link #ensureMemorySeeded(String, String)}).
     *
     * @param agentId agent instance identifier
     * @return path to the shared seed memory directory
     */
    private Path getSeedMemoryDir(String agentId) {
        return getAgentConfigDir(agentId).resolve("goose").resolve("memory");
    }

    private Path getMemorySeededMarker(String userId, String agentId) {
        return getUserAgentDir(userId, agentId).resolve("data").resolve(".memory-seeded");
    }

    /**
     * Returns the shared agent-level seed directory for default scheduled tasks. Holds a
     * {@code seed.json} manifest ({@code [{id, cron, recipe}]}) plus the goose-native recipe YAML
     * files it references; copied into a user's runtime once on first instance spawn (see
     * {@link #ensureSchedulesSeeded(String, String)}).
     *
     * @param agentId agent instance identifier
     * @return path to the shared seed-schedules directory
     */
    private Path getSeedSchedulesDir(String agentId) {
        return getAgentConfigDir(agentId).resolve("seed-schedules");
    }

    private Path getSchedulesSeededMarker(String userId, String agentId) {
        return getUserAgentDir(userId, agentId).resolve("data").resolve(".schedules-seeded");
    }

    /**
     * Seeds a user's memory from the shared agent seed exactly once, the first time that user's
     * memory is touched (by either an instance spawn or the memory tab). Idempotency is tracked by a
     * one-time {@code data/.memory-seeded} marker, NOT by emptiness — so a user who deliberately
     * clears all their memory is never re-seeded. Memory is per-user state; this is a platform-wide
     * rule applied uniformly to every agent so a user's first run inherits the agent's preset memory
     * while later edits stay per-user. Each preset {@code *.txt} is copied only when no file of that
     * name already exists, so an existing (user-written or already-seeded) entry is never overwritten
     * and a seed interrupted partway completes on the next call before the marker is written.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     */
    public void ensureMemorySeeded(String userId, String agentId) {
        Path marker = getMemorySeededMarker(userId, agentId);
        if (Files.exists(marker)) {
            return;
        }
        Path seedDir = getSeedMemoryDir(agentId);
        Path targetDir = getGooseMemoryDir(userId, agentId);
        try {
            int copied = 0;
            if (Files.isDirectory(seedDir)) {
                Files.createDirectories(targetDir);
                try (DirectoryStream<Path> seeds = Files.newDirectoryStream(seedDir, "*.txt")) {
                    for (Path seed : seeds) {
                        Path dest = targetDir.resolve(seed.getFileName());
                        if (Files.isRegularFile(seed) && Files.notExists(dest)) {
                            try {
                                Files.copy(seed, dest);
                                copied++;
                            } catch (FileAlreadyExistsException concurrent) {
                                // A concurrent seeder (spawn vs. memory tab) created it first; the
                                // file is present, so treat this as already done and keep going.
                                log.debug("Memory seed {} already created concurrently for {}:{}", seed.getFileName(),
                                    agentId, userId);
                            }
                        }
                    }
                }
            }
            // Mark done only after the copy loop completes without error, so an interrupted seed
            // retries; written regardless of count so a seedless agent is still marked exactly once.
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "", StandardCharsets.UTF_8);
            if (copied > 0) {
                log.info("Seeded {} memory file(s) for {}:{} into {}", copied, agentId, userId, targetDir);
            }
        } catch (IOException e) {
            // Non-fatal: a failed seed must not block memory reads/writes or instance startup.
            log.warn("Failed to seed memory for {}:{}: {}", agentId, userId, e.getMessage());
        }
    }

    /**
     * Seeds an agent's default scheduled tasks into a user's runtime exactly once, the first time that
     * user's instance spawns. Mirrors {@link #ensureMemorySeeded(String, String)}: idempotency is tracked
     * by a one-time {@code data/.schedules-seeded} marker, NOT by emptiness — so a user who pauses or
     * deletes a seeded task in the Scheduler tab never has it resurrected. Schedules are per-user state;
     * this is a uniform rule for every agent, but only agents that ship a {@code seed-schedules/seed.json}
     * actually get tasks (today only FO Copilot: ticket-watch-loop + memory-maintenance).
     *
     * <p>Each seed entry copies its recipe YAML to {@code data/scheduled_recipes/<id>.yaml} (only when
     * absent, so a user-edited recipe is never clobbered) and appends a job to {@code data/schedule.json}
     * (only when no job with that id exists, so existing user/goosed schedules are preserved). Seeding runs
     * before goosed launches, so it registers the tasks with its in-process cron on startup. Non-fatal: a
     * failed seed must not block instance startup.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     */
    public void ensureSchedulesSeeded(String userId, String agentId) {
        Path marker = getSchedulesSeededMarker(userId, agentId);
        if (Files.exists(marker)) {
            return;
        }
        Path seedManifest = getSeedSchedulesDir(agentId).resolve("seed.json");
        try {
            if (Files.isRegularFile(seedManifest)) {
                seedSchedulesFromManifest(userId, agentId, seedManifest);
            }
            // Mark done only after seeding completes without error, so an interrupted seed retries;
            // written regardless of whether the agent ships any seed so a seedless agent is marked once.
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Non-fatal: a failed seed (e.g. unparseable manifest) must not block instance startup.
            log.warn("Failed to seed schedules for {}:{}: {}", agentId, userId, e.getMessage());
        }
    }

    private void seedSchedulesFromManifest(String userId, String agentId, Path seedManifest) throws IOException {
        Path seedDir = seedManifest.getParent();
        Path dataDir = getUserAgentDir(userId, agentId).resolve("data");
        Path recipesDir = dataDir.resolve("scheduled_recipes");
        Path scheduleJson = dataDir.resolve("schedule.json");

        List<Map<String, String>> seeds = OBJECT_MAPPER.readValue(
            Files.readString(seedManifest, StandardCharsets.UTF_8), new TypeReference<List<Map<String, String>>>() { });
        List<Map<String, Object>> jobs = readScheduledJobs(scheduleJson);

        Files.createDirectories(recipesDir);
        int added = 0;
        for (Map<String, String> seed : seeds) {
            String id = seed.get("id");
            String cron = seed.get("cron");
            String recipeFile = seed.get("recipe");
            if (isBlank(id) || isBlank(cron) || isBlank(recipeFile) || !isPathSafe(id) || !isPathSafe(recipeFile)) {
                log.warn("Skipping malformed schedule seed {} for {}:{}", seed, agentId, userId);
                continue;
            }
            Path recipeDest = recipesDir.resolve(id + ".yaml");
            // Skip registering a job whose recipe could not be placed — otherwise schedule.json would
            // carry a source pointing at a file goosed will never find, baking in a dead task.
            if (!ensureSeedRecipe(seedDir.resolve(recipeFile), recipeDest, id, agentId, userId)) {
                continue;
            }
            if (jobs.stream().noneMatch(job -> id.equals(job.get("id")))) {
                jobs.add(newScheduledJob(id, recipeDest.toAbsolutePath().normalize().toString(), cron));
                added++;
            }
            // A seed may opt its task into IM delivery (e.g. ticket-watch-loop). The marker is a Gateway-side
            // concern stored per user, keyed by schedule id; set it once here as part of the one-time seed.
            String deliver = seed.get("deliver");
            if (!isBlank(deliver)) {
                seedDeliverMarker(userId, agentId, id, deliver);
            }
        }
        if (added > 0) {
            Files.writeString(scheduleJson, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jobs),
                StandardCharsets.UTF_8);
            log.info("Seeded {} scheduled task(s) for {}:{} into {}", added, agentId, userId, scheduleJson);
        }
    }

    /**
     * Seeds the per-user IM-delivery marker for a built-in schedule (best-effort; a failure must not abort the
     * surrounding schedule seed). Mirrors what the schedule-create endpoint persists when a user toggles delivery.
     *
     * @param userId user identifier
     * @param agentId agent identifier
     * @param scheduleId seeded schedule identifier
     * @param deliver delivery channel value from the seed manifest (e.g. {@code im})
     */
    private void seedDeliverMarker(String userId, String agentId, String scheduleId, String deliver) {
        Path deliveryFile = ProactiveStorage.deliveryMarkersFileIn(getUserAgentDir(userId, agentId));
        try {
            ProactiveDeliveryMarkers.setDeliver(deliveryFile, scheduleId, deliver);
            log.info("Seeded deliver={} marker for schedule {} ({}:{})", deliver, scheduleId, agentId, userId);
        } catch (IOException e) {
            log.warn("Failed to seed deliver marker for schedule {} ({}:{}); it will not auto-deliver to IM: {}",
                scheduleId, agentId, userId, e.getMessage());
        }
    }

    /**
     * Copies a seed recipe to its per-user destination when absent, and reports whether a usable recipe now
     * exists there. Returns {@code false} (and warns) when the seed names a recipe file that is missing, so
     * the caller skips registering a schedule whose {@code source} would dangle. A pre-existing dest (a
     * user-edited recipe) is kept verbatim and counts as present.
     */
    private boolean ensureSeedRecipe(Path src, Path dest, String id, String agentId, String userId)
        throws IOException {
        if (Files.exists(dest)) {
            return true;
        }
        if (!Files.isRegularFile(src)) {
            log.warn("Schedule seed {} references missing recipe {} for {}:{}; skipping",
                id, src.getFileName(), agentId, userId);
            return false;
        }
        try {
            Files.copy(src, dest);
        } catch (FileAlreadyExistsException concurrent) {
            log.debug("Schedule recipe {} already created concurrently for {}:{}", id, agentId, userId);
        }
        return true;
    }

    private List<Map<String, Object>> readScheduledJobs(Path scheduleJson) throws IOException {
        if (Files.notExists(scheduleJson)) {
            return new ArrayList<>();
        }
        String content = Files.readString(scheduleJson, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return new ArrayList<>();
        }
        return OBJECT_MAPPER.readValue(content, new TypeReference<List<Map<String, Object>>>() { });
    }

    private Map<String, Object> newScheduledJob(String id, String source, String cron) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("id", id);
        job.put("source", source);
        job.put("cron", cron);
        job.put("last_run", null);
        job.put("currently_running", false);
        job.put("paused", false);
        job.put("current_session_id", null);
        job.put("process_start_time", null);
        return job;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Rejects a seed id / recipe filename that could escape {@code data/scheduled_recipes/} when resolved.
     * Seeds are checked-in config, but this keeps a typo'd or hand-edited {@code seed.json} from writing a
     * recipe — or a schedule {@code source} — outside the per-user data dir (cf. {@link #ensureMemorySeeded}
     * which strips directories via {@code getFileName()}).
     */
    private static boolean isPathSafe(String name) {
        return name.indexOf('/') < 0 && name.indexOf('\\') < 0 && !name.contains("..");
    }

    /**
     * Returns the per-user memory dir after ensuring it has been seeded once. Single choke point so
     * every read/write path seeds first; a new memory accessor reusing this cannot forget to seed.
     * Destructive ops ({@link #deleteMemoryFile}) deliberately skip seeding — deleting only acts on
     * what already exists, and seeding there could resurrect presets the user is removing.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @return path to the per-user memory directory
     */
    private Path seededMemoryDir(String userId, String agentId) {
        ensureMemorySeeded(userId, agentId);
        return getGooseMemoryDir(userId, agentId);
    }

    /**
     * List all memory files (*.txt) for a user's agent, returning category name + content.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @return list of memory file maps, each containing category and content keys
     */
    public List<Map<String, String>> listMemoryFiles(String userId, String agentId) {
        Path memoryDir = seededMemoryDir(userId, agentId);
        List<Map<String, String>> files = new ArrayList<>();
        if (!Files.isDirectory(memoryDir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(memoryDir, "*.txt")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String fileName = entry.getFileName().toString();
                    // strip .txt
                    String category = fileName.substring(0, fileName.length() - 4);
                    Map<String, String> file = new HashMap<>();
                    file.put("category", category);
                    try {
                        file.put("content", Files.readString(entry, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        log.warn("Failed to read memory file {}/{}", agentId, fileName, e);
                        file.put("content", "");
                    }
                    files.add(file);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list memory files for {}", agentId, e);
        }
        return files;
    }

    /**
     * Read a single memory file content.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @param category memory file category name (without .txt extension)
     * @return file content string, or {@code null} if not found or unreadable
     */
    public String readMemoryFile(String userId, String agentId, String category) {
        Path filePath = seededMemoryDir(userId, agentId).resolve(category + ".txt");
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (java.nio.file.NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            log.error("Failed to read memory file {}/{}", agentId, category, e);
            return null;
        }
    }

    /**
     * Write (create/update) a memory file. Creates the memory directory if needed.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @param category memory file category name (without .txt extension)
     * @param content text content to write; must not exceed 100KB
     */
    public void writeMemoryFile(String userId, String agentId, String category, String content) {
        if (content != null && content.getBytes(StandardCharsets.UTF_8).length > MAX_MEMORY_CONTENT_SIZE) {
            throw new IllegalArgumentException("Memory file content exceeds maximum size of 100KB");
        }
        Path memoryDir = seededMemoryDir(userId, agentId);
        try {
            Files.createDirectories(memoryDir);
            Files.writeString(memoryDir.resolve(category + ".txt"), content != null ? content : "",
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write memory file for agent: " + agentId, e);
        }
    }

    /**
     * Delete a memory file.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @param category memory file category name (without .txt extension)
     */
    public void deleteMemoryFile(String userId, String agentId, String category) {
        // No seeding here: deletion acts only on what already exists; seeding first could re-create
        // presets the user is clearing. Reads/writes seed via seededMemoryDir.
        Path filePath = getGooseMemoryDir(userId, agentId).resolve(category + ".txt");
        try {
            Files.delete(filePath);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new IllegalArgumentException("Memory file '" + category + "' not found");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete memory file for agent: " + agentId, e);
        }
    }

    /**
     * Reads MCP settings for a given agent and MCP name.
     *
     * @param agentId agent instance identifier
     * @param mcpName MCP extension name
     * @return parsed settings map, or {@code null} if not found or invalid
     */
    public Map<String, Object> readMcpSettings(String agentId, String mcpName) {
        if (KNOWLEDGE_SERVICE_MCP.equals(mcpName)) {
            return readKnowledgeServiceScopeFromConfig(agentId);
        }
        if (KNOWLEDGE_CLI_MCP.equals(mcpName)) {
            return readKnowledgeCliScopeFromConfig(agentId);
        }
        Path settingsPath = getAgentConfigDir(agentId).resolve("mcp").resolve(mcpName).resolve("settings.json");
        if (!Files.exists(settingsPath)) {
            return null;
        }
        try {
            String content = Files.readString(settingsPath);
            if (content == null || content.isBlank()) {
                return null;
            }
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object parsed = yaml.load(content);
            if (parsed instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) rawMap;
                return cast;
            }
            return null;
        } catch (IOException | YAMLException | IllegalArgumentException e) {
            log.warn("Failed to parse MCP settings for {}/{}: {}", agentId, mcpName, e.getMessage());
            return null;
        }
    }

    /**
     * Writes MCP settings for a given agent and MCP name.
     *
     * @param agentId agent instance identifier
     * @param mcpName MCP extension name
     * @param settings settings map to persist
     */
    public void writeMcpSettings(String agentId, String mcpName, Map<String, Object> settings) {
        try {
            if (KNOWLEDGE_SERVICE_MCP.equals(mcpName)) {
                writeKnowledgeServiceScopeToConfig(agentId, settings);
                invalidateCache(agentId);
                return;
            }
            if (KNOWLEDGE_CLI_MCP.equals(mcpName)) {
                writeKnowledgeCliScopeToConfig(agentId, settings);
                invalidateCache(agentId);
                return;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write MCP settings for " + agentId + "/" + mcpName, e);
        }
        Path mcpDir = getAgentConfigDir(agentId).resolve("mcp").resolve(mcpName);
        if (!Files.isDirectory(mcpDir)) {
            throw new IllegalArgumentException("MCP '" + mcpName + "' not found for agent '" + agentId + "'");
        }
        Path settingsPath = mcpDir.resolve("settings.json");
        try {
            Files.createDirectories(mcpDir);
            Yaml yaml = createBlockYaml();
            Files.writeString(settingsPath, yaml.dump(settings));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write MCP settings for " + agentId + "/" + mcpName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readKnowledgeServiceScopeFromConfig(String agentId) {
        Map<String, Object> config = loadAgentConfigYaml(agentId);
        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> extensions)) {
            return null;
        }
        Object extensionObj = extensions.get(KNOWLEDGE_SERVICE_MCP);
        if (!(extensionObj instanceof Map<?, ?> extension)) {
            return null;
        }
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (!(opsfactoryObj instanceof Map<?, ?> opsfactory)) {
            return null;
        }
        Object knowledgeScopeObj = opsfactory.get("knowledgeScope");
        if (!(knowledgeScopeObj instanceof Map<?, ?> knowledgeScope)) {
            return null;
        }
        Object sourceId = knowledgeScope.get("sourceId");
        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", sourceId instanceof String source ? source : null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void writeKnowledgeServiceScopeToConfig(String agentId, Map<String, Object> settings) throws IOException {
        Path configPath = getAgentConfigDir(agentId).resolve("config.yaml");
        Map<String, Object> config = YamlLoader.load(configPath);

        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> rawExtensions)) {
            throw new IllegalArgumentException("Agent config for '" + agentId + "' does not contain extensions");
        }
        Map<String, Object> extensions = (Map<String, Object>) rawExtensions;
        Object extensionObj = extensions.get(KNOWLEDGE_SERVICE_MCP);
        if (!(extensionObj instanceof Map<?, ?> rawExtension)) {
            throw new IllegalArgumentException("MCP 'knowledge-service' not found for agent '" + agentId + "'");
        }
        Map<String, Object> extension = (Map<String, Object>) rawExtension;

        Map<String, Object> opsfactory;
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (opsfactoryObj instanceof Map<?, ?> rawOpsfactory) {
            opsfactory = (Map<String, Object>) rawOpsfactory;
        } else {
            opsfactory = new HashMap<>();
            extension.put("x-opsfactory", opsfactory);
        }

        Map<String, Object> knowledgeScope;
        Object knowledgeScopeObj = opsfactory.get("knowledgeScope");
        if (knowledgeScopeObj instanceof Map<?, ?> rawKnowledgeScope) {
            knowledgeScope = (Map<String, Object>) rawKnowledgeScope;
        } else {
            knowledgeScope = new HashMap<>();
            opsfactory.put("knowledgeScope", knowledgeScope);
        }

        Object sourceIdObj = settings != null ? settings.get("sourceId") : null;
        String sourceId = sourceIdObj instanceof String s && !s.isBlank() ? s.trim() : null;
        knowledgeScope.put("sourceId", sourceId);

        Yaml yaml = createBlockYaml();
        Files.writeString(configPath, yaml.dump(config));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readKnowledgeCliScopeFromConfig(String agentId) {
        Map<String, Object> config = loadAgentConfigYaml(agentId);
        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> extensions)) {
            return null;
        }
        Object extensionObj = extensions.get(KNOWLEDGE_CLI_MCP);
        if (!(extensionObj instanceof Map<?, ?> extension)) {
            return null;
        }
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (!(opsfactoryObj instanceof Map<?, ?> opsfactory)) {
            return null;
        }
        Object scopeObj = opsfactory.get("scope");
        if (!(scopeObj instanceof Map<?, ?> scope)) {
            return null;
        }

        Object sourceIdObj = scope.get("sourceId");
        Object rootDirObj = scope.get("rootDir");
        String sourceId = sourceIdObj instanceof String source ? source : null;
        String rootDir = rootDirObj instanceof String root ? root : null;

        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", sourceId);
        result.put("rootDir", rootDir);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void writeKnowledgeCliScopeToConfig(String agentId, Map<String, Object> settings) throws IOException {
        Path configPath = getAgentConfigDir(agentId).resolve("config.yaml");
        Map<String, Object> config = YamlLoader.load(configPath);

        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> rawExtensions)) {
            throw new IllegalArgumentException("Agent config for '" + agentId + "' does not contain extensions");
        }
        Map<String, Object> extensions = (Map<String, Object>) rawExtensions;
        Object extensionObj = extensions.get(KNOWLEDGE_CLI_MCP);
        if (!(extensionObj instanceof Map<?, ?> rawExtension)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' not found for agent '" + agentId + "'");
        }
        Map<String, Object> extension = (Map<String, Object>) rawExtension;

        Map<String, Object> opsfactory;
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (opsfactoryObj instanceof Map<?, ?> rawOpsfactory) {
            opsfactory = (Map<String, Object>) rawOpsfactory;
        } else {
            opsfactory = new HashMap<>();
            extension.put("x-opsfactory", opsfactory);
        }

        Map<String, Object> scope;
        Object scopeObj = opsfactory.get("scope");
        if (scopeObj instanceof Map<?, ?> rawScope) {
            scope = (Map<String, Object>) rawScope;
        } else {
            scope = new HashMap<>();
            opsfactory.put("scope", scope);
        }

        Object sourceIdObj = settings != null ? settings.get("sourceId") : null;
        String sourceId = sourceIdObj instanceof String s && !s.isBlank() ? s.trim() : null;

        if (sourceId == null) {
            scope.put("sourceId", null);
            scope.put("rootDir", DEFAULT_KNOWLEDGE_CLI_ROOT_DIR);
        } else {
            validateKnowledgeSourceId(sourceId);
            Path artifactsDir = resolveKnowledgeArtifactsRoot().resolve(sourceId).normalize();
            scope.put("sourceId", sourceId);
            scope.put("rootDir", relativizeForAgentConfig(agentId, artifactsDir));
        }

        Yaml yaml = createBlockYaml();
        Files.writeString(configPath, yaml.dump(config));
    }

    private void validateKnowledgeSourceId(String sourceId) {
        if (!sourceId.matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid knowledge sourceId '" + sourceId + "'");
        }
    }

    private Path resolveKnowledgeArtifactsRoot() {
        String configuredRoot = properties.getKnowledge() != null ? properties.getKnowledge().getArtifactsRoot() : null;
        String artifactsRoot = configuredRoot != null && !configuredRoot.isBlank() ? configuredRoot.trim()
            : "../knowledge-service/data/artifacts";
        Path rootPath = Path.of(artifactsRoot);
        return rootPath.isAbsolute() ? rootPath.normalize() : gatewayRoot.resolve(rootPath).normalize();
    }

    private String relativizeForAgentConfig(String agentId, Path targetPath) {
        Path configDir = getAgentConfigDir(agentId).normalize();
        Path projectRoot =
            gatewayRoot.getParent() != null ? gatewayRoot.getParent().normalize() : properties.getProjectRootPath();
        Path normalizedTarget = targetPath.normalize();

        if (normalizedTarget.startsWith(projectRoot)) {
            return configDir.relativize(normalizedTarget).toString().replace('\\', '/');
        }
        return normalizedTarget.toString();
    }

    private Yaml createBlockYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(options), options);
    }

    /**
     * Create a new agent: directory structure, config files, registry update.
     *
     * @param id unique agent identifier (lowercase alphanumeric with hyphens)
     * @param name display name for the agent
     * @return map containing id, name, provider, and model of the created agent
     */
    public Map<String, Object> createAgent(String id, String name) {
        validateNewAgentId(id);
        ensureAgentNameUnique(name);
        try {
            Path agentDir = getAgentsDir().resolve(id);
            Path configDir = agentDir.resolve("config");
            Path targetConfig = createAgentDirectoryStructure(agentDir, configDir);
            seedAgentFiles(agentDir, configDir, name, targetConfig);
            updateAgentsYaml(id, name, false);
            registerCreatedAgent(id, name);
            Map<String, Object> config = YamlLoader.load(targetConfig);
            return Map.of("id", id, "name", name, "provider", config.getOrDefault("GOOSE_PROVIDER", ""), "model",
                config.getOrDefault("GOOSE_MODEL", ""));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create agent: " + id, e);
        }
    }

    private void validateNewAgentId(String id) {
        if (!id.matches("^[a-z0-9]([a-z0-9\\-]*[a-z0-9])?$") || id.length() < 2) {
            throw new IllegalArgumentException(
                "Agent ID must be at least 2 chars, lowercase letters, numbers, and hyphens only "
                    + "(no leading/trailing hyphens)");
        }
        if (findAgent(id) != null) {
            throw new IllegalArgumentException("Agent with ID '" + id + "' already exists");
        }
    }

    private void ensureAgentNameUnique(String name) {
        for (AgentRegistryEntry entry : registry) {
            if (entry.name().equals(name)) {
                throw new IllegalArgumentException("Agent with name '" + name + "' already exists");
            }
        }
    }

    private Path createAgentDirectoryStructure(Path agentDir, Path configDir) throws IOException {
        Files.createDirectories(configDir.resolve("skills"));
        return configDir.resolve("config.yaml");
    }

    private void seedAgentFiles(Path agentDir, Path configDir, String name, Path targetConfig) throws IOException {
        copyOrSeedDefaultConfig(targetConfig);
        Files.writeString(configDir.resolve("secrets.yaml"), "");
        Files.writeString(agentDir.resolve("AGENTS.md"), "# " + name + "\n");
    }

    private void copyOrSeedDefaultConfig(Path targetConfig) throws IOException {
        Path templateConfig = getAgentsDir().resolve("universal-agent").resolve("config").resolve("config.yaml");
        if (Files.exists(templateConfig)) {
            Files.copy(templateConfig, targetConfig);
            return;
        }
        Files.writeString(targetConfig, "GOOSE_PROVIDER: openai\nGOOSE_MODEL: gpt-4o\n");
    }

    private void registerCreatedAgent(String id, String name) {
        registry.add(new AgentRegistryEntry(id, name));
        invalidateCache(id);
    }

    private record ResidentEntry(String userId, List<String> agentIds) {
    }

    /**
     * Delete an agent: stop instances, remove files, update registry.
     *
     * @param id agent identifier to delete
     */
    public void deleteAgent(String id) {
        AgentRegistryEntry entry = findAgent(id);
        if (entry == null) {
            throw new IllegalArgumentException("Agent '" + id + "' not found");
        }

        try {
            // Remove agent directory
            Path agentDir = getAgentsDir().resolve(id);
            if (Files.exists(agentDir)) {
                FileUtil.deleteRecursively(agentDir);
            }

            // Update config.yaml
            updateAgentsYaml(id, null, true);

            // Remove from in-memory registry and invalidate cache
            registry.removeIf(e -> e.id().equals(id));
            invalidateCache(id);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete agent: " + id, e);
        }
    }

    private void updateAgentsYaml(String id, String name, boolean remove) throws IOException {
        Path configYaml = getGatewayRoot().resolve("config.yaml");
        Map<String, Object> data = YamlLoader.load(configYaml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agents = (List<Map<String, Object>>) data.get("agents");
        if (agents == null) {
            agents = new ArrayList<>();
        }

        if (remove) {
            agents.removeIf(a -> id.equals(a.get("id")));
        } else {
            Map<String, Object> newAgent = new HashMap<>();
            newAgent.put("id", id);
            newAgent.put("name", name);
            agents.add(newAgent);
        }

        data.put("agents", agents);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
            new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
        Files.writeString(configYaml, yaml.dump(data));
    }

    /**
     * Returns the base directory containing all agent configurations.
     *
     * @return path to the agents directory
     */
    public Path getAgentsDir() {
        return gatewayRoot.resolve(properties.getPaths().getAgentsDir());
    }

    /**
     * Returns the base directory containing user-specific data.
     *
     * @return path to the users directory
     */
    public Path getUsersDir() {
        return gatewayRoot.resolve(properties.getPaths().getUsersDir());
    }

    /**
     * Resolves the knowledge CLI root directory for a given agent from its configuration.
     *
     * @param agentId agent instance identifier
     * @return resolved absolute or normalized relative path to the knowledge CLI root directory
     */
    @SuppressWarnings("unchecked")
    public Path getKnowledgeCliRootDir(String agentId) {
        Map<String, Object> config = loadAgentConfigYaml(agentId);
        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> extensions)) {
            throw new IllegalArgumentException("Agent config for '" + agentId + "' does not contain extensions");
        }

        Object extensionObj = extensions.get(KNOWLEDGE_CLI_MCP);
        if (!(extensionObj instanceof Map<?, ?> extension)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' not found for agent '" + agentId + "'");
        }

        Object opsfactoryObj = extension.get("x-opsfactory");
        if (!(opsfactoryObj instanceof Map<?, ?> opsfactory)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' does not contain x-opsfactory scope");
        }

        Object scopeObj = opsfactory.get("scope");
        if (!(scopeObj instanceof Map<?, ?> scope)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' does not contain scope");
        }

        Object rootDirObj = scope.get("rootDir");
        String configuredRoot =
            rootDirObj instanceof String s && !s.isBlank() ? s.trim() : DEFAULT_KNOWLEDGE_CLI_ROOT_DIR;
        Path configDir = getAgentConfigDir(agentId);
        return Path.of(configuredRoot).isAbsolute() ? Path.of(configuredRoot).normalize()
            : configDir.resolve(configuredRoot).normalize();
    }

    /**
     * Returns the per-user agent directory path.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @return path to the user-specific agent directory
     */
    public Path getUserAgentDir(String userId, String agentId) {
        return getUsersDir().resolve(userId).resolve("agents").resolve(agentId);
    }

    /**
     * Returns the config directory for the given agent.
     *
     * @param agentId agent instance identifier
     * @return path to the agent configuration directory
     */
    public Path getAgentConfigDir(String agentId) {
        return getAgentsDir().resolve(agentId).resolve("config");
    }

    /**
     * Returns the resolved gateway root path.
     *
     * @return the gateway root directory path
     */
    public Path getGatewayRoot() {
        return gatewayRoot;
    }
}
