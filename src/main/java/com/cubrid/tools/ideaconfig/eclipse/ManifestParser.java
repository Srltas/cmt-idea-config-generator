package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.Bundle.BundleRequirement;
import com.cubrid.tools.ideaconfig.model.Bundle.PackageImport;
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
 * Parser for OSGi MANIFEST.MF files.
 * Handles multi-line headers and OSGi-specific syntax.
 */
public class ManifestParser {

    private static final Logger log = LoggerFactory.getLogger(ManifestParser.class);

    // Header names
    private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
    private static final String BUNDLE_VERSION = "Bundle-Version";
    private static final String BUNDLE_NAME = "Bundle-Name";
    private static final String BUNDLE_VENDOR = "Bundle-Vendor";
    private static final String BUNDLE_ACTIVATOR = "Bundle-Activator";
    private static final String BUNDLE_EXECUTION_ENV = "Bundle-RequiredExecutionEnvironment";
    private static final String BUNDLE_ACTIVATION_POLICY = "Bundle-ActivationPolicy";
    private static final String BUNDLE_CLASSPATH = "Bundle-ClassPath";
    private static final String REQUIRE_BUNDLE = "Require-Bundle";
    private static final String IMPORT_PACKAGE = "Import-Package";
    private static final String EXPORT_PACKAGE = "Export-Package";
    private static final String FRAGMENT_HOST = "Fragment-Host";
    private static final String MAIN_CLASS = "Main-Class";

    // Pattern to extract directives like ;singleton:=true or ;bundle-version="[1.0,2.0)"
    // Handles both quoted and unquoted values
    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile(
        ";\\s*([^:=]+):?=(?:\"([^\"]*)\"|([^;,\"]+))");

    public ManifestParser() {
    }

    /**
     * Parse a MANIFEST.MF file and create a Bundle.
     *
     * @param manifestFile path to MANIFEST.MF
     * @param bundleDir the bundle directory (parent of META-INF)
     * @return parsed Bundle
     * @throws IOException if file cannot be read
     */
    public Bundle parse(Path manifestFile, Path bundleDir) throws IOException {
        log.debug("Parsing manifest: {}", manifestFile);

        Map<String, String> headers = parseHeaders(manifestFile);

        // Extract symbolic name (fall back to directory name for non-OSGi modules)
        String symbolicNameRaw = headers.get(BUNDLE_SYMBOLIC_NAME);
        SymbolicNameInfo nameInfo;
        if (symbolicNameRaw != null && !symbolicNameRaw.isBlank()) {
            nameInfo = parseSymbolicName(symbolicNameRaw);
        } else {
            // Non-OSGi module: use directory name as symbolic name
            nameInfo = new SymbolicNameInfo();
            nameInfo.name = bundleDir.getFileName().toString();
            log.info("No Bundle-SymbolicName found, using directory name: {}", nameInfo.name);
        }

        // Create bundle
        Bundle bundle = new Bundle(
            nameInfo.name,
            headers.get(BUNDLE_VERSION),
            bundleDir
        );

        bundle.setSingleton(nameInfo.singleton);

        // Parse Main-Class (standalone application entry point)
        String mainClass = headers.get(MAIN_CLASS);
        if (mainClass != null && !mainClass.isBlank()) {
            bundle.setMainClass(mainClass.trim());
            log.info("Found standalone app main class: {}", mainClass.trim());
        }
        bundle.setName(headers.get(BUNDLE_NAME));
        bundle.setVendor(headers.get(BUNDLE_VENDOR));
        bundle.setActivator(headers.get(BUNDLE_ACTIVATOR));
        bundle.setExecutionEnvironment(headers.get(BUNDLE_EXECUTION_ENV));
        bundle.setActivationPolicy(headers.get(BUNDLE_ACTIVATION_POLICY));

        // Parse Bundle-ClassPath
        String classpath = headers.get(BUNDLE_CLASSPATH);
        if (classpath != null) {
            parseClasspath(classpath, bundle);
        }

        // Parse Require-Bundle
        String requireBundle = headers.get(REQUIRE_BUNDLE);
        if (requireBundle != null) {
            parseRequireBundle(requireBundle, bundle);
        }

        // Parse Import-Package
        String importPackage = headers.get(IMPORT_PACKAGE);
        if (importPackage != null) {
            parseImportPackage(importPackage, bundle);
        }

        // Parse Export-Package
        String exportPackage = headers.get(EXPORT_PACKAGE);
        if (exportPackage != null) {
            parseExportPackage(exportPackage, bundle);
        }

        log.debug("Parsed bundle: {} with {} required bundles",
            bundle.getSymbolicName(), bundle.getRequiredBundles().size());

        return bundle;
    }

    /**
     * Parse manifest headers from file.
     * Handles continuation lines (lines starting with space).
     */
    private Map<String, String> parseHeaders(Path file) throws IOException {
        Map<String, String> headers = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            String currentHeader = null;
            StringBuilder currentValue = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // Empty line ends headers section
                    if (currentHeader != null) {
                        headers.put(currentHeader, currentValue.toString().trim());
                    }
                    break;
                }

                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // Continuation line
                    if (currentHeader != null) {
                        currentValue.append(line.substring(1));
                    }
                } else {
                    // New header
                    if (currentHeader != null) {
                        headers.put(currentHeader, currentValue.toString().trim());
                    }

                    int colonIndex = line.indexOf(':');
                    if (colonIndex > 0) {
                        currentHeader = line.substring(0, colonIndex).trim();
                        currentValue = new StringBuilder(line.substring(colonIndex + 1).trim());
                    }
                }
            }

            // Handle last header
            if (currentHeader != null && !headers.containsKey(currentHeader)) {
                headers.put(currentHeader, currentValue.toString().trim());
            }
        }

        return headers;
    }

    /**
     * Parse Bundle-SymbolicName with directives.
     */
    private SymbolicNameInfo parseSymbolicName(String raw) {
        SymbolicNameInfo info = new SymbolicNameInfo();

        int semicolon = raw.indexOf(';');
        if (semicolon > 0) {
            info.name = raw.substring(0, semicolon).trim();
            String directives = raw.substring(semicolon);

            Matcher matcher = DIRECTIVE_PATTERN.matcher(directives);
            while (matcher.find()) {
                String key = matcher.group(1).trim();
                String value = extractDirectiveValue(matcher);
                if ("singleton".equals(key)) {
                    info.singleton = "true".equalsIgnoreCase(value);
                }
            }
        } else {
            info.name = raw.trim();
        }

        return info;
    }

    /**
     * Parse Bundle-ClassPath header.
     */
    private void parseClasspath(String classpath, Bundle bundle) {
        String[] entries = classpath.split(",");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (".".equals(trimmed)) {
                bundle.addClasspathEntry(".");
            } else if (!trimmed.isEmpty()) {
                bundle.addEmbeddedLibrary(trimmed);
                bundle.addClasspathEntry(trimmed);
            }
        }
    }

    /**
     * Parse Require-Bundle header.
     */
    private void parseRequireBundle(String requireBundle, Bundle bundle) {
        // Split by comma, but be careful with version ranges containing commas
        for (String clause : splitClauses(requireBundle)) {
            BundleRequirement req = parseRequireBundleClause(clause);
            if (req != null) {
                bundle.addRequiredBundle(req);
            }
        }
    }

    /**
     * Parse a single Require-Bundle clause.
     */
    private BundleRequirement parseRequireBundleClause(String clause) {
        clause = clause.trim();
        if (clause.isEmpty()) {
            return null;
        }

        int semicolon = clause.indexOf(';');
        String bundleName;
        String directives = "";

        if (semicolon > 0) {
            bundleName = clause.substring(0, semicolon).trim();
            directives = clause.substring(semicolon);
        } else {
            bundleName = clause;
        }

        if (bundleName.isEmpty()) {
            return null;
        }

        BundleRequirement req = new BundleRequirement(bundleName);

        // Parse directives
        Matcher matcher = DIRECTIVE_PATTERN.matcher(directives);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = extractDirectiveValue(matcher);

            switch (key) {
                case "bundle-version" -> req.setVersionRange(value);
                case "resolution" -> req.setOptional("optional".equals(value));
                case "visibility" -> req.setReexport("reexport".equals(value));
            }
        }

        return req;
    }

    /**
     * Parse Import-Package header.
     */
    private void parseImportPackage(String importPackage, Bundle bundle) {
        for (String clause : splitClauses(importPackage)) {
            PackageImport pi = parseImportPackageClause(clause);
            if (pi != null) {
                bundle.addImportedPackage(pi);
            }
        }
    }

    /**
     * Parse a single Import-Package clause.
     */
    private PackageImport parseImportPackageClause(String clause) {
        clause = clause.trim();
        if (clause.isEmpty()) {
            return null;
        }

        int semicolon = clause.indexOf(';');
        String packageName;
        String directives = "";

        if (semicolon > 0) {
            packageName = clause.substring(0, semicolon).trim();
            directives = clause.substring(semicolon);
        } else {
            packageName = clause;
        }

        if (packageName.isEmpty()) {
            return null;
        }

        PackageImport pi = new PackageImport(packageName);

        Matcher matcher = DIRECTIVE_PATTERN.matcher(directives);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = extractDirectiveValue(matcher);
            if ("resolution".equals(key)) {
                pi.setOptional("optional".equals(value));
            }
        }

        return pi;
    }

    /**
     * Parse Export-Package header (extract package names only).
     */
    private void parseExportPackage(String exportPackage, Bundle bundle) {
        for (String clause : splitClauses(exportPackage)) {
            String packageName = parseExportPackageClause(clause);
            if (packageName != null) {
                bundle.addExportedPackage(packageName);
            }
        }
    }

    /**
     * Parse a single Export-Package clause (extract name only).
     */
    private String parseExportPackageClause(String clause) {
        clause = clause.trim();
        if (clause.isEmpty()) {
            return null;
        }

        int semicolon = clause.indexOf(';');
        String packageName = semicolon > 0 ? clause.substring(0, semicolon).trim() : clause;

        return packageName.isEmpty() ? null : packageName;
    }

    /**
     * Split OSGi header into clauses, respecting quoted strings and version ranges.
     */
    private String[] splitClauses(String header) {
        // Simple split that handles version ranges like [1.0,2.0)
        java.util.List<String> clauses = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;

        for (char c : header.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
                current.append(c);
            } else if (!inQuote && (c == '[' || c == '(')) {
                depth++;
                current.append(c);
            } else if (!inQuote && (c == ']' || c == ')')) {
                depth--;
                current.append(c);
            } else if (!inQuote && depth == 0 && c == ',') {
                clauses.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            clauses.add(current.toString());
        }

        return clauses.toArray(new String[0]);
    }

    /**
     * Parse a bundle from its directory.
     *
     * @param bundleDir bundle directory containing META-INF/MANIFEST.MF
     * @return parsed Bundle
     * @throws IOException if manifest cannot be read
     */
    public Bundle parseBundle(Path bundleDir) throws IOException {
        Path manifestFile = bundleDir.resolve("META-INF").resolve("MANIFEST.MF");
        if (!Files.exists(manifestFile)) {
            throw new IOException("MANIFEST.MF not found in " + bundleDir);
        }
        return parse(manifestFile, bundleDir);
    }

    /**
     * Extract directive value from matcher.
     * Handles both quoted (group 2) and unquoted (group 3) values.
     */
    private String extractDirectiveValue(Matcher matcher) {
        String quoted = matcher.group(2);
        if (quoted != null) {
            return quoted.trim();
        }
        String unquoted = matcher.group(3);
        return unquoted != null ? unquoted.trim() : "";
    }

    /**
     * Helper class for parsed symbolic name.
     */
    private static class SymbolicNameInfo {
        String name;
        boolean singleton = false;
    }
}
