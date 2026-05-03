package com.cubrid.tools.ideaconfig.util;

import java.nio.file.Path;

/**
 * Helper class for path operations.
 */
public final class PathHelper {

    private PathHelper() {
    }

    /**
     * Normalize a Path to use forward slashes.
     */
    public static String normalize(Path path) {
        if (path == null) {
            return null;
        }
        return path.toString().replace('\\', '/');
    }

    /**
     * Convert a path to a file URI string using Java's built-in URI conversion.
     * Mac/Linux: /Users/dev/file.jar  -> file:///Users/dev/file.jar
     * Windows:   C:\Users\dev\file.jar -> file:///C:/Users/dev/file.jar
     */
    public static String toFileUri(Path path) {
        return path.toAbsolutePath().toUri().toString();
    }

    /**
     * Convert a path to a file URI string for use in .properties files,
     * where the colon after `file` must be escaped.
     */
    public static String toEscapedFileUri(Path path) {
        return toFileUri(path).replace("file:", "file\\:");
    }
}
