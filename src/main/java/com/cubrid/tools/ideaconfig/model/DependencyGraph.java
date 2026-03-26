package com.cubrid.tools.ideaconfig.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Dependency graph for OSGi bundles.
 * Manages bundle dependencies and provides topological ordering.
 */
public class DependencyGraph {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraph.class);

    // All bundles indexed by symbolic name
    private final Map<String, Bundle> bundlesByName = new LinkedHashMap<>();

    // Adjacency list: bundle -> bundles it depends on
    private final Map<String, Set<String>> dependencies = new HashMap<>();

    // Reverse adjacency list: bundle -> bundles that depend on it
    private final Map<String, Set<String>> dependents = new HashMap<>();

    // Resolved external bundles (from P2 repositories)
    private final Map<String, ExternalBundle> externalBundles = new LinkedHashMap<>();

    // Fragment-Host mapping: host bundle -> list of fragment bundles
    // In OSGi, fragments attach to a host bundle and provide classes/resources.
    // In IDEA, fragments must be added as separate library references.
    private final Map<String, Set<String>> fragmentsByHost = new HashMap<>();

    // Unresolved dependencies
    private final Set<UnresolvedDependency> unresolvedDependencies = new LinkedHashSet<>();

    public DependencyGraph() {
    }

    /**
     * Add a bundle to the graph.
     *
     * @param bundle the bundle to add
     */
    public void addBundle(Bundle bundle) {
        String name = bundle.getSymbolicName();
        bundlesByName.put(name, bundle);
        dependencies.computeIfAbsent(name, k -> new LinkedHashSet<>());
        dependents.computeIfAbsent(name, k -> new LinkedHashSet<>());
    }

    /**
     * Add a dependency edge from one bundle to another.
     *
     * @param from dependent bundle symbolic name
     * @param to dependency bundle symbolic name
     * @param optional whether the dependency is optional
     */
    public void addDependency(String from, String to, boolean optional) {
        dependencies.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
        dependents.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);

        log.debug("Added dependency: {} -> {} (optional={})", from, to, optional);
    }

    /**
     * Add an unresolved dependency.
     *
     * @param bundleName the bundle with the unresolved dependency
     * @param dependencyName the name of the unresolved dependency
     * @param versionRange the requested version range
     * @param optional whether the dependency is optional
     */
    public void addUnresolvedDependency(String bundleName, String dependencyName,
                                        String versionRange, boolean optional) {
        unresolvedDependencies.add(new UnresolvedDependency(
            bundleName, dependencyName, versionRange, optional
        ));
    }

    /**
     * Add an external bundle (resolved from P2 repository).
     *
     * @param externalBundle the external bundle
     */
    public void addExternalBundle(ExternalBundle externalBundle) {
        externalBundles.put(externalBundle.getSymbolicName(), externalBundle);

        // If this is a fragment, register it with its host
        if (externalBundle.getFragmentHost() != null) {
            fragmentsByHost.computeIfAbsent(externalBundle.getFragmentHost(), k -> new LinkedHashSet<>())
                .add(externalBundle.getSymbolicName());
        }
    }

    /**
     * Get a bundle by its symbolic name.
     *
     * @param symbolicName the symbolic name
     * @return the bundle, or null if not found
     */
    public Bundle getBundle(String symbolicName) {
        return bundlesByName.get(symbolicName);
    }

    /**
     * Get all bundles.
     *
     * @return collection of all bundles
     */
    public Collection<Bundle> getAllBundles() {
        return Collections.unmodifiableCollection(bundlesByName.values());
    }

    /**
     * Get all bundle symbolic names.
     *
     * @return set of symbolic names
     */
    public Set<String> getAllBundleNames() {
        return Collections.unmodifiableSet(bundlesByName.keySet());
    }

    /**
     * Get dependencies of a bundle.
     *
     * @param symbolicName the bundle's symbolic name
     * @return set of dependency symbolic names
     */
    public Set<String> getDependencies(String symbolicName) {
        return Collections.unmodifiableSet(
            dependencies.getOrDefault(symbolicName, Collections.emptySet())
        );
    }

    /**
     * Get dependents of a bundle (bundles that depend on it).
     *
     * @param symbolicName the bundle's symbolic name
     * @return set of dependent symbolic names
     */
    public Set<String> getDependents(String symbolicName) {
        return Collections.unmodifiableSet(
            dependents.getOrDefault(symbolicName, Collections.emptySet())
        );
    }

    /**
     * Get all external bundles.
     *
     * @return collection of external bundles
     */
    public Collection<ExternalBundle> getExternalBundles() {
        return Collections.unmodifiableCollection(externalBundles.values());
    }

    /**
     * Get an external bundle by name.
     *
     * @param symbolicName the symbolic name
     * @return the external bundle, or null if not found
     */
    public ExternalBundle getExternalBundle(String symbolicName) {
        return externalBundles.get(symbolicName);
    }

    /**
     * Get all unresolved dependencies.
     *
     * @return set of unresolved dependencies
     */
    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return Collections.unmodifiableSet(unresolvedDependencies);
    }

    /**
     * Get only non-optional unresolved dependencies.
     *
     * @return set of required but unresolved dependencies
     */
    public Set<UnresolvedDependency> getRequiredUnresolvedDependencies() {
        Set<UnresolvedDependency> required = new LinkedHashSet<>();
        for (UnresolvedDependency dep : unresolvedDependencies) {
            if (!dep.isOptional()) {
                required.add(dep);
            }
        }
        return required;
    }

    /**
     * Check if a bundle is known (local or external).
     *
     * @param symbolicName the symbolic name
     * @return true if the bundle is known
     */
    public boolean isKnownBundle(String symbolicName) {
        return bundlesByName.containsKey(symbolicName) ||
               externalBundles.containsKey(symbolicName);
    }

    /**
     * Check if a bundle is a local (project) bundle.
     *
     * @param symbolicName the symbolic name
     * @return true if it's a local bundle
     */
    public boolean isLocalBundle(String symbolicName) {
        return bundlesByName.containsKey(symbolicName);
    }

    /**
     * Check if a bundle is an external bundle.
     *
     * @param symbolicName the symbolic name
     * @return true if it's an external bundle
     */
    public boolean isExternalBundle(String symbolicName) {
        return externalBundles.containsKey(symbolicName);
    }

    /**
     * Get bundles in topological order (dependencies before dependents).
     * Uses Kahn's algorithm.
     *
     * @return list of bundles in dependency order
     * @throws IllegalStateException if there's a circular dependency
     */
    public List<Bundle> getTopologicalOrder() {
        List<Bundle> result = new ArrayList<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // Calculate in-degrees (only for local bundles)
        for (String name : bundlesByName.keySet()) {
            int degree = 0;
            for (String dep : dependencies.getOrDefault(name, Collections.emptySet())) {
                if (bundlesByName.containsKey(dep)) {
                    degree++;
                }
            }
            inDegree.put(name, degree);
            if (degree == 0) {
                queue.add(name);
            }
        }

        // Process bundles
        while (!queue.isEmpty()) {
            String current = queue.poll();
            Bundle bundle = bundlesByName.get(current);
            if (bundle != null) {
                result.add(bundle);
            }

            for (String dependent : dependents.getOrDefault(current, Collections.emptySet())) {
                if (bundlesByName.containsKey(dependent)) {
                    int newDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newDegree);
                    if (newDegree == 0) {
                        queue.add(dependent);
                    }
                }
            }
        }

        // Check for cycles
        if (result.size() != bundlesByName.size()) {
            Set<String> processed = new HashSet<>();
            for (Bundle b : result) {
                processed.add(b.getSymbolicName());
            }
            Set<String> remaining = new HashSet<>(bundlesByName.keySet());
            remaining.removeAll(processed);

            log.warn("Circular dependency detected among: {}", remaining);
            // Add remaining bundles in arbitrary order
            for (String name : remaining) {
                result.add(bundlesByName.get(name));
            }
        }

        return result;
    }

    /**
     * Detect circular dependencies.
     *
     * @return list of cycles (each cycle is a list of bundle names)
     */
    public List<List<String>> detectCycles() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String bundle : bundlesByName.keySet()) {
            if (!visited.contains(bundle)) {
                List<String> currentPath = new ArrayList<>();
                detectCyclesDFS(bundle, visited, inStack, currentPath, cycles);
            }
        }

        return cycles;
    }

    private void detectCyclesDFS(String current, Set<String> visited, Set<String> inStack,
                                  List<String> currentPath, List<List<String>> cycles) {
        visited.add(current);
        inStack.add(current);
        currentPath.add(current);

        for (String dep : dependencies.getOrDefault(current, Collections.emptySet())) {
            if (!bundlesByName.containsKey(dep)) {
                continue; // Skip external dependencies
            }

            if (!visited.contains(dep)) {
                detectCyclesDFS(dep, visited, inStack, currentPath, cycles);
            } else if (inStack.contains(dep)) {
                // Found cycle
                int startIdx = currentPath.indexOf(dep);
                List<String> cycle = new ArrayList<>(currentPath.subList(startIdx, currentPath.size()));
                cycle.add(dep);
                cycles.add(cycle);
            }
        }

        inStack.remove(current);
        currentPath.remove(currentPath.size() - 1);
    }

    /**
     * Get all transitive dependencies of a bundle.
     *
     * @param symbolicName the bundle's symbolic name
     * @return set of all transitive dependencies
     */
    public Set<String> getTransitiveDependencies(String symbolicName) {
        Set<String> result = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(symbolicName);
        visited.add(symbolicName);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String dep : dependencies.getOrDefault(current, Collections.emptySet())) {
                if (!visited.contains(dep)) {
                    visited.add(dep);
                    result.add(dep);
                    queue.add(dep);
                }
            }
        }

        return result;
    }

    /**
     * Get the transitive re-export closure for a set of external bundles.
     * In OSGi, when bundle A re-exports bundle B (visibility:=reexport),
     * any bundle that requires A also implicitly has access to B's exports.
     * This method follows those re-export chains transitively.
     *
     * @param externalBundleNames initial set of external bundle names
     * @return the expanded set including all re-exported bundles
     */
    public Set<String> getReExportClosure(Set<String> externalBundleNames) {
        Set<String> result = new LinkedHashSet<>(externalBundleNames);
        Queue<String> queue = new LinkedList<>(externalBundleNames);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            ExternalBundle ext = externalBundles.get(current);
            if (ext == null) continue;

            // Follow re-export chains
            for (String reExported : ext.getReExportedBundles()) {
                if (result.add(reExported)) {
                    queue.add(reExported);
                }
            }

            // Include OSGi fragments for this bundle.
            // In OSGi, fragments attach to a host and provide actual classes
            // (e.g., org.eclipse.swt.cocoa.macosx.aarch64 is a fragment of org.eclipse.swt
            //  and contains all the actual SWT classes).
            Set<String> fragments = fragmentsByHost.get(current);
            if (fragments != null) {
                for (String fragment : fragments) {
                    result.add(fragment);
                    // Don't queue fragments for further expansion - they don't re-export
                }
            }
        }

        return result;
    }

    /**
     * Print the dependency graph for debugging.
     */
    public void printGraph() {
        log.info("Dependency Graph:");
        log.info("  Local bundles: {}", bundlesByName.size());
        log.info("  External bundles: {}", externalBundles.size());
        log.info("  Unresolved dependencies: {}", unresolvedDependencies.size());

        for (Bundle bundle : bundlesByName.values()) {
            Set<String> deps = dependencies.get(bundle.getSymbolicName());
            log.info("  {} -> {}", bundle.getSymbolicName(),
                deps != null ? deps : "[]");
        }
    }

    /**
     * Represents an external bundle (from P2 repository).
     */
    public static class ExternalBundle {
        private final String symbolicName;
        private final String version;
        private final java.nio.file.Path jarPath;
        private final List<String> exportedPackages = new ArrayList<>();
        private final Set<String> reExportedBundles = new LinkedHashSet<>();
        private String fragmentHost; // If this bundle is an OSGi fragment

        public ExternalBundle(String symbolicName, String version, java.nio.file.Path jarPath) {
            this.symbolicName = symbolicName;
            this.version = version;
            this.jarPath = jarPath;
        }

        public String getSymbolicName() {
            return symbolicName;
        }

        public String getVersion() {
            return version;
        }

        public java.nio.file.Path getJarPath() {
            return jarPath;
        }

        public List<String> getExportedPackages() {
            return exportedPackages;
        }

        public void addExportedPackage(String pkg) {
            exportedPackages.add(pkg);
        }

        public Set<String> getReExportedBundles() {
            return reExportedBundles;
        }

        public void addReExportedBundle(String bundleName) {
            reExportedBundles.add(bundleName);
        }

        public String getFragmentHost() {
            return fragmentHost;
        }

        public void setFragmentHost(String fragmentHost) {
            this.fragmentHost = fragmentHost;
        }

        @Override
        public String toString() {
            return symbolicName + "_" + version;
        }
    }

    /**
     * Represents an unresolved dependency.
     */
    public static class UnresolvedDependency {
        private final String bundleName;
        private final String dependencyName;
        private final String versionRange;
        private final boolean optional;

        public UnresolvedDependency(String bundleName, String dependencyName,
                                   String versionRange, boolean optional) {
            this.bundleName = bundleName;
            this.dependencyName = dependencyName;
            this.versionRange = versionRange;
            this.optional = optional;
        }

        public String getBundleName() {
            return bundleName;
        }

        public String getDependencyName() {
            return dependencyName;
        }

        public String getVersionRange() {
            return versionRange;
        }

        public boolean isOptional() {
            return optional;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnresolvedDependency that = (UnresolvedDependency) o;
            return bundleName.equals(that.bundleName) &&
                   dependencyName.equals(that.dependencyName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bundleName, dependencyName);
        }

        @Override
        public String toString() {
            return bundleName + " -> " + dependencyName +
                   (versionRange != null ? " " + versionRange : "") +
                   (optional ? " (optional)" : "");
        }
    }
}
