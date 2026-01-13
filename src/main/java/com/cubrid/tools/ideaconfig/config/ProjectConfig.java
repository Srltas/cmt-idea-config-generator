package com.cubrid.tools.ideaconfig.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Project configuration model parsed from osgi-app.properties.
 * Contains all settings required to generate IntelliJ IDEA configuration.
 */
public class ProjectConfig {

    private String workspaceName;
    private final List<String> featuresPaths = new ArrayList<>();
    private final List<String> bundlesPaths = new ArrayList<>();
    private final List<String> repositories = new ArrayList<>();
    private final List<String> productsPaths = new ArrayList<>();
    private final List<String> testBundlePaths = new ArrayList<>();
    private final List<String> testLibraries = new ArrayList<>();
    private final List<String> ideaConfigurationFilesPaths = new ArrayList<>();
    private final List<String> additionalModuleRoots = new ArrayList<>();
    private final List<String> excludeOutputs = new ArrayList<>();
    private final List<String> optionalFeatureRepositories = new ArrayList<>();

    // Resolved paths (set after loading)
    private Path projectsFolder;
    private Path outputDir;
    private Path eclipseDepsDir;

    public ProjectConfig() {
    }

    // Workspace name

    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    // Features paths

    public List<String> getFeaturesPaths() {
        return Collections.unmodifiableList(featuresPaths);
    }

    public void addFeaturesPath(String path) {
        if (path != null && !path.isBlank()) {
            featuresPaths.add(path.trim());
        }
    }

    // Bundles paths

    public List<String> getBundlesPaths() {
        return Collections.unmodifiableList(bundlesPaths);
    }

    public void addBundlesPath(String path) {
        if (path != null && !path.isBlank()) {
            bundlesPaths.add(path.trim());
        }
    }

    // Repositories

    public List<String> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    public void addRepository(String repository) {
        if (repository != null && !repository.isBlank()) {
            repositories.add(repository.trim());
        }
    }

    // Products paths

    public List<String> getProductsPaths() {
        return Collections.unmodifiableList(productsPaths);
    }

    public void addProductsPath(String path) {
        if (path != null && !path.isBlank()) {
            productsPaths.add(path.trim());
        }
    }

    // Test bundle paths

    public List<String> getTestBundlePaths() {
        return Collections.unmodifiableList(testBundlePaths);
    }

    public void addTestBundlePath(String path) {
        if (path != null && !path.isBlank()) {
            testBundlePaths.add(path.trim());
        }
    }

    // Test libraries

    public List<String> getTestLibraries() {
        return Collections.unmodifiableList(testLibraries);
    }

    public void addTestLibrary(String library) {
        if (library != null && !library.isBlank()) {
            testLibraries.add(library.trim());
        }
    }

    // IDEA configuration files paths

    public List<String> getIdeaConfigurationFilesPaths() {
        return Collections.unmodifiableList(ideaConfigurationFilesPaths);
    }

    public void addIdeaConfigurationFilesPath(String path) {
        if (path != null && !path.isBlank()) {
            ideaConfigurationFilesPaths.add(path.trim());
        }
    }

    // Additional module roots

    public List<String> getAdditionalModuleRoots() {
        return Collections.unmodifiableList(additionalModuleRoots);
    }

    public void addAdditionalModuleRoot(String path) {
        if (path != null && !path.isBlank()) {
            additionalModuleRoots.add(path.trim());
        }
    }

    // Exclude outputs

    public List<String> getExcludeOutputs() {
        return Collections.unmodifiableList(excludeOutputs);
    }

    public void addExcludeOutput(String pattern) {
        if (pattern != null && !pattern.isBlank()) {
            excludeOutputs.add(pattern.trim());
        }
    }

    // Optional feature repositories

    public List<String> getOptionalFeatureRepositories() {
        return Collections.unmodifiableList(optionalFeatureRepositories);
    }

    public void addOptionalFeatureRepository(String path) {
        if (path != null && !path.isBlank()) {
            optionalFeatureRepositories.add(path.trim());
        }
    }

    // Resolved paths

    public Path getProjectsFolder() {
        return projectsFolder;
    }

    public void setProjectsFolder(Path projectsFolder) {
        this.projectsFolder = projectsFolder;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    public Path getEclipseDepsDir() {
        return eclipseDepsDir;
    }

    public void setEclipseDepsDir(Path eclipseDepsDir) {
        this.eclipseDepsDir = eclipseDepsDir;
    }

    /**
     * Validate the configuration.
     *
     * @throws ConfigurationException if configuration is invalid
     */
    public void validate() throws ConfigurationException {
        if (workspaceName == null || workspaceName.isBlank()) {
            throw new ConfigurationException("workspaceName is required");
        }
        if (bundlesPaths.isEmpty()) {
            throw new ConfigurationException("At least one bundlesPath is required");
        }
        if (productsPaths.isEmpty()) {
            throw new ConfigurationException("At least one productsPath is required");
        }
        // Note: repositories is optional - Eclipse dependencies are loaded from --eclipse directory
    }

    @Override
    public String toString() {
        return "ProjectConfig{" +
                "workspaceName='" + workspaceName + '\'' +
                ", featuresPaths=" + featuresPaths +
                ", bundlesPaths=" + bundlesPaths +
                ", repositories=" + repositories +
                ", productsPaths=" + productsPaths +
                ", projectsFolder=" + projectsFolder +
                ", outputDir=" + outputDir +
                '}';
    }

    /**
     * Exception thrown when configuration is invalid.
     */
    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
