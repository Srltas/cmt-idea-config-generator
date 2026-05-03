package com.cubrid.tools.ideaconfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.cubrid.tools.ideaconfig.config.ConfigLoader;
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
import com.cubrid.tools.ideaconfig.resolver.BundleResolver;
import com.cubrid.tools.ideaconfig.resolver.FeatureResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

            loadConfiguration();
            initializePaths();
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

    private void loadConfiguration() throws Exception {
        log.info("Loading configuration...");
        log.info("  Config file: {}", params.getConfigFile());
        log.info("  Projects folder: {}", params.getProjectsFolder());
        log.info("  Output directory: {}", params.getOutputDir());

        config = new ConfigLoader().load(params.getConfigFile());
        config.setProjectsFolder(params.getProjectsFolder());
        config.setOutputDir(params.getOutputDir());
        config.setEclipseDepsDir(params.getEclipseDepsDir());
        config.validate();

        log.info("  Workspace name: {}", config.getWorkspaceName());
        log.info("  Bundle paths: {}", config.getBundlesPaths().size());
        log.info("  Feature paths: {}", config.getFeaturesPaths().size());
        log.info("  Products: {}", config.getProductsPaths().size());
        log.info("  Test modules: {}", config.getTestModuleRoots().size());
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

        Path runtimeDir = pathsManager.getWorkspaceDir().resolve("runtime");
        runConfigProducer.generateDevProperties(orderedBundles, runtimeDir);
        if (!products.isEmpty()) {
            runConfigProducer.generateConfigIni(products.get(0), orderedBundles, runtimeDir);
        }

        log.info("Configuration generation completed");
    }
}
