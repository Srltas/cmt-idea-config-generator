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
import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.DependencyGraph;
import com.cubrid.tools.ideaconfig.model.Feature;
import com.cubrid.tools.ideaconfig.model.Product;
import com.cubrid.tools.ideaconfig.producer.IMLProducer;
import com.cubrid.tools.ideaconfig.producer.LibraryProducer;
import com.cubrid.tools.ideaconfig.producer.ModulesXmlProducer;
import com.cubrid.tools.ideaconfig.producer.RunConfigProducer;
import com.cubrid.tools.ideaconfig.resolver.BundleResolver;
import com.cubrid.tools.ideaconfig.resolver.FeatureResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for CMT IDEA Config Generator.
 * Generates IntelliJ IDEA configuration files for the CUBRID Migration Toolkit project.
 */
public class EntryPoint {

    private static final Logger log = LoggerFactory.getLogger(EntryPoint.class);

    private final Params params;
    private ProjectConfig config;
    private PathsManager pathsManager;

    // Parsers
    private final ManifestParser manifestParser = new ManifestParser();
    private final FeatureParser featureParser = new FeatureParser();
    private final ProductParser productParser = new ProductParser();
    private final BuildPropertiesParser buildPropertiesParser = new BuildPropertiesParser();
    private final PomDependencyParser pomDependencyParser = new PomDependencyParser();

    // Parsed data
    private final List<Bundle> bundles = new ArrayList<>();
    private final List<Feature> features = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();

    // Resolvers
    private BundleResolver bundleResolver;
    private FeatureResolver featureResolver;
    private DependencyGraph dependencyGraph;

    public EntryPoint(Params params) {
        this.params = params;
    }

    /**
     * Main entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    /**
     * Run the generator with the given arguments.
     *
     * @param args command line arguments
     * @return exit code (0 for success)
     */
    public static int run(String[] args) {
        Params params = new Params();
        CommandLine cmd = new CommandLine(params);

        try {
            CommandLine.ParseResult result = cmd.parseArgs(args);

            if (result.isUsageHelpRequested()) {
                cmd.usage(System.out);
                return 0;
            }

            if (result.isVersionHelpRequested()) {
                cmd.printVersionHelp(System.out);
                return 0;
            }

            EntryPoint entryPoint = new EntryPoint(params);
            return entryPoint.execute();

        } catch (CommandLine.ParameterException ex) {
            System.err.println("Error: " + ex.getMessage());
            cmd.usage(System.err);
            return 1;
        } catch (Exception ex) {
            log.error("Fatal error", ex);
            System.err.println("Fatal error: " + ex.getMessage());
            return 1;
        }
    }

    /**
     * Execute the generation process.
     *
     * @return exit code (0 for success)
     */
    public int execute() {
        try {
            // Configure logging
            configureLogging();

            log.info("=".repeat(60));
            log.info("CMT IDEA Config Generator");
            log.info("=".repeat(60));

            // Step 1: Load configuration
            loadConfiguration();

            // Step 2: Initialize paths
            initializePaths();

            // Step 3: Discover bundles
            discoverBundles();

            // Step 4: Resolve dependencies
            resolveDependencies();

            // Step 5: Generate configuration
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

    /**
     * Configure logging based on parameters.
     */
    private void configureLogging() {
        if (params.isDebug()) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger rootLogger = context.getLogger("com.cubrid.tools.ideaconfig");
            rootLogger.setLevel(Level.DEBUG);
            log.debug("Debug logging enabled");
        }
    }

    /**
     * Load and validate the configuration file.
     */
    private void loadConfiguration() throws Exception {
        log.info("Loading configuration...");
        log.info("  Config file: {}", params.getConfigFile());
        log.info("  Projects folder: {}", params.getProjectsFolder());
        log.info("  Output directory: {}", params.getOutputDir());

        // Load configuration
        ConfigLoader loader = new ConfigLoader();
        config = loader.load(params.getConfigFile());

        // Set resolved paths from CLI params
        config.setProjectsFolder(params.getProjectsFolder());
        config.setOutputDir(params.getOutputDir());
        config.setEclipseDepsDir(params.getEclipseDepsDir());

        // Validate configuration
        config.validate();

        log.info("Configuration loaded successfully");
        log.info("  Workspace name: {}", config.getWorkspaceName());
        log.info("  Bundle paths: {}", config.getBundlesPaths().size());
        log.info("  Feature paths: {}", config.getFeaturesPaths().size());
        log.info("  Products: {}", config.getProductsPaths().size());
    }

    /**
     * Initialize the paths manager.
     */
    private void initializePaths() throws Exception {
        log.info("Initializing paths...");

        pathsManager = new PathsManager(
                params.getProjectsFolder(),
                params.getOutputDir(),
                params.getEclipseDepsDir(),
                config
        );

        pathsManager.initialize();

        // Create output directories if not dry run
        if (!params.isDryRun()) {
            pathsManager.createOutputDirectories();
        }
    }

    /**
     * Discover and parse bundles, features, and products.
     */
    private void discoverBundles() throws Exception {
        log.info("Discovering and parsing Eclipse artifacts...");

        // Parse bundles
        List<Path> bundleDirs = pathsManager.findBundleDirectories();
        log.info("Found {} bundle directories", bundleDirs.size());

        for (Path bundleDir : bundleDirs) {
            try {
                Bundle bundle = manifestParser.parseBundle(bundleDir);
                buildPropertiesParser.parseForBundle(bundleDir, bundle);

                // For standalone apps, parse pom.xml to discover dependencies
                if (bundle.isStandaloneApp()) {
                    List<String> pomDeps = pomDependencyParser.parseDependencyArtifactIds(bundleDir);
                    for (String dep : pomDeps) {
                        bundle.addPomDependencyArtifactId(dep);
                    }
                    log.info("  Standalone app: {} (main: {}, {} pom dependencies)",
                        bundle.getSymbolicName(), bundle.getMainClass(), pomDeps.size());
                }

                bundles.add(bundle);
                log.info("  Bundle: {} v{} ({} required bundles, {} source folders)",
                    bundle.getSymbolicName(),
                    bundle.getVersion(),
                    bundle.getRequiredBundles().size(),
                    bundle.getSourceFolders().size());
            } catch (Exception e) {
                log.warn("  Failed to parse bundle {}: {}", bundleDir.getFileName(), e.getMessage());
            }
        }

        // Parse features
        List<Path> featureDirs = pathsManager.findFeatureDirectories();
        log.info("Found {} feature directories", featureDirs.size());

        for (Path featureDir : featureDirs) {
            try {
                Feature feature = featureParser.parseFeature(featureDir);
                features.add(feature);
                log.info("  Feature: {} ({} plugins, {} included features)",
                    feature.getId(),
                    feature.getPlugins().size(),
                    feature.getIncludedFeatures().size());
            } catch (Exception e) {
                log.warn("  Failed to parse feature {}: {}", featureDir.getFileName(), e.getMessage());
            }
        }

        // Parse products
        for (Path productPath : pathsManager.getProductsPaths()) {
            try {
                Product product = productParser.parse(productPath);
                products.add(product);
                log.info("  Product: {} ({} features, app={})",
                    product.getName(),
                    product.getFeatureIds().size(),
                    product.getApplication());
            } catch (Exception e) {
                log.warn("  Failed to parse product {}: {}", productPath.getFileName(), e.getMessage());
            }
        }

        log.info("Parsed: {} bundles, {} features, {} products",
            bundles.size(), features.size(), products.size());
    }

    /**
     * Resolve dependencies between bundles and features.
     */
    private void resolveDependencies() throws Exception {
        log.info("Resolving dependencies...");

        // Initialize resolvers
        bundleResolver = new BundleResolver();
        featureResolver = new FeatureResolver();

        // Register external bundles if available
        if (pathsManager.getEclipseDepsDir() != null) {
            bundleResolver.registerExternalBundles(pathsManager.getEclipseDepsDir());
        }

        // Resolve bundle dependencies
        dependencyGraph = bundleResolver.resolve(bundles);

        // Index features and bundles for feature resolution
        featureResolver.indexFeatures(features);
        featureResolver.indexBundles(bundles);

        // For each product, resolve its features and plugins
        for (Product product : products) {
            log.info("Resolving product: {}", product.getName());

            // Print feature hierarchy
            if (params.isDebug()) {
                featureResolver.printFeatureHierarchy(product);
            }

            // Get required plugins
            var requiredPlugins = featureResolver.resolveProductPlugins(product);
            var externalPlugins = featureResolver.resolveExternalPlugins(product);

            log.info("  Total plugins: {} ({} local, {} external)",
                requiredPlugins.size(),
                requiredPlugins.size() - externalPlugins.size(),
                externalPlugins.size());
        }

        // Print dependency graph summary
        if (params.isDebug()) {
            dependencyGraph.printGraph();
        }

        // Get bundles in dependency order
        List<Bundle> orderedBundles = dependencyGraph.getTopologicalOrder();
        log.info("Bundle dependency order ({} bundles):", orderedBundles.size());
        for (int i = 0; i < orderedBundles.size(); i++) {
            log.info("  {}. {}", i + 1, orderedBundles.get(i).getSymbolicName());
        }

        // Report unresolved dependencies
        var unresolved = dependencyGraph.getRequiredUnresolvedDependencies();
        if (!unresolved.isEmpty()) {
            log.warn("Warning: {} required dependencies could not be resolved", unresolved.size());
        }
    }

    /**
     * Generate IntelliJ IDEA configuration files.
     */
    private void generateConfiguration() throws Exception {
        log.info("Generating IntelliJ IDEA configuration...");
        log.info("  Output directory: {}", pathsManager.getWorkspaceDir());
        log.info("  IDEA config: {}", pathsManager.getIdeaConfigDir());
        log.info("  Modules: {}", pathsManager.getModulesDir());

        // Get bundles in dependency order
        List<Bundle> orderedBundles = dependencyGraph.getTopologicalOrder();

        // Determine JDK version from config or default
        String jdkVersion = "21";  // Default JDK version for generated modules

        // 1. Generate .iml files
        IMLProducer imlProducer = new IMLProducer(
            pathsManager.getProjectsFolder(),
            pathsManager.getModulesDir(),
            dependencyGraph,
            jdkVersion
        );

        // Ensure Equinox launcher is available for Desktop run configuration.
        // Add it only to the app module used by the Desktop run config (not all modules).
        for (Product product : products) {
            String appModuleName = findAppModuleName(orderedBundles, product);
            if (appModuleName != null) {
                imlProducer.addExtraExternalBundle(appModuleName, "org.eclipse.equinox.launcher");
            }
        }

        imlProducer.generateAll(orderedBundles);

        // 2. Generate modules.xml
        ModulesXmlProducer modulesXmlProducer = new ModulesXmlProducer(
            pathsManager.getIdeaConfigDir(),
            pathsManager.getModulesDir()
        );
        modulesXmlProducer.generate(orderedBundles);

        // 3. Generate library configurations
        LibraryProducer libraryProducer = new LibraryProducer(
            pathsManager.getLibrariesDir(),
            pathsManager.getEclipseDepsDir()
        );
        libraryProducer.generateAll(dependencyGraph.getExternalBundles());

        // 4. Generate run configurations and runtime files
        RunConfigProducer runConfigProducer = new RunConfigProducer(
            pathsManager.getRunConfigurationsDir(),
            pathsManager.getEclipseDepsDir(),
            config.getWorkspaceName()
        );
        runConfigProducer.generateAll(products, orderedBundles);

        // 5. Generate OSGi runtime files
        Path runtimeDir = pathsManager.getWorkspaceDir().resolve("runtime");
        runConfigProducer.generateDevProperties(orderedBundles, runtimeDir);

        if (!products.isEmpty()) {
            runConfigProducer.generateConfigIni(products.get(0), orderedBundles, runtimeDir);
        }

        log.info("Configuration generation completed");
    }

    /**
     * Find the app module name for a product's Desktop run configuration.
     * Mirrors the logic in RunConfigProducer.findAppModule().
     */
    private String findAppModuleName(List<Bundle> bundleList, Product product) {
        String application = product.getApplication();
        if (application != null && !application.isBlank()) {
            String appBundleGuess = application;
            if (application.endsWith(".application")) {
                appBundleGuess = application.substring(0, application.length() - ".application".length());
            }
            for (Bundle b : bundleList) {
                if (b.getSymbolicName().equals(appBundleGuess)) {
                    return appBundleGuess;
                }
            }
            for (Bundle b : bundleList) {
                if (b.getSymbolicName().contains(".app")) {
                    return b.getSymbolicName();
                }
            }
        }
        if (!bundleList.isEmpty()) {
            return bundleList.get(bundleList.size() - 1).getSymbolicName();
        }
        return null;
    }

    // Getters for testing

    public ProjectConfig getConfig() {
        return config;
    }

    public PathsManager getPathsManager() {
        return pathsManager;
    }

    public DependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    public BundleResolver getBundleResolver() {
        return bundleResolver;
    }

    public FeatureResolver getFeatureResolver() {
        return featureResolver;
    }

    public List<Bundle> getBundles() {
        return bundles;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public List<Product> getProducts() {
        return products;
    }
}
