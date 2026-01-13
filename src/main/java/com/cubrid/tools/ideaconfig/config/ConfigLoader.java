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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and parses osgi-app.properties configuration files.
 * Supports multi-line values with backslash continuation.
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    // Pattern to match variable references like ${variable-name}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, String> variables = new HashMap<>();

    public ConfigLoader() {
    }

    /**
     * Add a variable for substitution in config values.
     *
     * @param name variable name (without ${})
     * @param value variable value
     */
    public void addVariable(String name, String value) {
        variables.put(name, value);
    }

    /**
     * Load configuration from the specified file.
     *
     * @param configFile path to the configuration file
     * @return parsed ProjectConfig
     * @throws IOException if file cannot be read
     * @throws ProjectConfig.ConfigurationException if configuration is invalid
     */
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
     * Parse properties file with multi-line support.
     */
    private Map<String, String> parseProperties(Path file) throws IOException {
        Map<String, String> properties = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            String currentKey = null;
            StringBuilder currentValue = new StringBuilder();
            boolean continuation = false;

            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines (unless in continuation)
                String trimmed = line.trim();
                if (!continuation && (trimmed.isEmpty() || trimmed.startsWith("#"))) {
                    continue;
                }

                if (continuation) {
                    // Continue previous value
                    if (trimmed.endsWith("\\")) {
                        currentValue.append(trimmed, 0, trimmed.length() - 1);
                    } else {
                        currentValue.append(trimmed);
                        // End of multi-line value
                        properties.put(currentKey, substituteVariables(currentValue.toString()));
                        currentKey = null;
                        currentValue.setLength(0);
                        continuation = false;
                    }
                } else {
                    // New key=value pair
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex == -1) {
                        log.warn("Invalid line (no '='): {}", line);
                        continue;
                    }

                    currentKey = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();

                    if (value.endsWith("\\")) {
                        // Multi-line value starts
                        currentValue.append(value, 0, value.length() - 1);
                        continuation = true;
                    } else {
                        // Single-line value
                        properties.put(currentKey, substituteVariables(value));
                    }
                }
            }

            // Handle case where file ends during continuation
            if (continuation && currentKey != null) {
                properties.put(currentKey, substituteVariables(currentValue.toString()));
            }
        }

        log.debug("Parsed {} properties", properties.size());
        return properties;
    }

    /**
     * Substitute ${variable} references in the value.
     */
    private String substituteVariables(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Map parsed properties to ProjectConfig.
     */
    private ProjectConfig mapToConfig(Map<String, String> properties) {
        ProjectConfig config = new ProjectConfig();

        // Workspace name
        config.setWorkspaceName(properties.get("workspaceName"));

        // Features paths (semicolon-separated)
        parsePathList(properties.get("featuresPaths"), config::addFeaturesPath);

        // Bundles paths (semicolon-separated)
        parsePathList(properties.get("bundlesPaths"), config::addBundlesPath);

        // Repositories (semicolon-separated)
        parsePathList(properties.get("repositories"), config::addRepository);

        // Products paths (semicolon-separated)
        parsePathList(properties.get("productsPaths"), config::addProductsPath);

        // Test bundle paths (semicolon-separated)
        parsePathList(properties.get("testBundlePaths"), config::addTestBundlePath);

        // Test libraries (semicolon-separated)
        parsePathList(properties.get("testLibraries"), config::addTestLibrary);

        // IDEA configuration files paths (semicolon-separated)
        parsePathList(properties.get("ideaConfigurationFilesPaths"), config::addIdeaConfigurationFilesPath);

        // Additional module roots (semicolon-separated)
        parsePathList(properties.get("additionalModuleRoots"), config::addAdditionalModuleRoot);

        // Exclude outputs (semicolon-separated)
        parsePathList(properties.get("excludeOutputs"), config::addExcludeOutput);

        // Optional feature repositories (semicolon-separated)
        parsePathList(properties.get("optionalFeatureRepositories"), config::addOptionalFeatureRepository);

        return config;
    }

    /**
     * Parse semicolon-separated list and add each item via the consumer.
     */
    private void parsePathList(String value, java.util.function.Consumer<String> consumer) {
        if (value == null || value.isBlank()) {
            return;
        }

        String[] parts = value.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                consumer.accept(trimmed);
            }
        }
    }
}
