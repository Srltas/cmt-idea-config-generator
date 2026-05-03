package com.cubrid.tools.ideaconfig.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Project configuration model parsed from osgi-app.properties.
 */
public class ProjectConfig {

    private String workspaceName;
    private final List<String> featuresPaths = new ArrayList<>();
    private final List<String> bundlesPaths = new ArrayList<>();
    private final List<String> productsPaths = new ArrayList<>();
    private final List<String> additionalModuleRoots = new ArrayList<>();
    private final List<String> testModuleRoots = new ArrayList<>();

    private Path projectsFolder;
    private Path outputDir;
    private Path eclipseDepsDir;

    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    public List<String> getFeaturesPaths() {
        return Collections.unmodifiableList(featuresPaths);
    }

    public void addFeaturesPath(String path) {
        addPath(path, featuresPaths);
    }

    public List<String> getBundlesPaths() {
        return Collections.unmodifiableList(bundlesPaths);
    }

    public void addBundlesPath(String path) {
        addPath(path, bundlesPaths);
    }

    public List<String> getProductsPaths() {
        return Collections.unmodifiableList(productsPaths);
    }

    public void addProductsPath(String path) {
        addPath(path, productsPaths);
    }

    public List<String> getAdditionalModuleRoots() {
        return Collections.unmodifiableList(additionalModuleRoots);
    }

    public void addAdditionalModuleRoot(String path) {
        addPath(path, additionalModuleRoots);
    }

    /**
     * Roots of Maven-style test modules (each contains a pom.xml and src/test/java).
     * These become IDEA modules with test source folders and TEST-scope dependencies.
     */
    public List<String> getTestModuleRoots() {
        return Collections.unmodifiableList(testModuleRoots);
    }

    public void addTestModuleRoot(String path) {
        addPath(path, testModuleRoots);
    }

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
    }

    private static void addPath(String path, List<String> target) {
        if (path != null && !path.isBlank()) {
            target.add(path.trim());
        }
    }

    @Override
    public String toString() {
        return "ProjectConfig{" +
                "workspaceName='" + workspaceName + '\'' +
                ", featuresPaths=" + featuresPaths +
                ", bundlesPaths=" + bundlesPaths +
                ", productsPaths=" + productsPaths +
                ", additionalModuleRoots=" + additionalModuleRoots +
                ", testModuleRoots=" + testModuleRoots +
                ", projectsFolder=" + projectsFolder +
                ", outputDir=" + outputDir +
                '}';
    }

    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
    }
}
