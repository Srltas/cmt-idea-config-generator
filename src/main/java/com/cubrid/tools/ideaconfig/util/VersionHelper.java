package com.cubrid.tools.ideaconfig.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for version handling and mapping.
 */
public final class VersionHelper {

    // Eclipse execution environment to IntelliJ IDEA language level mapping
    private static final Map<String, String> ECLIPSE_TO_IDEA_JAVA = Map.ofEntries(
            Map.entry("J2SE-1.5", "JDK_1_5"),
            Map.entry("JavaSE-1.6", "JDK_1_6"),
            Map.entry("JavaSE-1.7", "JDK_1_7"),
            Map.entry("JavaSE-1.8", "JDK_1_8"),
            Map.entry("JavaSE-9", "JDK_9"),
            Map.entry("JavaSE-10", "JDK_10"),
            Map.entry("JavaSE-11", "JDK_11"),
            Map.entry("JavaSE-12", "JDK_12"),
            Map.entry("JavaSE-13", "JDK_13"),
            Map.entry("JavaSE-14", "JDK_14"),
            Map.entry("JavaSE-15", "JDK_15"),
            Map.entry("JavaSE-16", "JDK_16"),
            Map.entry("JavaSE-17", "JDK_17"),
            Map.entry("JavaSE-18", "JDK_18"),
            Map.entry("JavaSE-19", "JDK_19"),
            Map.entry("JavaSE-20", "JDK_20"),
            Map.entry("JavaSE-21", "JDK_21"),
            Map.entry("JavaSE-22", "JDK_22"),
            Map.entry("JavaSE-23", "JDK_23"),
            Map.entry("JavaSE-24", "JDK_24")
    );

    // Pattern to extract version number from Eclipse execution environment
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("JavaSE-(\\d+(?:\\.\\d+)?)");

    private VersionHelper() {
        // Utility class
    }

    /**
     * Convert Eclipse execution environment to IntelliJ IDEA language level.
     *
     * @param eclipseEnv Eclipse execution environment (e.g., "JavaSE-21")
     * @return IntelliJ language level (e.g., "JDK_21")
     */
    public static String toIdeaLanguageLevel(String eclipseEnv) {
        if (eclipseEnv == null || eclipseEnv.isBlank()) {
            return "JDK_21"; // Default to JDK 21
        }

        // Try direct mapping
        String mapped = ECLIPSE_TO_IDEA_JAVA.get(eclipseEnv);
        if (mapped != null) {
            return mapped;
        }

        // Try to extract version number and construct
        Matcher matcher = JAVA_VERSION_PATTERN.matcher(eclipseEnv);
        if (matcher.find()) {
            String version = matcher.group(1);
            // Handle versions like 1.8
            if (version.startsWith("1.")) {
                return "JDK_" + version.replace(".", "_");
            }
            return "JDK_" + version;
        }

        // Fallback to JDK 21
        return "JDK_21";
    }

    /**
     * Parse OSGi version string into components.
     *
     * @param version OSGi version string (e.g., "1.2.3.qualifier")
     * @return Version object with parsed components
     */
    public static Version parseOsgiVersion(String version) {
        if (version == null || version.isBlank()) {
            return new Version(0, 0, 0, null);
        }

        String[] parts = version.split("\\.");
        int major = parts.length > 0 ? parseIntSafe(parts[0]) : 0;
        int minor = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
        int micro = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
        String qualifier = parts.length > 3 ? parts[3] : null;

        return new Version(major, minor, micro, qualifier);
    }

    /**
     * Compare two OSGi version strings.
     *
     * @param v1 first version
     * @param v2 second version
     * @return negative if v1 < v2, positive if v1 > v2, 0 if equal
     */
    public static int compareVersions(String v1, String v2) {
        return parseOsgiVersion(v1).compareTo(parseOsgiVersion(v2));
    }

    /**
     * Check if a version matches a version range.
     *
     * @param version the version to check
     * @param range the version range (OSGi format, e.g., "[1.0,2.0)")
     * @return true if version is within range
     */
    public static boolean matchesRange(String version, String range) {
        if (range == null || range.isBlank()) {
            return true; // No range means any version
        }

        Version v = parseOsgiVersion(version);

        // Simple version (no brackets)
        if (!range.contains(",")) {
            if (range.startsWith("[") || range.startsWith("(")) {
                range = range.substring(1);
            }
            if (range.endsWith("]") || range.endsWith(")")) {
                range = range.substring(0, range.length() - 1);
            }
            return v.compareTo(parseOsgiVersion(range)) >= 0;
        }

        // Range format: [min,max) or (min,max]
        boolean includeMin = range.startsWith("[");
        boolean includeMax = range.endsWith("]");

        String inner = range.substring(1, range.length() - 1);
        String[] parts = inner.split(",");
        if (parts.length != 2) {
            return true; // Invalid range
        }

        Version min = parseOsgiVersion(parts[0].trim());
        Version max = parseOsgiVersion(parts[1].trim());

        int minCompare = v.compareTo(min);
        int maxCompare = v.compareTo(max);

        boolean meetsMin = includeMin ? minCompare >= 0 : minCompare > 0;
        boolean meetsMax = includeMax ? maxCompare <= 0 : maxCompare < 0;

        return meetsMin && meetsMax;
    }

    private static int parseIntSafe(String s) {
        try {
            // Handle qualifiers in version numbers (e.g., "1" from "1.qualifier")
            StringBuilder digits = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break;
                }
            }
            return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Version record with major, minor, micro, and qualifier components.
     */
    public record Version(int major, int minor, int micro, String qualifier) implements Comparable<Version> {

        @Override
        public int compareTo(Version other) {
            if (major != other.major) return Integer.compare(major, other.major);
            if (minor != other.minor) return Integer.compare(minor, other.minor);
            if (micro != other.micro) return Integer.compare(micro, other.micro);
            // Qualifier comparison (null is greater than non-null in OSGi)
            if (qualifier == null && other.qualifier == null) return 0;
            if (qualifier == null) return 1;
            if (other.qualifier == null) return -1;
            return qualifier.compareTo(other.qualifier);
        }

        @Override
        public String toString() {
            if (qualifier != null) {
                return major + "." + minor + "." + micro + "." + qualifier;
            }
            return major + "." + minor + "." + micro;
        }
    }
}
