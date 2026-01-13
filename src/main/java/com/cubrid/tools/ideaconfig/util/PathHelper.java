package com.cubrid.tools.ideaconfig.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Helper class for path operations.
 */
public final class PathHelper {

    private PathHelper() {
        // Utility class
    }

    /**
     * Normalize a path string to use forward slashes.
     *
     * @param path the path string
     * @return normalized path with forward slashes
     */
    public static String normalize(String path) {
        if (path == null) {
            return null;
        }
        return path.replace('\\', '/');
    }

    /**
     * Normalize a Path to use forward slashes.
     *
     * @param path the path
     * @return normalized path string with forward slashes
     */
    public static String normalize(Path path) {
        if (path == null) {
            return null;
        }
        return path.toString().replace('\\', '/');
    }

    /**
     * Convert a path to a file:// URL.
     *
     * @param path the path
     * @return file URL string
     */
    public static String toFileUrl(Path path) {
        String normalized = normalize(path.toAbsolutePath());
        if (normalized.startsWith("/")) {
            return "file://" + normalized;
        } else {
            // Windows path
            return "file:///" + normalized;
        }
    }

    /**
     * Convert a path to a file URI string using Java's built-in URI conversion.
     * This ensures correct URI format on all platforms.
     *
     * Examples:
     * - Mac/Linux: /Users/dev/file.jar -> file:///Users/dev/file.jar
     * - Windows: C:\Users\dev\file.jar -> file:///C:/Users/dev/file.jar
     *
     * @param path the path
     * @return file URI string
     */
    public static String toFileUri(Path path) {
        return path.toAbsolutePath().toUri().toString();
    }

    /**
     * Convert a path to a file URI string for use in properties files.
     * The colon after 'file' is escaped as '\:' for .properties format.
     *
     * @param path the path
     * @return escaped file URI string for properties files
     */
    public static String toEscapedFileUri(Path path) {
        String uri = toFileUri(path);
        // In .properties files, colon needs to be escaped
        return uri.replace("file:", "file\\:");
    }

    /**
     * Convert a path to a jar:// URL for a JAR file.
     *
     * @param jarPath path to the JAR file
     * @return jar URL string
     */
    public static String toJarUrl(Path jarPath) {
        return "jar://" + normalize(jarPath.toAbsolutePath()) + "!/";
    }

    /**
     * Create an IntelliJ-style $MODULE_DIR$ relative path.
     *
     * @param moduleDir the module directory
     * @param target the target path
     * @return IntelliJ-style path with $MODULE_DIR$ variable
     */
    public static String toModuleDirPath(Path moduleDir, Path target) {
        try {
            Path relative = moduleDir.relativize(target.toAbsolutePath());
            return "file://$MODULE_DIR$/" + normalize(relative);
        } catch (IllegalArgumentException e) {
            // Cannot relativize, use absolute path
            return toFileUrl(target);
        }
    }

    /**
     * Create an IntelliJ-style $PROJECT_DIR$ relative path.
     *
     * @param projectDir the project directory
     * @param target the target path
     * @return IntelliJ-style path with $PROJECT_DIR$ variable
     */
    public static String toProjectDirPath(Path projectDir, Path target) {
        try {
            Path relative = projectDir.relativize(target.toAbsolutePath());
            return "$PROJECT_DIR$/" + normalize(relative);
        } catch (IllegalArgumentException e) {
            return normalize(target.toAbsolutePath());
        }
    }

    /**
     * Get the file name without extension.
     *
     * @param path the path
     * @return file name without extension
     */
    public static String getNameWithoutExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    /**
     * Get the file extension.
     *
     * @param path the path
     * @return file extension (without dot) or empty string if no extension
     */
    public static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }

    /**
     * Check if a file is a JAR file.
     *
     * @param path the path
     * @return true if the file has .jar extension
     */
    public static boolean isJarFile(Path path) {
        return "jar".equalsIgnoreCase(getExtension(path));
    }

    /**
     * Find all JAR files in a directory (non-recursive).
     *
     * @param directory the directory to search
     * @return stream of JAR file paths
     * @throws IOException if directory cannot be read
     */
    public static Stream<Path> findJarFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Stream.empty();
        }
        return Files.list(directory).filter(PathHelper::isJarFile);
    }

    /**
     * Find all JAR files in a directory (recursive).
     *
     * @param directory the directory to search
     * @return stream of JAR file paths
     * @throws IOException if directory cannot be read
     */
    public static Stream<Path> findJarFilesRecursive(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Stream.empty();
        }
        return Files.walk(directory).filter(PathHelper::isJarFile);
    }

    /**
     * Ensure a directory exists, creating it if necessary.
     *
     * @param directory the directory path
     * @throws IOException if directory cannot be created
     */
    public static void ensureDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    /**
     * Create a safe filename from a string.
     *
     * @param name the original name
     * @return safe filename with invalid characters replaced
     */
    public static String toSafeFilename(String name) {
        if (name == null) {
            return "unnamed";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Check if a path is absolute.
     *
     * @param path the path string
     * @return true if the path is absolute
     */
    public static boolean isAbsolute(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // Unix absolute path
        if (path.startsWith("/")) {
            return true;
        }
        // Windows absolute path (e.g., C:\)
        return path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
    }
}
