package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for Eclipse build.properties files.
 * Extracts source folders and the output folder.
 */
public class BuildPropertiesParser {

    private static final Logger log = LoggerFactory.getLogger(BuildPropertiesParser.class);

    private static final String SOURCE_PREFIX = "source..";
    private static final String OUTPUT_PREFIX = "output..";

    /**
     * Parse build.properties (or apply defaults) for a bundle directory.
     */
    public void parseForBundle(Path bundleDir, Bundle bundle) throws IOException {
        Path buildPropertiesFile = bundleDir.resolve("build.properties");
        if (!Files.exists(buildPropertiesFile)) {
            log.debug("No build.properties found, using defaults for {}", bundle.getSymbolicName());
            bundle.addSourceFolder("src");
            bundle.setOutputFolder("bin");
            return;
        }

        Map<String, String> properties = parseProperties(buildPropertiesFile);

        String sourceFolders = properties.get(SOURCE_PREFIX);
        if (sourceFolders != null) {
            for (String folder : sourceFolders.split(",")) {
                String trimmed = folder.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                bundle.addSourceFolder(trimmed);
            }
        }

        String outputFolder = properties.get(OUTPUT_PREFIX);
        if (outputFolder != null) {
            bundle.setOutputFolder(outputFolder.trim());
        }

        log.debug("Parsed build.properties: {} source folders, output={}",
            bundle.getSourceFolders().size(), bundle.getOutputFolder());
    }

    /**
     * Parse a properties file into a map. Supports multi-line values with
     * trailing backslash continuation.
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
                        properties.put(currentKey, currentValue.toString().trim());
                        currentKey = null;
                        currentValue.setLength(0);
                        continuation = false;
                    }
                } else {
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex == -1) continue;

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

            if (continuation && currentKey != null) {
                properties.put(currentKey, currentValue.toString().trim());
            }
        }

        return properties;
    }
}
