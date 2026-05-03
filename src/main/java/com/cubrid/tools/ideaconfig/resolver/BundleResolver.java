package com.cubrid.tools.ideaconfig.resolver;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.Bundle.BundleRequirement;
import com.cubrid.tools.ideaconfig.model.Bundle.PackageImport;
import com.cubrid.tools.ideaconfig.model.DependencyGraph;
import com.cubrid.tools.ideaconfig.model.DependencyGraph.ExternalBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * Resolves bundle dependencies and builds a dependency graph.
 */
public class BundleResolver {

    private static final Logger log = LoggerFactory.getLogger(BundleResolver.class);

    // Eclipse platform bundles that are typically external
    private static final Set<String> ECLIPSE_PLATFORM_PREFIXES = Set.of(
        "org.eclipse.",
        "org.apache.",
        "javax.",
        "jakarta.",
        "com.sun.",
        "com.ibm."
    );

    private final DependencyGraph graph;
    private final Map<String, Bundle> bundlesByName;
    private final Map<String, Set<String>> exportedPackages; // package -> bundles that export it

    public BundleResolver() {
        this.graph = new DependencyGraph();
        this.bundlesByName = new HashMap<>();
        this.exportedPackages = new HashMap<>();
    }

    /**
     * Resolve dependencies for a list of bundles.
     *
     * @param bundles the bundles to resolve
     * @return the dependency graph
     */
    public DependencyGraph resolve(List<Bundle> bundles) {
        log.info("Resolving dependencies for {} bundles", bundles.size());

        // Phase 1: Index bundles
        indexBundles(bundles);

        // Phase 2: Resolve Require-Bundle
        resolveRequireBundles(bundles);

        // Phase 3: Resolve Import-Package
        resolveImportPackages(bundles);

        // Phase 4: Report results
        reportResults();

        return graph;
    }

    /**
     * Index bundles and their exported packages.
     */
    private void indexBundles(List<Bundle> bundles) {
        log.debug("Indexing {} bundles", bundles.size());

        for (Bundle bundle : bundles) {
            String name = bundle.getSymbolicName();
            bundlesByName.put(name, bundle);
            graph.addBundle(bundle);

            // Index exported packages
            for (String pkg : bundle.getExportedPackages()) {
                exportedPackages.computeIfAbsent(pkg, k -> new HashSet<>()).add(name);
            }

            log.debug("  Indexed bundle: {} (exports {} packages)",
                name, bundle.getExportedPackages().size());
        }
    }

    /**
     * Resolve Require-Bundle dependencies.
     */
    private void resolveRequireBundles(List<Bundle> bundles) {
        log.debug("Resolving Require-Bundle dependencies");

        for (Bundle bundle : bundles) {
            String bundleName = bundle.getSymbolicName();

            for (BundleRequirement req : bundle.getRequiredBundles()) {
                String depName = req.getBundleName();

                // "system.bundle" alias has no JAR; resolve as the OSGi framework bundle.
                if ("system.bundle".equals(depName)) {
                    log.debug("  Skipping system bundle alias: {} -> {}", bundleName, depName);
                    continue;
                }

                if (bundlesByName.containsKey(depName)) {
                    // Local bundle dependency
                    graph.addDependency(bundleName, depName, req.isOptional());
                    log.debug("  {} -> {} (local)", bundleName, depName);
                } else if (isEclipsePlatformBundle(depName)) {
                    // Eclipse platform bundle - mark as external
                    addExternalDependency(bundleName, depName, req.getVersionRange(), req.isOptional());
                } else {
                    // Unknown bundle
                    graph.addUnresolvedDependency(bundleName, depName,
                        req.getVersionRange(), req.isOptional());

                    if (!req.isOptional()) {
                        log.warn("  Unresolved required bundle: {} -> {}", bundleName, depName);
                    } else {
                        log.debug("  Unresolved optional bundle: {} -> {}", bundleName, depName);
                    }
                }
            }
        }
    }

    /**
     * Resolve Import-Package dependencies.
     */
    private void resolveImportPackages(List<Bundle> bundles) {
        log.debug("Resolving Import-Package dependencies");

        for (Bundle bundle : bundles) {
            String bundleName = bundle.getSymbolicName();

            for (PackageImport imp : bundle.getImportedPackages()) {
                String pkg = imp.getPackageName();

                // Skip java.* packages (provided by JDK)
                if (pkg.startsWith("java.") || pkg.startsWith("javax.")) {
                    continue;
                }

                Set<String> providers = exportedPackages.get(pkg);
                if (providers != null && !providers.isEmpty()) {
                    // Package is provided by local bundle(s)
                    for (String provider : providers) {
                        if (!provider.equals(bundleName)) {
                            graph.addDependency(bundleName, provider, imp.isOptional());
                            log.debug("  {} -> {} (via package {})", bundleName, provider, pkg);
                        }
                    }
                } else if (isEclipsePlatformPackage(pkg)) {
                    // Eclipse platform package - typically from external bundle
                    log.debug("  Package {} from Eclipse platform", pkg);
                } else if (!imp.isOptional()) {
                    log.debug("  Unresolved required package: {} imports {}", bundleName, pkg);
                }
            }
        }
    }

    /**
     * Add an external bundle dependency.
     */
    private void addExternalDependency(String bundleName, String depName,
                                       String versionRange, boolean optional) {
        if (!graph.isExternalBundle(depName)) {
            // Create placeholder for external bundle
            ExternalBundle external = new ExternalBundle(depName, null, null);
            graph.addExternalBundle(external);
            log.debug("  Added external bundle: {}", depName);
        }

        graph.addDependency(bundleName, depName, optional);
    }

    /**
     * Check if a bundle name appears to be an Eclipse platform bundle.
     */
    private boolean isEclipsePlatformBundle(String bundleName) {
        for (String prefix : ECLIPSE_PLATFORM_PREFIXES) {
            if (bundleName.startsWith(prefix)) {
                return true;
            }
        }
        // CUBRID external bundles
        if (bundleName.startsWith("com.cubrid.bundle.")) {
            return true;
        }
        return false;
    }

    /**
     * Check if a package appears to be from Eclipse platform.
     */
    private boolean isEclipsePlatformPackage(String pkg) {
        return pkg.startsWith("org.eclipse.") ||
               pkg.startsWith("org.osgi.") ||
               pkg.startsWith("org.apache.") ||
               pkg.startsWith("com.ibm.");
    }

    /**
     * Report resolution results.
     */
    private void reportResults() {
        int localCount = graph.getAllBundles().size();
        int externalCount = graph.getExternalBundles().size();
        int unresolvedCount = graph.getUnresolvedDependencies().size();
        int requiredUnresolvedCount = graph.getRequiredUnresolvedDependencies().size();

        log.info("Dependency resolution complete:");
        log.info("  Local bundles: {}", localCount);
        log.info("  External bundles: {}", externalCount);
        log.info("  Unresolved dependencies: {} ({} required)",
            unresolvedCount, requiredUnresolvedCount);

        // Check for cycles
        List<List<String>> cycles = graph.detectCycles();
        if (!cycles.isEmpty()) {
            log.warn("  Circular dependencies detected: {}", cycles.size());
            for (List<String> cycle : cycles) {
                log.warn("    Cycle: {}", String.join(" -> ", cycle));
            }
        }

        // Report required unresolved dependencies
        if (requiredUnresolvedCount > 0) {
            log.warn("Required but unresolved dependencies:");
            for (DependencyGraph.UnresolvedDependency dep : graph.getRequiredUnresolvedDependencies()) {
                log.warn("  {}", dep);
            }
        }
    }

    // Eclipse IDE tool bundles that are NOT needed by CMT runtime.
    // These cause ServiceLoader conflicts (e.g., M2ELogbackConfigurator)
    // when present on the classpath outside of a full Eclipse IDE environment.
    private static final Set<String> EXCLUDED_BUNDLE_PREFIXES = Set.of(
        "org.eclipse.m2e."
    );

    /**
     * Register external bundles from a directory.
     * This is used to add pre-downloaded Eclipse dependencies.
     *
     * @param dependencyDir the directory containing external bundle JARs
     */
    public void registerExternalBundles(Path dependencyDir) {
        if (dependencyDir == null || !java.nio.file.Files.exists(dependencyDir)) {
            log.debug("External dependency directory not available");
            return;
        }

        log.info("Scanning external bundles from: {}", dependencyDir);

        int registered = 0;
        int excluded = 0;
        try (var stream = java.nio.file.Files.walk(dependencyDir, 2)) {
            var jars = stream.filter(p -> p.toString().endsWith(".jar")).toList();
            for (Path jar : jars) {
                if (registerExternalJar(jar)) {
                    registered++;
                } else {
                    excluded++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan external bundles: {}", e.getMessage());
        }

        log.info("Registered {} external bundles ({} excluded)", registered, excluded);

        // After all JARs are registered, parse their manifests for re-export info
        parseExternalBundleReExports();
    }

    /**
     * Register a single external JAR as an external bundle.
     *
     * @return true if registered, false if excluded
     */
    private boolean registerExternalJar(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        // Extract bundle name from JAR filename (e.g., org.eclipse.core.runtime_3.25.0.jar)
        String nameVersion = fileName.replace(".jar", "");
        int underscoreIdx = nameVersion.lastIndexOf('_');

        String bundleName;
        String version;
        if (underscoreIdx > 0) {
            bundleName = nameVersion.substring(0, underscoreIdx);
            version = nameVersion.substring(underscoreIdx + 1);
        } else {
            bundleName = nameVersion;
            version = null;
        }

        // Filter out excluded bundles
        for (String prefix : EXCLUDED_BUNDLE_PREFIXES) {
            if (bundleName.startsWith(prefix)) {
                log.debug("  Excluded bundle: {}", bundleName);
                return false;
            }
        }

        ExternalBundle external = new ExternalBundle(bundleName, version, jarPath);
        graph.addExternalBundle(external);
        log.debug("  Registered external bundle: {} ({})", bundleName, version);
        return true;
    }

    /**
     * Parse MANIFEST.MF from registered external JAR bundles to discover
     * Require-Bundle entries with visibility:=reexport.
     * This enables accurate transitive dependency resolution in IntelliJ IDEA,
     * which doesn't have OSGi's class loading mechanism.
     */
    private void parseExternalBundleReExports() {
        log.info("Parsing external bundle manifests (re-exports and fragments)...");
        int reExportCount = 0;
        int fragmentCount = 0;

        for (ExternalBundle ext : graph.getExternalBundles()) {
            if (ext.getJarPath() == null) continue;

            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(ext.getJarPath().toFile())) {
                java.util.jar.Manifest manifest = jar.getManifest();
                if (manifest == null) continue;

                var attrs = manifest.getMainAttributes();

                // Parse Require-Bundle for visibility:=reexport
                String requireBundle = attrs.getValue("Require-Bundle");
                if (requireBundle != null) {
                    List<String> reExports = parseReExports(requireBundle);
                    for (String reExported : reExports) {
                        ext.addReExportedBundle(reExported);
                        reExportCount++;
                    }
                    if (!reExports.isEmpty()) {
                        log.debug("  {} re-exports: {}", ext.getSymbolicName(), reExports);
                    }
                }

                // Parse Fragment-Host: in OSGi, fragments attach to a host bundle
                // and provide actual classes (e.g., org.eclipse.swt.cocoa.macosx.aarch64
                // is a fragment of org.eclipse.swt and contains all SWT classes).
                String fragmentHost = attrs.getValue("Fragment-Host");
                if (fragmentHost != null) {
                    // Fragment-Host format: hostBundle;bundle-version="[x,y)"
                    int semiIdx = fragmentHost.indexOf(';');
                    String hostName = semiIdx > 0 ? fragmentHost.substring(0, semiIdx).trim() : fragmentHost.trim();
                    ext.setFragmentHost(hostName);
                    // Re-register to update fragmentsByHost in graph
                    graph.addExternalBundle(ext);
                    fragmentCount++;
                    log.debug("  {} is fragment of {}", ext.getSymbolicName(), hostName);
                }
            } catch (Exception e) {
                log.debug("  Could not read manifest for {}: {}", ext.getSymbolicName(), e.getMessage());
            }
        }

        log.info("Found {} re-export relationships and {} fragments among external bundles",
            reExportCount, fragmentCount);
    }

    /**
     * Parse a Require-Bundle header value and extract bundle names with visibility:=reexport.
     *
     * @param requireBundle the raw Require-Bundle header value
     * @return list of bundle names that are re-exported
     */
    private List<String> parseReExports(String requireBundle) {
        List<String> reExports = new ArrayList<>();

        // Split by comma but respect quoted strings
        // Require-Bundle entries are: bundleName;directive1:=value1;directive2:=value2
        String[] entries = requireBundle.split(",(?=\\s*[a-zA-Z])");

        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            // Check if this entry has visibility:=reexport
            if (!entry.contains("visibility:=reexport")) continue;

            // Extract bundle name (before first semicolon)
            int semiIdx = entry.indexOf(';');
            String bundleName = semiIdx > 0 ? entry.substring(0, semiIdx).trim() : entry.trim();

            if (!bundleName.isEmpty()) {
                reExports.add(bundleName);
            }
        }

        return reExports;
    }

}
