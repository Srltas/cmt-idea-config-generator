package com.cubrid.tools.ideaconfig.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the parts of an Eclipse RCP project live, discovered from the standard layout:
 * {@code plugins/} and {@code features/} for bundles, a {@code *.product} file for the
 * application, and Maven-style test modules under {@code tests/}.
 */
public class ProjectConfig {

    private static final Logger log = LoggerFactory.getLogger(ProjectConfig.class);

    private static final String WORKSPACE_NAME = "cubrid-migration-idea";
    private static final String BUNDLES_DIR = "plugins";
    private static final String FEATURES_DIR = "features";
    private static final String TEST_MODULES_DIR = "tests";
    private static final int PRODUCT_SEARCH_DEPTH = 3;

    private final List<String> featuresPaths = new ArrayList<>();
    private final List<String> bundlesPaths = new ArrayList<>();
    private final List<String> productsPaths = new ArrayList<>();
    private final List<String> testModuleRoots = new ArrayList<>();

    /**
     * Inspect the project folder and record what is actually there. Paths are kept
     * relative to {@code projectsFolder}.
     */
    public static ProjectConfig discover(Path projectsFolder) throws IOException {
        ProjectConfig config = new ProjectConfig();

        addIfDirectory(projectsFolder, BUNDLES_DIR, config.bundlesPaths, "Bundles");
        addIfDirectory(projectsFolder, FEATURES_DIR, config.featuresPaths, "Features");

        for (Path product : findProducts(projectsFolder)) {
            String relative = toRelative(projectsFolder, product);
            config.productsPaths.add(relative);
            log.info("  Product: {}", relative);
        }

        for (Path module : findTestModules(projectsFolder)) {
            String relative = toRelative(projectsFolder, module);
            config.testModuleRoots.add(relative);
            log.info("  Test module: {}", relative);
        }

        return config;
    }

    private static void addIfDirectory(Path root, String name, List<String> target, String label) {
        if (Files.isDirectory(root.resolve(name))) {
            target.add(name);
            log.info("  {}: {}", label, name);
        }
    }

    /** Product files anywhere near the top of the project, ignoring build output. */
    private static List<Path> findProducts(Path projectsFolder) throws IOException {
        try (var stream = Files.walk(projectsFolder, PRODUCT_SEARCH_DEPTH)) {
            return stream.filter(Files::isRegularFile)
                         .filter(p -> p.getFileName().toString().endsWith(".product"))
                         .filter(p -> !isBuildOutput(projectsFolder.relativize(p)))
                         .sorted()
                         .toList();
        }
    }

    private static boolean isBuildOutput(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if ("target".equals(name) || "bin".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Maven-style test modules: direct children of {@code tests/} holding a pom.xml. */
    private static List<Path> findTestModules(Path projectsFolder) throws IOException {
        Path testsDir = projectsFolder.resolve(TEST_MODULES_DIR);
        if (!Files.isDirectory(testsDir)) {
            return List.of();
        }
        try (var stream = Files.list(testsDir)) {
            return stream.filter(Files::isDirectory)
                         .filter(dir -> Files.isRegularFile(dir.resolve("pom.xml")))
                         .sorted()
                         .toList();
        }
    }

    private static String toRelative(Path projectsFolder, Path path) {
        return projectsFolder.relativize(path).toString().replace('\\', '/');
    }

    public String getWorkspaceName() {
        return WORKSPACE_NAME;
    }

    public List<String> getFeaturesPaths() {
        return Collections.unmodifiableList(featuresPaths);
    }

    public List<String> getBundlesPaths() {
        return Collections.unmodifiableList(bundlesPaths);
    }

    public List<String> getProductsPaths() {
        return Collections.unmodifiableList(productsPaths);
    }

    public List<String> getTestModuleRoots() {
        return Collections.unmodifiableList(testModuleRoots);
    }

    public void validate() throws ConfigurationException {
        if (bundlesPaths.isEmpty()) {
            throw new ConfigurationException(
                    "No '" + BUNDLES_DIR + "' directory - is this an Eclipse RCP project?");
        }
        if (productsPaths.isEmpty()) {
            throw new ConfigurationException("No *.product file found in the project");
        }
    }

    @Override
    public String toString() {
        return "ProjectConfig{" +
                "workspaceName='" + WORKSPACE_NAME + '\'' +
                ", featuresPaths=" + featuresPaths +
                ", bundlesPaths=" + bundlesPaths +
                ", productsPaths=" + productsPaths +
                ", testModuleRoots=" + testModuleRoots +
                '}';
    }

    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
    }
}
