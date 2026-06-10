/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * REST controller for managing MCP (Model Context Protocol) extensions on agent instances.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RestSchema(schemaId = "mcpController")
@RequestMapping("/api/gateway/agents/{agentId}/mcp")
public class McpController {
    private static final String KNOWLEDGE_SERVICE_MCP = "knowledge-service";

    private static final String KNOWLEDGE_CLI_MCP = "knowledge-cli";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern MCP_NAME_PATTERN = Pattern.compile("^[-A-Za-z0-9._ ]+$");

    private static final int MCP_NAME_MAX_LENGTH = 100;

    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    private final AgentConfigService agentConfigService;

    /**
     * Creates the mcp controller instance.
     */
    public McpController(InstanceManager instanceManager, GoosedProxy goosedProxy,
        AgentConfigService agentConfigService) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
        this.agentConfigService = agentConfigService;
    }

    /**
     * Lists MCP extensions configured on the agent's system instance.
     *
     * @param agentId agent identifier
     * @param request current HTTP request
     * @return the result
     */
    @GetMapping
    public ResponseEntity<String> getMcpExtensions(@PathVariable("agentId") String agentId,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        String result = goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.GET, "/config/extensions", null, 30, instance.getSecretKey())
            .block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    /**
     * Creates a new MCP extension on the agent's system instance and recycles running instances.
     * Rejects the request if an MCP with the same name already exists.
     *
     * @param agentId agent identifier
     * @param body request body
     * @param request current HTTP request
     * @return the created MCP extension, or a conflict response if the name already exists
     */
    @PostMapping
    public ResponseEntity<String> createMcpExtension(@PathVariable("agentId") String agentId, @RequestBody String body,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();

        String mcpName;
        boolean isUpdate;
        try {
            JsonNode bodyNode = OBJECT_MAPPER.readTree(body);
            String nameError = validateMcpName(bodyNode);
            if (nameError != null) {
                return ResponseEntity.badRequest().body(nameError);
            }
            mcpName = bodyNode.get("name").asText().trim();
            isUpdate = isUpdateOperation(bodyNode);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Invalid request body");
        }

        if (!isUpdate && isDuplicateMcpName(mcpName, instance)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("MCP '" + mcpName + "' already exists");
        }

        String result = goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.POST, "/config/extensions", body, 30, instance.getSecretKey())
            .block();
        return ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(result);
    }

    private String validateMcpName(JsonNode bodyNode) {
        JsonNode nameNode = bodyNode.get("name");
        if (nameNode == null || !nameNode.isTextual()) {
            return "MCP name is required";
        }
        String mcpName = nameNode.asText().trim();
        if (mcpName.isEmpty()) {
            return "MCP name is required";
        }
        if (mcpName.length() > MCP_NAME_MAX_LENGTH) {
            return "MCP name must not exceed " + MCP_NAME_MAX_LENGTH + " characters";
        }
        if (!MCP_NAME_PATTERN.matcher(mcpName).matches()) {
            return "MCP name can only contain letters, numbers, spaces, dots, underscores, and hyphens";
        }
        return null;
    }

    private boolean isUpdateOperation(JsonNode bodyNode) {
        JsonNode configNode = bodyNode.get("config");
        return configNode != null && configNode.isObject()
            && configNode.hasNonNull("bundled");
    }

    private boolean isDuplicateMcpName(String mcpName, ManagedInstance instance) {
        String existingJson = goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.GET, "/config/extensions", null, 30,
                instance.getSecretKey())
            .block();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(existingJson);
            JsonNode extensions = root.get("extensions");
            if (extensions != null && extensions.isArray()) {
                for (JsonNode ext : extensions) {
                    JsonNode nameNode = ext.get("name");
                    if (nameNode != null && nameNode.isTextual() && mcpName.equals(nameNode.asText())) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            // If we can't parse the existing config, proceed with creation and let goose handle it
        }
        return false;
    }

    /**
     * Deletes an MCP extension by name and recycles running instances.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param request current HTTP request
     * @return the deletion result
     */
    @DeleteMapping("/{name}")
    public String deleteMcpExtension(@PathVariable("agentId") String agentId, @PathVariable("name") String name,
        HttpServletRequest request) {
        String userId = (String) request.getAttribute(UserContextFilter.USER_ID_ATTR);
        ManagedInstance instance = instanceManager.getOrSpawn(agentId, userId).block();
        return goosedProxy
            .fetchJson(instance.getPort(), HttpMethod.DELETE, "/config/extensions/" + name, null, 30,
                instance.getSecretKey())
            .block();
    }

    /**
     * Gets the settings for a specific MCP extension.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param request current HTTP request
     * @return the settings for a specific MCP extension
     */
    @GetMapping("/{name}/settings")
    public ResponseEntity<Map<String, Object>> getMcpSettings(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, HttpServletRequest request) {
        try {
            Map<String, Object> settings = agentConfigService.readMcpSettings(agentId, name);
            if (hasConfigBackedSettings(name)) {
                if (settings == null) {
                    return ResponseEntity.ok(emptyKnowledgeSettings(name));
                }
                if (!settings.containsKey("sourceId")) {
                    settings.put("sourceId", null);
                }
                return ResponseEntity.ok(settings);
            }
            if (settings == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of());
            }
            return ResponseEntity.ok(settings);
        } catch (IllegalStateException e) {
            if (hasConfigBackedSettings(name)) {
                return ResponseEntity.ok(emptyKnowledgeSettings(name));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "SETTINGS_READ_FAILED", "message", "Failed to read MCP settings"));
        }
    }

    /**
     * Updates the settings for a specific MCP extension.
     *
     * @param agentId agent identifier
     * @param name name value
     * @param body request body
     * @param request current HTTP request
     * @return the update result
     */
    @PutMapping("/{name}/settings")
    public ResponseEntity<Map<String, Object>> putMcpSettings(@PathVariable("agentId") String agentId,
        @PathVariable("name") String name, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            agentConfigService.writeMcpSettings(agentId, name, body);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "RESOURCE_NOT_FOUND", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "SETTINGS_WRITE_FAILED", "message", "Failed to write MCP settings"));
        }
    }

    private boolean hasConfigBackedSettings(String name) {
        return KNOWLEDGE_SERVICE_MCP.equals(name) || KNOWLEDGE_CLI_MCP.equals(name);
    }

    private Map<String, Object> emptyKnowledgeSettings(String name) {
        Map<String, Object> fallback = new java.util.HashMap<>();
        fallback.put("sourceId", null);
        if (KNOWLEDGE_CLI_MCP.equals(name)) {
            fallback.put("rootDir", null);
        }
        return fallback;
    }
}