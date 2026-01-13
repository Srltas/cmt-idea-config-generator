package com.cubrid.tools.ideaconfig.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an OSGi bundle parsed from MANIFEST.MF.
 */
public class Bundle {

    private final String symbolicName;
    private final String version;
    private final Path location;

    private String name;
    private String vendor;
    private String activator;
    private String executionEnvironment;
    private String activationPolicy;
    private boolean singleton;

    private final List<String> classpath = new ArrayList<>();
    private final List<BundleRequirement> requiredBundles = new ArrayList<>();
    private final List<PackageImport> importedPackages = new ArrayList<>();
    private final List<String> exportedPackages = new ArrayList<>();

    // Source folders from build.properties
    private final List<String> sourceFolders = new ArrayList<>();
    private String outputFolder;

    // Additional libraries from Bundle-ClassPath
    private final List<String> embeddedLibraries = new ArrayList<>();

    public Bundle(String symbolicName, String version, Path location) {
        this.symbolicName = Objects.requireNonNull(symbolicName, "symbolicName is required");
        this.version = version != null ? version : "0.0.0";
        this.location = location;
    }

    // Basic properties

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getVersion() {
        return version;
    }

    public Path getLocation() {
        return location;
    }

    public String getName() {
        return name != null ? name : symbolicName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getActivator() {
        return activator;
    }

    public void setActivator(String activator) {
        this.activator = activator;
    }

    public String getExecutionEnvironment() {
        return executionEnvironment;
    }

    public void setExecutionEnvironment(String executionEnvironment) {
        this.executionEnvironment = executionEnvironment;
    }

    public String getActivationPolicy() {
        return activationPolicy;
    }

    public void setActivationPolicy(String activationPolicy) {
        this.activationPolicy = activationPolicy;
    }

    public boolean isSingleton() {
        return singleton;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    // Classpath

    public List<String> getClasspath() {
        return Collections.unmodifiableList(classpath);
    }

    public void addClasspathEntry(String entry) {
        if (entry != null && !entry.isBlank()) {
            classpath.add(entry.trim());
        }
    }

    // Required bundles

    public List<BundleRequirement> getRequiredBundles() {
        return Collections.unmodifiableList(requiredBundles);
    }

    public void addRequiredBundle(BundleRequirement requirement) {
        if (requirement != null) {
            requiredBundles.add(requirement);
        }
    }

    // Imported packages

    public List<PackageImport> getImportedPackages() {
        return Collections.unmodifiableList(importedPackages);
    }

    public void addImportedPackage(PackageImport packageImport) {
        if (packageImport != null) {
            importedPackages.add(packageImport);
        }
    }

    // Exported packages

    public List<String> getExportedPackages() {
        return Collections.unmodifiableList(exportedPackages);
    }

    public void addExportedPackage(String packageName) {
        if (packageName != null && !packageName.isBlank()) {
            exportedPackages.add(packageName.trim());
        }
    }

    // Source folders

    public List<String> getSourceFolders() {
        return Collections.unmodifiableList(sourceFolders);
    }

    public void addSourceFolder(String folder) {
        if (folder != null && !folder.isBlank()) {
            sourceFolders.add(folder.trim());
        }
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }

    // Embedded libraries

    public List<String> getEmbeddedLibraries() {
        return Collections.unmodifiableList(embeddedLibraries);
    }

    public void addEmbeddedLibrary(String library) {
        if (library != null && !library.isBlank() && !".".equals(library.trim())) {
            embeddedLibraries.add(library.trim());
        }
    }

    // Utility methods

    /**
     * Check if this bundle is a fragment.
     */
    public boolean isFragment() {
        // Fragments have Fragment-Host header - detected during parsing
        return false; // Will be set by subclass or flag
    }

    /**
     * Get the META-INF directory path.
     */
    public Path getMetaInfPath() {
        return location.resolve("META-INF");
    }

    /**
     * Get the MANIFEST.MF file path.
     */
    public Path getManifestPath() {
        return getMetaInfPath().resolve("MANIFEST.MF");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bundle bundle = (Bundle) o;
        return symbolicName.equals(bundle.symbolicName) && version.equals(bundle.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbolicName, version);
    }

    @Override
    public String toString() {
        return "Bundle{" +
                "symbolicName='" + symbolicName + '\'' +
                ", version='" + version + '\'' +
                ", requiredBundles=" + requiredBundles.size() +
                ", location=" + location +
                '}';
    }

    /**
     * Represents a Require-Bundle dependency.
     */
    public static class BundleRequirement {
        private final String bundleName;
        private String versionRange;
        private boolean optional;
        private boolean reexport;

        public BundleRequirement(String bundleName) {
            this.bundleName = Objects.requireNonNull(bundleName);
        }

        public String getBundleName() {
            return bundleName;
        }

        public String getVersionRange() {
            return versionRange;
        }

        public void setVersionRange(String versionRange) {
            this.versionRange = versionRange;
        }

        public boolean isOptional() {
            return optional;
        }

        public void setOptional(boolean optional) {
            this.optional = optional;
        }

        public boolean isReexport() {
            return reexport;
        }

        public void setReexport(boolean reexport) {
            this.reexport = reexport;
        }

        @Override
        public String toString() {
            return bundleName + (optional ? " (optional)" : "") + (reexport ? " (reexport)" : "");
        }
    }

    /**
     * Represents an Import-Package dependency.
     */
    public static class PackageImport {
        private final String packageName;
        private String versionRange;
        private boolean optional;

        public PackageImport(String packageName) {
            this.packageName = Objects.requireNonNull(packageName);
        }

        public String getPackageName() {
            return packageName;
        }

        public String getVersionRange() {
            return versionRange;
        }

        public void setVersionRange(String versionRange) {
            this.versionRange = versionRange;
        }

        public boolean isOptional() {
            return optional;
        }

        public void setOptional(boolean optional) {
            this.optional = optional;
        }

        @Override
        public String toString() {
            return packageName + (optional ? " (optional)" : "");
        }
    }
}
