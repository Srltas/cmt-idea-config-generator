package com.cubrid.tools.ideaconfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.cubrid.tools.ideaconfig.config.PathsManager;
import com.cubrid.tools.ideaconfig.config.ProjectConfig;
import com.cubrid.tools.ideaconfig.eclipse.BuildPropertiesParser;
import com.cubrid.tools.ideaconfig.eclipse.FeatureParser;
import com.cubrid.tools.ideaconfig.eclipse.ManifestParser;
import com.cubrid.tools.ideaconfig.eclipse.PomDependencyParser;
import com.cubrid.tools.ideaconfig.eclipse.ProductParser;
import com.cubrid.tools.ideaconfig.eclipse.TestModuleParser;
import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.DependencyGraph;
import com.cubrid.tools.ideaconfig.model.Feature;
import com.cubrid.tools.ideaconfig.model.Product;
import com.cubrid.tools.ideaconfig.model.TestModule;
import com.cubrid.tools.ideaconfig.producer.IMLProducer;
import com.cubrid.tools.ideaconfig.producer.LibraryProducer;
import com.cubrid.tools.ideaconfig.producer.ModulesXmlProducer;
import com.cubrid.tools.ideaconfig.producer.RunConfigProducer;
import com.cubrid.tools.ideaconfig.provision.P2Provisioner;
import com.cubrid.tools.ideaconfig.resolver.BundleResolver;
import com.cubrid.tools.ideaconfig.resolver.FeatureResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main entry point for CMT IDEA Config Generator.
 */
public class EntryPoint {

    private static final Logger log = LoggerFactory.getLogger(EntryPoint.class);

    private final Params params;
    private ProjectConfig config;
    private PathsManager pathsManager;

    private final ManifestParser manifestParser = new ManifestParser();
    private final FeatureParser featureParser = new FeatureParser();
    private final ProductParser productParser = new ProductParser();
    private final BuildPropertiesParser buildPropertiesParser = new BuildPropertiesParser();
    private final PomDependencyParser pomDependencyParser = new PomDependencyParser();
    private final TestModuleParser testModuleParser = new TestModuleParser();

    private final List<Bundle> bundles = new ArrayList<>();
    private final List<Feature> features = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();
    private final List<TestModule> testModules = new ArrayList<>();

    private DependencyGraph dependencyGraph;

    public EntryPoint(Params params) {
        this.params = params;
    }

    public static void main(String[] args) {
        Params params = Params.parse(args);
        if (params == null) {
            System.exit(1);
        }
        System.exit(new EntryPoint(params).execute());
    }

    public int execute() {
        try {
            configureLogging();

            log.info("=".repeat(60));
            log.info("CMT IDEA Config Generator");
            log.info("=".repeat(60));

            discoverProjectLayout();
            initializePaths();
            provisionEclipseDependencies();
            discoverArtifacts();
            resolveDependencies();
            discoverTestModules();

            if (!params.isDryRun()) {
                generateConfiguration();
            } else {
                log.info("Dry run mode - skipping file generation");
            }

            log.info("=".repeat(60));
            log.info("Generation completed successfully");
            log.info("=".repeat(60));
            return 0;
        } catch (ProjectConfig.ConfigurationException ex) {
            log.error("Configuration error: {}", ex.getMessage());
            return 1;
        } catch (Exception ex) {
            log.error("Error during generation", ex);
            return 1;
        }
    }

    private void configureLogging() {
        if (params.isDebug()) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger rootLogger = context.getLogger("com.cubrid.tools.ideaconfig");
            rootLogger.setLevel(Level.DEBUG);
            log.debug("Debug logging enabled");
        }
    }

    private void discoverProjectLayout() throws Exception {
        log.info("Discovering project layout...");
        log.info("  Projects folder: {}", params.getProjectsFolder());
        log.info("  Output directory: {}", params.getOutputDir());

        config = ProjectConfig.discover(params.getProjectsFolder());
        config.validate();
    }

    private void initializePaths() throws Exception {
        log.info("Initializing paths...");
        pathsManager = new PathsManager(
                params.getProjectsFolder(),
                params.getOutputDir(),
                params.getEclipseDepsDir(),
                config
        );
        pathsManager.initialize();
        if (!params.isDryRun()) {
            pathsManager.createOutputDirectories();
        }
    }

    /** Fill the Eclipse dependency folder from Tycho's p2 cache. */
    private void provisionEclipseDependencies() throws Exception {
        Path depsDir = params.getEclipseDepsDir();

        P2Provisioner provisioner = new P2Provisioner(params.getMavenRepo(), depsDir);
        if (!provisioner.isCacheAvailable()) {
            throw new ProjectConfig.ConfigurationException(buildMissingCacheMessage(provisioner));
        }

        log.info("Provisioning Eclipse dependencies into {}", depsDir);
        if (params.isDryRun()) {
            log.info("Dry run mode - skipping provisioning");
            return;
        }

        provisioner.provision();

        if (!provisioner.hasOsgiFramework()) {
            throw new ProjectConfig.ConfigurationException(
                    "No OSGi framework (org.eclipse.osgi) in " + depsDir
                            + System.lineSeparator()
                            + "  The p2 cache at " + provisioner.getP2BundleDir() + " looks incomplete."
                            + System.lineSeparator()
                            + "  Build the project once so Tycho downloads the full target platform:"
                            + System.lineSeparator()
                            + "    mvn -f " + params.getProjectsFolder() + " package -DskipTests");
        }
    }

    /**
     * Resources a bundle keeps at its root are on its bundle class path in OSGi mode, but
     * the Console run configuration is a plain Java launch that reads them from its working
     * directory instead. Put a copy there so both modes find them.
     */
    private void copyWorkingDirResources(List<Bundle> bundles, Path workingDir) throws IOException {
        for (Bundle bundle : bundles) {
            Path logback = bundle.getLocation().resolve("logback.xml");
            if (Files.isRegularFile(logback)) {
                Files.copy(logback, workingDir.resolve("logback.xml"),
                        StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied logback.xml from {} into the working directory",
                        bundle.getSymbolicName());
                return;
            }
        }
    }

    private String buildMissingCacheMessage(P2Provisioner provisioner) {
        return "The target platform is not in the local Maven repository yet: " + provisioner.getP2BundleDir()
                + System.lineSeparator()
                + "  Build the project once so Tycho downloads it:"
                + System.lineSeparator()
                + "    mvn -f " + params.getProjectsFolder() + " package -DskipTests";
    }

    private void discoverArtifacts() throws Exception {
        log.info("Discovering and parsing Eclipse artifacts...");

        for (Path bundleDir : pathsManager.findBundleDirectories()) {
            try {
                Bundle bundle = manifestParser.parseBundle(bundleDir);
                buildPropertiesParser.parseForBundle(bundleDir, bundle);

                if (bundle.isStandaloneApp()) {
                    for (String dep : pomDependencyParser.parseDependencyArtifactIds(bundleDir)) {
                        bundle.addPomDependencyArtifactId(dep);
                    }
                    log.info("  Standalone app: {} (main: {}, {} pom deps)",
                        bundle.getSymbolicName(), bundle.getMainClass(),
                        bundle.getPomDependencyArtifactIds().size());
                }

                bundles.add(bundle);
                log.info("  Bundle: {} v{} ({} required, {} sources)",
                    bundle.getSymbolicName(), bundle.getVersion(),
                    bundle.getRequiredBundles().size(), bundle.getSourceFolders().size());
            } catch (Exception e) {
                log.warn("  Failed to parse bundle {}: {}", bundleDir.getFileName(), e.getMessage());
            }
        }

        for (Path featureDir : pathsManager.findFeatureDirectories()) {
            try {
                features.add(featureParser.parseFeature(featureDir));
            } catch (Exception e) {
                log.warn("  Failed to parse feature {}: {}", featureDir.getFileName(), e.getMessage());
            }
        }

        for (Path productPath : pathsManager.getProductsPaths()) {
            try {
                products.add(productParser.parse(productPath));
            } catch (Exception e) {
                log.warn("  Failed to parse product {}: {}", productPath.getFileName(), e.getMessage());
            }
        }

        log.info("Parsed: {} bundles, {} features, {} products",
            bundles.size(), features.size(), products.size());
    }

    private void resolveDependencies() {
        log.info("Resolving dependencies...");

        BundleResolver bundleResolver = new BundleResolver();
        FeatureResolver featureResolver = new FeatureResolver();

        if (pathsManager.getEclipseDepsDir() != null) {
            bundleResolver.registerExternalBundles(pathsManager.getEclipseDepsDir());
        }
        dependencyGraph = bundleResolver.resolve(bundles);

        featureResolver.indexFeatures(features);
        featureResolver.indexBundles(bundles);

        for (Product product : products) {
            log.info("Resolving product: {}", product.getName());
            if (params.isDebug()) {
                featureResolver.printFeatureHierarchy(product);
            }
            var requiredPlugins = featureResolver.resolveProductPlugins(product);
            var externalPlugins = featureResolver.resolveExternalPlugins(product);
            log.info("  Total plugins: {} ({} local, {} external)",
                requiredPlugins.size(),
                requiredPlugins.size() - externalPlugins.size(),
                externalPlugins.size());
        }

        if (params.isDebug()) {
            dependencyGraph.printGraph();
        }

        var unresolved = dependencyGraph.getRequiredUnresolvedDependencies();
        if (!unresolved.isEmpty()) {
            log.warn("Warning: {} required dependencies could not be resolved", unresolved.size());
        }
    }

    private void discoverTestModules() throws Exception {
        if (pathsManager.getTestModuleRoots().isEmpty()) {
            return;
        }
        log.info("Discovering test modules...");

        Set<String> localBundleNames = new HashSet<>();
        for (Bundle b : bundles) {
            localBundleNames.add(b.getSymbolicName());
        }

        for (Path moduleDir : pathsManager.getTestModuleRoots()) {
            try {
                TestModule module = testModuleParser.parse(moduleDir, localBundleNames);
                if (module != null) {
                    testModules.add(module);
                }
            } catch (Exception e) {
                log.warn("  Failed to parse test module {}: {}", moduleDir.getFileName(), e.getMessage());
            }
        }
    }

    private void generateConfiguration() throws Exception {
        log.info("Generating IntelliJ IDEA configuration...");
        log.info("  Output directory: {}", pathsManager.getWorkspaceDir());

        List<Bundle> orderedBundles = dependencyGraph.getTopologicalOrder();
        String jdkVersion = "21";

        IMLProducer imlProducer = new IMLProducer(
            pathsManager.getModulesDir(),
            dependencyGraph,
            jdkVersion
        );

        // Equinox launcher must be on the Desktop run-config module's classpath.
        // Use the same module the run config will target to keep them in sync.
        for (Product product : products) {
            String appModule = RunConfigProducer.findAppModuleName(orderedBundles, product);
            if (appModule != null) {
                imlProducer.addExtraExternalBundle(appModule, "org.eclipse.equinox.launcher");
            }
        }

        imlProducer.generateAll(orderedBundles);
        imlProducer.generateTestModules(testModules);

        new ModulesXmlProducer(
            pathsManager.getIdeaConfigDir(),
            pathsManager.getModulesDir()
        ).generate(orderedBundles, testModules);

        new LibraryProducer(
            pathsManager.getLibrariesDir(),
            pathsManager.getEclipseDepsDir()
        ).generateAll(dependencyGraph.getExternalBundles());

        RunConfigProducer runConfigProducer = new RunConfigProducer(
            pathsManager.getRunConfigurationsDir(),
            pathsManager.getEclipseDepsDir()
        );
        runConfigProducer.generateAll(products, orderedBundles);

        copyWorkingDirResources(orderedBundles, pathsManager.getWorkspaceDir());

        Path runtimeDir = pathsManager.getWorkspaceDir().resolve("runtime");
        runConfigProducer.generateDevProperties(orderedBundles, runtimeDir);
        if (!products.isEmpty()) {
            runConfigProducer.generateConfigIni(products.get(0), orderedBundles, runtimeDir);
        }

        log.info("Configuration generation completed");
    }
}
