package com.cubrid.tools.ideaconfig.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Loads and parses osgi-app.properties configuration files.
 * Supports multi-line values with backslash continuation.
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    public ProjectConfig load(Path configFile) throws IOException, ProjectConfig.ConfigurationException {
        log.info("Loading configuration from: {}", configFile);

        if (!Files.exists(configFile)) {
            throw new IOException("Configuration file not found: " + configFile);
        }

        Map<String, String> properties = parseProperties(configFile);
        ProjectConfig config = mapToConfig(properties);

        log.debug("Loaded configuration: {}", config);
        return config;
    }

    /**
     * Parse a properties file. Supports multi-line values with trailing backslash.
     */
    private Map<String, String> parseProperties(Path file) throws IOException {
        Map<String, String> properties = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            String currentKey = null;
            StringBuilder currentValue = new StringBuilder();
            boolean continuation = false;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!continuation && (trimmed.isEmpty() || trimmed.startsWith("#"))) {
                    continue;
                }

                if (continuation) {
                    if (trimmed.endsWith("\\")) {
                        currentValue.append(trimmed, 0, trimmed.length() - 1);
                    } else {
                        currentValue.append(trimmed);
                        properties.put(currentKey, currentValue.toString());
                        currentKey = null;
                        currentValue.setLength(0);
                        continuation = false;
                    }
                } else {
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex == -1) {
                        log.warn("Invalid line (no '='): {}", line);
                        continue;
                    }

                    currentKey = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();

                    if (value.endsWith("\\")) {
                        currentValue.append(value, 0, value.length() - 1);
                        continuation = true;
                    } else {
                        properties.put(currentKey, value);
                    }
                }
            }

            if (continuation && currentKey != null) {
                properties.put(currentKey, currentValue.toString());
            }
        }

        log.debug("Parsed {} properties", properties.size());
        return properties;
    }

    private ProjectConfig mapToConfig(Map<String, String> properties) {
        ProjectConfig config = new ProjectConfig();
        config.setWorkspaceName(properties.get("workspaceName"));
        parsePathList(properties.get("featuresPaths"), config::addFeaturesPath);
        parsePathList(properties.get("bundlesPaths"), config::addBundlesPath);
        parsePathList(properties.get("productsPaths"), config::addProductsPath);
        parsePathList(properties.get("testModuleRoots"), config::addTestModuleRoot);
        return config;
    }

    private void parsePathList(String value, Consumer<String> consumer) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                consumer.accept(trimmed);
            }
        }
    }
}
