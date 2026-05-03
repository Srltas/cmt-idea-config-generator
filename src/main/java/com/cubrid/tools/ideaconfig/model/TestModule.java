package com.cubrid.tools.ideaconfig.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A Maven-style test module (with a pom.xml + src/test/java).
 * Generated as an IDEA module with TEST-scoped dependencies on local OSGi bundles
 * and on the JARs resolved from the Maven local repository.
 */
public class TestModule {

    private final String name;
    private final Path location;

    /** Source folders (relative to location). */
    private final List<SourceFolder> sourceFolders = new ArrayList<>();

    /** Symbolic names of local OSGi bundles this module depends on (TEST scope). */
    private final List<String> localModuleDependencies = new ArrayList<>();

    /** Resolved external JAR paths (from Maven local repo) for TEST scope. */
    private final List<Path> externalLibraries = new ArrayList<>();

    public TestModule(String name, Path location) {
        this.name = Objects.requireNonNull(name);
        this.location = Objects.requireNonNull(location);
    }

    public String getName() {
        return name;
    }

    public Path getLocation() {
        return location;
    }

    public List<SourceFolder> getSourceFolders() {
        return Collections.unmodifiableList(sourceFolders);
    }

    public void addSourceFolder(SourceFolder folder) {
        sourceFolders.add(folder);
    }

    public List<String> getLocalModuleDependencies() {
        return Collections.unmodifiableList(localModuleDependencies);
    }

    public void addLocalModuleDependency(String moduleName) {
        if (moduleName != null && !moduleName.isBlank() && !localModuleDependencies.contains(moduleName)) {
            localModuleDependencies.add(moduleName);
        }
    }

    public List<Path> getExternalLibraries() {
        return Collections.unmodifiableList(externalLibraries);
    }

    public void addExternalLibrary(Path jarPath) {
        if (jarPath != null && !externalLibraries.contains(jarPath)) {
            externalLibraries.add(jarPath);
        }
    }

    @Override
    public String toString() {
        return "TestModule{name='" + name + "', location=" + location + "}";
    }

    /**
     * A source folder within the test module.
     * IDEA distinguishes main vs test, source vs resource.
     */
    public record SourceFolder(String relativePath, Kind kind) {
        public enum Kind {
            MAIN_JAVA,
            MAIN_RESOURCES,
            TEST_JAVA,
            TEST_RESOURCES
        }

        public boolean isTestSource() {
            return kind == Kind.TEST_JAVA || kind == Kind.TEST_RESOURCES;
        }

        public boolean isResource() {
            return kind == Kind.MAIN_RESOURCES || kind == Kind.TEST_RESOURCES;
        }
    }
}
