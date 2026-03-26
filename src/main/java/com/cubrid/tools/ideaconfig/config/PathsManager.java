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
 * Manages and resolves paths for the project.
 * Converts relative paths from configuration to absolute paths.
 */
public class PathsManager {

    private static final Logger log = LoggerFactory.getLogger(PathsManager.class);

    private final Path projectsFolder;
    private final Path outputDir;
    private final Path eclipseDepsDir;
    private final ProjectConfig config;

    // Resolved paths
    private final List<Path> featuresPaths = new ArrayList<>();
    private final List<Path> bundlesPaths = new ArrayList<>();
    private final List<Path> productsPaths = new ArrayList<>();
    private final List<Path> testBundlePaths = new ArrayList<>();
    private final List<Path> additionalModuleRoots = new ArrayList<>();

    // Output directories
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

    /**
     * Initialize and resolve all paths.
     *
     * @throws IOException if required directories don't exist
     */
    public void initialize() throws IOException {
        log.info("Initializing paths...");
        log.info("  Projects folder: {}", projectsFolder);
        log.info("  Output directory: {}", outputDir);
        log.info("  Eclipse deps: {}", eclipseDepsDir);

        // Validate projects folder exists
        if (!Files.isDirectory(projectsFolder)) {
            throw new IOException("Projects folder does not exist: " + projectsFolder);
        }

        // Resolve feature paths
        for (String featuresPath : config.getFeaturesPaths()) {
            Path resolved = resolvePath(featuresPath);
            if (Files.isDirectory(resolved)) {
                featuresPaths.add(resolved);
                log.debug("  Features path: {}", resolved);
            } else {
                log.warn("  Features path not found: {}", resolved);
            }
        }

        // Resolve bundle paths
        for (String bundlesPath : config.getBundlesPaths()) {
            Path resolved = resolvePath(bundlesPath);
            if (Files.isDirectory(resolved)) {
                bundlesPaths.add(resolved);
                log.debug("  Bundles path: {}", resolved);
            } else {
                log.warn("  Bundles path not found: {}", resolved);
            }
        }

        // Resolve product paths
        for (String productsPath : config.getProductsPaths()) {
            Path resolved = resolvePath(productsPath);
            if (Files.exists(resolved)) {
                productsPaths.add(resolved);
                log.debug("  Products path: {}", resolved);
            } else {
                log.warn("  Products path not found: {}", resolved);
            }
        }

        // Resolve test bundle paths
        for (String testPath : config.getTestBundlePaths()) {
            Path resolved = resolvePath(testPath);
            if (Files.isDirectory(resolved)) {
                testBundlePaths.add(resolved);
                log.debug("  Test bundle path: {}", resolved);
            }
        }

        // Resolve additional module roots
        for (String modulePath : config.getAdditionalModuleRoots()) {
            Path resolved = resolvePath(modulePath);
            if (Files.isDirectory(resolved)) {
                additionalModuleRoots.add(resolved);
                log.debug("  Additional module: {}", resolved);
            }
        }

        // Setup output directories
        setupOutputDirectories();

        log.info("Paths initialized successfully");
    }

    /**
     * Resolve a path relative to the projects folder.
     */
    private Path resolvePath(String relativePath) {
        if (relativePath.startsWith("/") || relativePath.matches("^[A-Za-z]:.*")) {
            // Absolute path
            return Path.of(relativePath).toAbsolutePath().normalize();
        }
        return projectsFolder.resolve(relativePath).normalize();
    }

    /**
     * Setup output directory structure.
     */
    private void setupOutputDirectories() throws IOException {
        // Main output structure
        String workspaceName = config.getWorkspaceName();
        Path workspaceDir = outputDir.resolve(workspaceName);

        ideaConfigDir = workspaceDir.resolve(".idea");
        modulesDir = workspaceDir.resolve("modules");
        librariesDir = ideaConfigDir.resolve("libraries");
        runConfigurationsDir = ideaConfigDir.resolve("runConfigurations");

        log.debug("Output directories:");
        log.debug("  IDEA config: {}", ideaConfigDir);
        log.debug("  Modules: {}", modulesDir);
        log.debug("  Libraries: {}", librariesDir);
        log.debug("  Run configs: {}", runConfigurationsDir);
    }

    /**
     * Create all output directories.
     */
    public void createOutputDirectories() throws IOException {
        Files.createDirectories(ideaConfigDir);
        Files.createDirectories(modulesDir);
        Files.createDirectories(librariesDir);
        Files.createDirectories(runConfigurationsDir);
        log.info("Created output directories");
    }

    /**
     * Find all bundle directories in the configured bundle paths.
     *
     * @return list of bundle directories (directories containing META-INF/MANIFEST.MF)
     */
    public List<Path> findBundleDirectories() throws IOException {
        List<Path> bundles = new ArrayList<>();
        Set<Path> seen = new HashSet<>();

        for (Path bundlesPath : bundlesPaths) {
            try (Stream<Path> stream = Files.list(bundlesPath)) {
                stream.filter(Files::isDirectory)
                      .filter(this::isBundleDirectory)
                      .forEach(dir -> {
                          Path normalized = dir.toAbsolutePath().normalize();
                          if (seen.add(normalized)) {
                              bundles.add(dir);
                          }
                      });
            }
        }

        // Add additional module roots (skip duplicates already found in bundle paths)
        for (Path modulePath : additionalModuleRoots) {
            Path normalized = modulePath.toAbsolutePath().normalize();
            if (isBundleDirectory(modulePath) && seen.add(normalized)) {
                bundles.add(modulePath);
            }
        }

        log.info("Found {} bundle directories", bundles.size());
        return bundles;
    }

    /**
     * Check if a directory is an OSGi bundle (has META-INF/MANIFEST.MF).
     */
    private boolean isBundleDirectory(Path dir) {
        return Files.exists(dir.resolve("META-INF").resolve("MANIFEST.MF"));
    }

    /**
     * Find all feature directories in the configured feature paths.
     *
     * @return list of feature directories (directories containing feature.xml)
     */
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

    /**
     * Get the relative path from the workspace to a file.
     *
     * @param file the absolute file path
     * @return relative path from workspace root
     */
    public String getRelativePathFromWorkspace(Path file) {
        try {
            Path workspaceDir = outputDir.resolve(config.getWorkspaceName());
            return workspaceDir.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // Cannot relativize, return absolute
            return file.toString().replace('\\', '/');
        }
    }

    /**
     * Get the relative path from one file to another.
     *
     * @param from source file
     * @param to target file
     * @return relative path
     */
    public String getRelativePath(Path from, Path to) {
        try {
            Path fromDir = Files.isDirectory(from) ? from : from.getParent();
            return fromDir.relativize(to).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return to.toString().replace('\\', '/');
        }
    }

    // Getters

    public Path getProjectsFolder() {
        return projectsFolder;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public Path getEclipseDepsDir() {
        return eclipseDepsDir;
    }

    public List<Path> getFeaturesPaths() {
        return Collections.unmodifiableList(featuresPaths);
    }

    public List<Path> getBundlesPaths() {
        return Collections.unmodifiableList(bundlesPaths);
    }

    public List<Path> getProductsPaths() {
        return Collections.unmodifiableList(productsPaths);
    }

    public List<Path> getTestBundlePaths() {
        return Collections.unmodifiableList(testBundlePaths);
    }

    public List<Path> getAdditionalModuleRoots() {
        return Collections.unmodifiableList(additionalModuleRoots);
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

    /**
     * Get path for a module's .iml file.
     */
    public Path getModuleImlPath(String bundleName) {
        return modulesDir.resolve(bundleName + ".iml");
    }

    /**
     * Get path for a library definition file.
     */
    public Path getLibraryPath(String libraryName) {
        // Sanitize library name for filename
        String safeName = libraryName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return librariesDir.resolve(safeName + ".xml");
    }

    /**
     * Get path for a run configuration file.
     */
    public Path getRunConfigurationPath(String configName) {
        String safeName = configName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return runConfigurationsDir.resolve(safeName + ".xml");
    }
}
