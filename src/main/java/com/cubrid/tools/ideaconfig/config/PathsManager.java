package com.cubrid.tools.ideaconfig.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Resolves project-relative paths to absolute paths and locates output directories.
 */
public class PathsManager {

    private static final Logger log = LoggerFactory.getLogger(PathsManager.class);

    private final Path projectsFolder;
    private final Path outputDir;
    private final Path eclipseDepsDir;
    private final ProjectConfig config;

    private final List<Path> featuresPaths = new ArrayList<>();
    private final List<Path> bundlesPaths = new ArrayList<>();
    private final List<Path> productsPaths = new ArrayList<>();
    private final List<Path> testModuleRoots = new ArrayList<>();

    private Path ideaConfigDir;
    private Path modulesDir;
    private Path librariesDir;
    private Path runConfigurationsDir;

    public PathsManager(Path projectsFolder, Path outputDir, Path eclipseDepsDir, ProjectConfig config) {
        this.projectsFolder = projectsFolder.toAbsolutePath().normalize();
        this.outputDir = outputDir.toAbsolutePath().normalize();
        this.eclipseDepsDir = eclipseDepsDir.toAbsolutePath().normalize();
        this.config = config;
    }

    public void initialize() throws IOException {
        log.info("Initializing paths...");
        log.info("  Projects folder: {}", projectsFolder);
        log.info("  Output directory: {}", outputDir);
        log.info("  Eclipse deps: {}", eclipseDepsDir);

        if (!Files.isDirectory(projectsFolder)) {
            throw new IOException("Projects folder does not exist: " + projectsFolder);
        }

        resolveDirList(config.getFeaturesPaths(), featuresPaths, "Features path");
        resolveDirList(config.getBundlesPaths(), bundlesPaths, "Bundles path");

        for (String productPath : config.getProductsPaths()) {
            Path resolved = resolvePath(productPath);
            if (Files.exists(resolved)) {
                productsPaths.add(resolved);
                log.debug("  Products path: {}", resolved);
            } else {
                log.warn("  Products path not found: {}", resolved);
            }
        }

        resolveDirList(config.getTestModuleRoots(), testModuleRoots, "Test module");

        // Output directory layout
        Path workspaceDir = outputDir.resolve(config.getWorkspaceName());
        ideaConfigDir = workspaceDir.resolve(".idea");
        modulesDir = workspaceDir.resolve("modules");
        librariesDir = ideaConfigDir.resolve("libraries");
        runConfigurationsDir = ideaConfigDir.resolve("runConfigurations");
    }

    private void resolveDirList(List<String> input, List<Path> output, String label) {
        for (String relativePath : input) {
            Path resolved = resolvePath(relativePath);
            if (Files.isDirectory(resolved)) {
                output.add(resolved);
                log.debug("  {}: {}", label, resolved);
            } else {
                log.warn("  {} not found: {}", label, resolved);
            }
        }
    }

    private Path resolvePath(String relativePath) {
        if (relativePath.startsWith("/") || relativePath.matches("^[A-Za-z]:.*")) {
            return Path.of(relativePath).toAbsolutePath().normalize();
        }
        return projectsFolder.resolve(relativePath).normalize();
    }

    public void createOutputDirectories() throws IOException {
        Files.createDirectories(ideaConfigDir);
        Files.createDirectories(modulesDir);
        Files.createDirectories(librariesDir);
        Files.createDirectories(runConfigurationsDir);
        log.info("Created output directories");
    }

    /**
     * Find OSGi bundle directories (those containing META-INF/MANIFEST.MF) under
     * each configured {@code bundlesPaths}. Same directory present in multiple
     * roots is reported once.
     */
    public List<Path> findBundleDirectories() throws IOException {
        List<Path> bundles = new ArrayList<>();
        Set<Path> seen = new HashSet<>();

        for (Path bundlesPath : bundlesPaths) {
            try (Stream<Path> stream = Files.list(bundlesPath)) {
                stream.filter(Files::isDirectory)
                      .filter(PathsManager::isBundleDirectory)
                      .forEach(dir -> {
                          if (seen.add(dir.toAbsolutePath().normalize())) {
                              bundles.add(dir);
                          }
                      });
            }
        }

        log.info("Found {} bundle directories", bundles.size());
        return bundles;
    }

    private static boolean isBundleDirectory(Path dir) {
        return Files.exists(dir.resolve("META-INF").resolve("MANIFEST.MF"));
    }

    public List<Path> findFeatureDirectories() throws IOException {
        List<Path> features = new ArrayList<>();
        for (Path featuresPath : featuresPaths) {
            try (Stream<Path> stream = Files.list(featuresPath)) {
                stream.filter(Files::isDirectory)
                      .filter(dir -> Files.exists(dir.resolve("feature.xml")))
                      .forEach(features::add);
            }
        }
        log.info("Found {} feature directories", features.size());
        return features;
    }

    public Path getProjectsFolder() {
        return projectsFolder;
    }

    public Path getEclipseDepsDir() {
        return eclipseDepsDir;
    }

    public List<Path> getProductsPaths() {
        return Collections.unmodifiableList(productsPaths);
    }

    public List<Path> getTestModuleRoots() {
        return Collections.unmodifiableList(testModuleRoots);
    }

    public Path getIdeaConfigDir() {
        return ideaConfigDir;
    }

    public Path getModulesDir() {
        return modulesDir;
    }

    public Path getLibrariesDir() {
        return librariesDir;
    }

    public Path getRunConfigurationsDir() {
        return runConfigurationsDir;
    }

    public Path getWorkspaceDir() {
        return outputDir.resolve(config.getWorkspaceName());
    }
}
