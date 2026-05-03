package com.cubrid.tools.ideaconfig.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for version handling.
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

    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("JavaSE-(\\d+(?:\\.\\d+)?)");

    private VersionHelper() {
    }

    /**
     * Convert an Eclipse execution environment (e.g., "JavaSE-21") to an
     * IntelliJ IDEA language level (e.g., "JDK_21"). Defaults to JDK_21.
     */
    public static String toIdeaLanguageLevel(String eclipseEnv) {
        if (eclipseEnv == null || eclipseEnv.isBlank()) {
            return "JDK_21";
        }
        String mapped = ECLIPSE_TO_IDEA_JAVA.get(eclipseEnv);
        if (mapped != null) {
            return mapped;
        }
        Matcher matcher = JAVA_VERSION_PATTERN.matcher(eclipseEnv);
        if (matcher.find()) {
            String version = matcher.group(1);
            if (version.startsWith("1.")) {
                return "JDK_" + version.replace(".", "_");
            }
            return "JDK_" + version;
        }
        return "JDK_21";
    }
}
