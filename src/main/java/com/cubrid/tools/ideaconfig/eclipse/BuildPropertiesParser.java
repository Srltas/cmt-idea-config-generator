package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Eclipse build.properties files.
 * Extracts source folders, output folders, and bin.includes.
 */
public class BuildPropertiesParser {

    private static final Logger log = LoggerFactory.getLogger(BuildPropertiesParser.class);

    // Standard property keys
    private static final String SOURCE_PREFIX = "source..";
    private static final String OUTPUT_PREFIX = "output..";
    private static final String BIN_INCLUDES = "bin.includes";
    private static final String BIN_EXCLUDES = "bin.excludes";
    private static final String EXTRA_PREFIX = "extra.";
    private static final String JARS_COMPILE_ORDER = "jars.compile.order";

    public BuildPropertiesParser() {
    }

    /**
     * Parse build.properties and update the bundle with source/output information.
     *
     * @param buildPropertiesFile path to build.properties
     * @param bundle the bundle to update
     * @throws IOException if file cannot be read
     */
    public void parse(Path buildPropertiesFile, Bundle bundle) throws IOException {
        log.debug("Parsing build.properties: {}", buildPropertiesFile);

        Map<String, String> properties = parseProperties(buildPropertiesFile);

        // Parse source folders (source.. = src/,src2/)
        String sourceFolders = properties.get(SOURCE_PREFIX);
        if (sourceFolders != null) {
            for (String folder : splitValue(sourceFolders)) {
                bundle.addSourceFolder(folder);
            }
        }

        // Parse output folder (output.. = bin/)
        String outputFolder = properties.get(OUTPUT_PREFIX);
        if (outputFolder != null) {
            bundle.setOutputFolder(outputFolder.trim());
        }

        // Handle additional JAR source folders (source.lib/foo.jar = src-foo/)
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("source.") && !key.equals(SOURCE_PREFIX)) {
                // This is a source for an embedded JAR
                String jarName = key.substring("source.".length());
                log.debug("  Found source for embedded JAR {}: {}", jarName, entry.getValue());
            }
        }

        log.debug("Parsed build.properties: {} source folders, output={}",
            bundle.getSourceFolders().size(), bundle.getOutputFolder());
    }

    /**
     * Parse a build.properties file into a map.
     * Handles multi-line values with backslash continuation.
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
                        properties.put(currentKey, currentValue.toString().trim());
                        currentKey = null;
                        currentValue.setLength(0);
                        continuation = false;
                    }
                } else {
                    // New key=value pair
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex == -1) {
                        continue;
                    }

                    currentKey = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();

                    if (value.endsWith("\\")) {
                        currentValue.append(value, 0, value.length() - 1);
                        continuation = true;
                    } else {
                        properties.put(currentKey, value);
                        currentKey = null;
                    }
                }
            }

            // Handle trailing continuation
            if (continuation && currentKey != null) {
                properties.put(currentKey, currentValue.toString().trim());
            }
        }

        return properties;
    }

    /**
     * Split a comma-separated value into individual items.
     */
    private List<String> splitValue(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                // Remove trailing slashes for consistency
                if (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                result.add(trimmed);
            }
        }

        return result;
    }

    /**
     * Parse build.properties for a bundle directory.
     *
     * @param bundleDir the bundle directory
     * @param bundle the bundle to update
     * @throws IOException if file cannot be read
     */
    public void parseForBundle(Path bundleDir, Bundle bundle) throws IOException {
        Path buildPropertiesFile = bundleDir.resolve("build.properties");
        if (Files.exists(buildPropertiesFile)) {
            parse(buildPropertiesFile, bundle);
        } else {
            // Use defaults if no build.properties
            log.debug("No build.properties found, using defaults for {}", bundle.getSymbolicName());
            bundle.addSourceFolder("src");
            bundle.setOutputFolder("bin");
        }
    }

    /**
     * Result of parsing build.properties.
     */
    public static class BuildProperties {
        private final List<String> sourceFolders = new ArrayList<>();
        private String outputFolder = "bin";
        private final List<String> binIncludes = new ArrayList<>();
        private final List<String> binExcludes = new ArrayList<>();

        public List<String> getSourceFolders() {
            return sourceFolders;
        }

        public String getOutputFolder() {
            return outputFolder;
        }

        public void setOutputFolder(String outputFolder) {
            this.outputFolder = outputFolder;
        }

        public List<String> getBinIncludes() {
            return binIncludes;
        }

        public List<String> getBinExcludes() {
            return binExcludes;
        }
    }
}
