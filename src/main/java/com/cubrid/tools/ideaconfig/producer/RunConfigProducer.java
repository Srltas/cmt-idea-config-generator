package com.cubrid.tools.ideaconfig.producer;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.Product;
import com.cubrid.tools.ideaconfig.util.PathHelper;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Produces IntelliJ IDEA run configuration files for Eclipse RCP applications.
 */
public class RunConfigProducer {

    private static final Logger log = LoggerFactory.getLogger(RunConfigProducer.class);

    // Eclipse Equinox launcher main class
    private static final String EQUINOX_LAUNCHER_CLASS = "org.eclipse.equinox.launcher.Main";

    private final Path runConfigDir;
    private final Path eclipseDepsDir;

    public RunConfigProducer(Path runConfigDir, Path eclipseDepsDir) {
        this.runConfigDir = runConfigDir;
        this.eclipseDepsDir = eclipseDepsDir;
    }

    /**
     * Generate run configurations for all products and standalone apps.
     *
     * @param products the products
     * @param bundles the local bundles
     * @throws IOException if file writing fails
     */
    public void generateAll(List<Product> products, List<Bundle> bundles) throws IOException {
        Files.createDirectories(runConfigDir);

        int count = 0;

        // Generate Desktop (OSGi) run configurations
        for (Product product : products) {
            generateRunConfig(product, bundles);
            count++;
        }

        // Generate Console (standalone) run configurations
        for (Bundle bundle : bundles) {
            if (bundle.isStandaloneApp()) {
                generateConsoleRunConfig(bundle);
                count++;
            }
        }

        log.info("Generated {} run configurations in {}", count, runConfigDir);
    }

    /**
     * Generate a run configuration for a single product (Desktop version).
     *
     * @param product the product
     * @param bundles the local bundles
     * @throws IOException if file writing fails
     */
    public void generateRunConfig(Product product, List<Bundle> bundles) throws IOException {
        // Use fixed name for Desktop run configuration
        String configName = "CMT Desktop";
        String safeFileName = "CMT_Desktop";
        Path configFile = runConfigDir.resolve(safeFileName + ".xml");

        log.info("Generating Desktop run configuration: {}", configName);

        Document doc = XmlHelper.createDocument();

        // Root component element
        Element component = doc.createElement("component");
        component.setAttribute("name", "ProjectRunConfigurationManager");
        doc.appendChild(component);

        // Configuration element
        Element configuration = doc.createElement("configuration");
        configuration.setAttribute("default", "false");
        configuration.setAttribute("name", configName);
        configuration.setAttribute("type", "Application");
        configuration.setAttribute("factoryName", "Application");
        component.appendChild(configuration);

        // Main class option
        addOption(configuration, "MAIN_CLASS_NAME", EQUINOX_LAUNCHER_CLASS);

        // VM parameters
        String vmParams = buildVmParameters(product);
        addOption(configuration, "VM_PARAMETERS", vmParams);

        // Program parameters
        String programParams = buildProgramParameters(product);
        addOption(configuration, "PROGRAM_PARAMETERS", programParams);

        // Working directory
        addOption(configuration, "WORKING_DIRECTORY", "$PROJECT_DIR$");

        // Module (the app bundle, or the last bundle in topological order, which
        // has the most dependencies on its classpath).
        Element module = doc.createElement("module");
        String moduleName = findAppModuleName(bundles, product);
        if (moduleName == null) {
            moduleName = bundles.isEmpty() ? "" : bundles.get(bundles.size() - 1).getSymbolicName();
        }
        module.setAttribute("name", moduleName);
        configuration.appendChild(module);

        // Method element (before launch tasks)
        Element method = doc.createElement("method");
        method.setAttribute("v", "2");
        configuration.appendChild(method);

        // Make option
        Element makeOption = doc.createElement("option");
        makeOption.setAttribute("name", "Make");
        makeOption.setAttribute("enabled", "true");
        method.appendChild(makeOption);

        // Shorten command line option (Java 9+)
        Element shortenClasspath = doc.createElement("shortenClasspath");
        shortenClasspath.setAttribute("name", "ARGS_FILE");
        configuration.appendChild(shortenClasspath);

        // Write file
        XmlHelper.writeDocument(doc, configFile);
        log.debug("  Written: {}", configFile.getFileName());
    }

    /**
     * Generate a run configuration for a standalone (non-OSGi) console application.
     *
     * @param consoleBundle the bundle with Main-Class
     * @throws IOException if file writing fails
     */
    public void generateConsoleRunConfig(Bundle consoleBundle) throws IOException {
        String configName = "CMT Console";
        String safeFileName = "CMT_Console";
        Path configFile = runConfigDir.resolve(safeFileName + ".xml");

        log.info("Generating Console run configuration: {} (main: {})",
            configName, consoleBundle.getMainClass());

        Document doc = XmlHelper.createDocument();

        // Root component element
        Element component = doc.createElement("component");
        component.setAttribute("name", "ProjectRunConfigurationManager");
        doc.appendChild(component);

        // Configuration element
        Element configuration = doc.createElement("configuration");
        configuration.setAttribute("default", "false");
        configuration.setAttribute("name", configName);
        configuration.setAttribute("type", "Application");
        configuration.setAttribute("factoryName", "Application");
        component.appendChild(configuration);

        // Main class - from MANIFEST.MF Main-Class header
        addOption(configuration, "MAIN_CLASS_NAME", consoleBundle.getMainClass());

        // VM parameters - basic memory settings only (no OSGi)
        addOption(configuration, "VM_PARAMETERS", "-Xms512M -Xmx2048M");

        // Working directory
        addOption(configuration, "WORKING_DIRECTORY", "$PROJECT_DIR$");

        // Module
        Element module = doc.createElement("module");
        module.setAttribute("name", consoleBundle.getSymbolicName());
        configuration.appendChild(module);

        // Method element (before launch tasks)
        Element method = doc.createElement("method");
        method.setAttribute("v", "2");
        configuration.appendChild(method);

        // Make option
        Element makeOption = doc.createElement("option");
        makeOption.setAttribute("name", "Make");
        makeOption.setAttribute("enabled", "true");
        method.appendChild(makeOption);

        // Shorten command line option (Java 9+)
        Element shortenClasspath = doc.createElement("shortenClasspath");
        shortenClasspath.setAttribute("name", "ARGS_FILE");
        configuration.appendChild(shortenClasspath);

        // Write file
        XmlHelper.writeDocument(doc, configFile);
        log.debug("  Written: {}", configFile.getFileName());
    }

    /**
     * Build VM parameters for the product.
     * Includes platform-specific args for the current OS.
     */
    private String buildVmParameters(Product product) {
        StringBuilder sb = new StringBuilder();

        // Development mode - use file: protocol
        sb.append("-Dosgi.dev=file:$PROJECT_DIR$/runtime/dev.properties ");

        // Instance area
        sb.append("-Dosgi.instance.area=$PROJECT_DIR$/runtime/workspace ");

        // Install area - Eclipse expects plugins to be in {install.area}/plugins/
        // So we set install.area to the PARENT of the plugins folder
        if (eclipseDepsDir != null) {
            Path installArea = eclipseDepsDir.getParent();
            if (installArea != null) {
                sb.append("-Dosgi.install.area=").append(PathHelper.normalize(installArea.toAbsolutePath())).append(" ");
            } else {
                sb.append("-Dosgi.install.area=").append(PathHelper.normalize(eclipseDepsDir.toAbsolutePath())).append(" ");
            }
        }

        // Product-specific VM args (cross-platform + current platform)
        String currentOs = detectCurrentOs();
        String combinedVmArgs = product.getCombinedVmArgs(currentOs);
        if (combinedVmArgs != null && !combinedVmArgs.isBlank()) {
            sb.append(combinedVmArgs.trim()).append(" ");
        }

        return sb.toString().trim();
    }

    /**
     * Detect the current operating system for platform-specific arguments.
     */
    private static String detectCurrentOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return "macosx";
        if (os.contains("win")) return "windows";
        return "linux";
    }

    /**
     * Build program parameters for the product.
     */
    private String buildProgramParameters(Product product) {
        StringBuilder sb = new StringBuilder();

        // Console log
        sb.append("-consoleLog ");

        // Configuration area
        sb.append("-configuration $PROJECT_DIR$/runtime/configuration ");

        // Data location
        sb.append("-data @noDefault ");

        // Product-specific program args
        String productArgs = product.getProgramArgs();
        if (productArgs != null && !productArgs.isBlank()) {
            sb.append(productArgs.trim()).append(" ");
        }

        return sb.toString().trim();
    }

    /**
     * Find the bundle that should be the run configuration's module: prefer
     * the bundle whose symbolic name matches the product's {@code application}
     * (stripping the trailing ".application"), else any bundle whose symbolic
     * name contains ".app", else null.
     */
    public static String findAppModuleName(List<Bundle> bundles, Product product) {
        if (bundles.isEmpty()) {
            return null;
        }
        String application = product.getApplication();
        if (application != null && !application.isBlank()) {
            String appBundleGuess = application.endsWith(".application")
                ? application.substring(0, application.length() - ".application".length())
                : application;
            for (Bundle bundle : bundles) {
                if (bundle.getSymbolicName().equals(appBundleGuess)) {
                    return appBundleGuess;
                }
            }
            for (Bundle bundle : bundles) {
                if (bundle.getSymbolicName().contains(".app")) {
                    return bundle.getSymbolicName();
                }
            }
        }
        return bundles.get(bundles.size() - 1).getSymbolicName();
    }

    /**
     * Add an option element.
     */
    private void addOption(Element parent, String name, String value) {
        Element option = parent.getOwnerDocument().createElement("option");
        option.setAttribute("name", name);
        option.setAttribute("value", value);
        parent.appendChild(option);
    }

    /**
     * Find the org.eclipse.osgi framework JAR in the Eclipse dependencies folder.
     *
     * @return path to the OSGi framework JAR, or null if not found
     */
    private Path findOsgiFramework() {
        if (eclipseDepsDir == null || !Files.exists(eclipseDepsDir)) {
            log.warn("Eclipse dependencies directory not found: {}", eclipseDepsDir);
            return null;
        }

        try (var stream = Files.list(eclipseDepsDir)) {
            return stream
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("org.eclipse.osgi_") && name.endsWith(".jar");
                })
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            log.warn("Error searching for OSGi framework: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate dev.properties file for OSGi development mode.
     *
     * @param bundles the local bundles
     * @throws IOException if file writing fails
     */
    public void generateDevProperties(List<Bundle> bundles, Path runtimeDir) throws IOException {
        Path devPropertiesFile = runtimeDir.resolve("dev.properties");
        Files.createDirectories(runtimeDir);

        log.info("Generating dev.properties");

        StringBuilder sb = new StringBuilder();
        sb.append("# OSGi Development Properties\n");
        sb.append("# Generated by CMT IDEA Config Generator\n\n");

        // Default entry - tells OSGi where to find compiled classes
        sb.append("@ignoredot=true\n\n");

        // For each bundle, specify its output directory
        for (Bundle bundle : bundles) {
            String symbolicName = bundle.getSymbolicName();
            String outputFolder = bundle.getOutputFolder();
            if (outputFolder == null || outputFolder.isBlank()) {
                outputFolder = "bin";
            }

            // Remove trailing slash
            if (outputFolder.endsWith("/") || outputFolder.endsWith("\\")) {
                outputFolder = outputFolder.substring(0, outputFolder.length() - 1);
            }

            Path bundleDir = bundle.getLocation();
            Path outputDir = bundleDir.resolve(outputFolder);

            sb.append(symbolicName).append("=").append(PathHelper.normalize(outputDir.toAbsolutePath())).append("\n");
        }

        Files.writeString(devPropertiesFile, sb.toString());
        log.info("Written: {}", devPropertiesFile);
    }

    /**
     * Generate config.ini file for OSGi runtime.
     *
     * @param product the product
     * @param bundles the local bundles
     * @throws IOException if file writing fails
     */
    public void generateConfigIni(Product product, List<Bundle> bundles, Path runtimeDir) throws IOException {
        Path configDir = runtimeDir.resolve("configuration");
        Files.createDirectories(configDir);

        // Generate bundles.info for simpleconfigurator (includes both Eclipse deps and local bundles)
        generateBundlesInfo(configDir, bundles);

        Path configIniFile = configDir.resolve("config.ini");

        log.info("Generating config.ini with bundle start configurations");

        StringBuilder sb = new StringBuilder();
        sb.append("# Eclipse Configuration\n");
        sb.append("# Generated by CMT IDEA Config Generator\n\n");

        // OSGi framework - use proper file URI format
        Path osgiFramework = findOsgiFramework();
        if (osgiFramework != null) {
            sb.append("osgi.framework=").append(PathHelper.toEscapedFileUri(osgiFramework)).append("\n\n");
        }

        // Use simpleconfigurator for bundle management - proper file URI
        Path simpleConfigJar = findBundleJar("org.eclipse.equinox.simpleconfigurator");
        if (simpleConfigJar != null) {
            sb.append("osgi.bundles=reference\\:").append(PathHelper.toEscapedFileUri(simpleConfigJar)).append("@1:start\n");
        }
        // Use proper file URI for bundles.info
        Path bundlesInfoPath = configDir.resolve("org.eclipse.equinox.simpleconfigurator/bundles.info");
        sb.append("org.eclipse.equinox.simpleconfigurator.configUrl=").append(PathHelper.toEscapedFileUri(bundlesInfoPath)).append("\n\n");

        // Application
        String application = product.getApplication();
        if (application != null && !application.isBlank()) {
            sb.append("eclipse.application=").append(application).append("\n");
        }

        // Product - use 'id' attribute (e.g., com.cubrid.cubridmigration.app.product)
        // not 'uid' which is just an internal identifier
        String productId = product.getId();
        if (productId != null && !productId.isBlank()) {
            sb.append("eclipse.product=").append(productId).append("\n");
        }

        // Additional settings
        sb.append("\n# OSGi Console\n");
        sb.append("osgi.console=\n");
        sb.append("osgi.console.enable.builtin=true\n");

        // Note: osgi.dev is set via VM parameters to use absolute path

        // Clean start (useful for development)
        sb.append("\n# Runtime settings\n");
        sb.append("osgi.clean=true\n");
        sb.append("eclipse.consoleLog=true\n");

        Files.writeString(configIniFile, sb.toString());
        log.info("Written: {}", configIniFile);
    }

    /**
     * Generate bundles.info file for simpleconfigurator.
     * Lists all bundles from the Eclipse dependencies folder and local bundles.
     */
    private void generateBundlesInfo(Path configDir, List<Bundle> localBundles) throws IOException {
        Path simpleConfigDir = configDir.resolve("org.eclipse.equinox.simpleconfigurator");
        Files.createDirectories(simpleConfigDir);
        Path bundlesInfoFile = simpleConfigDir.resolve("bundles.info");

        log.info("Generating bundles.info for simpleconfigurator");

        StringBuilder sb = new StringBuilder();
        sb.append("#encoding=UTF-8\n");
        sb.append("#version=1\n");

        // Add local development bundles first
        for (Bundle bundle : localBundles) {
            String symbolicName = bundle.getSymbolicName();
            String version = bundle.getVersion();
            if (version == null || version.isBlank()) {
                version = "1.0.0";
            }
            Path bundleLocation = bundle.getLocation();

            // For development, point to the bundle directory using file: URI
            // Directory URIs end with / to indicate directory
            String location = PathHelper.toFileUri(bundleLocation);

            // Application bundles need to start
            int startLevel = 4;
            boolean autoStart = symbolicName.contains(".app") || symbolicName.equals("com.cubrid.cubridmigration.ui");

            sb.append(symbolicName).append(",")
              .append(version).append(",")
              .append(location).append(",")
              .append(startLevel).append(",")
              .append(autoStart).append("\n");
        }

        // Add Eclipse dependencies
        if (eclipseDepsDir == null || !Files.exists(eclipseDepsDir)) {
            log.warn("Eclipse dependencies directory not found");
            Files.writeString(bundlesInfoFile, sb.toString());
            log.info("Written: {} with {} local bundle entries", bundlesInfoFile, localBundles.size());
            return;
        }

        try (var stream = Files.list(eclipseDepsDir)) {
            stream.filter(p -> !p.getFileName().toString().contains(".source"))  // Skip source bundles
                  .filter(p -> !p.getFileName().toString().startsWith("org.eclipse.m2e."))  // Skip m2e (IDE-only)
                  .filter(p -> p.toString().endsWith(".jar") || Files.isDirectory(p))  // JARs and directories
                  .sorted()
                  .forEach(bundlePath -> {
                      String fileName = bundlePath.getFileName().toString();
                      String nameWithoutExt;

                      // Handle both JAR files and directory bundles
                      boolean isDirectory = Files.isDirectory(bundlePath);
                      if (isDirectory) {
                          nameWithoutExt = fileName;
                      } else if (fileName.endsWith(".jar")) {
                          nameWithoutExt = fileName.substring(0, fileName.length() - 4);
                      } else {
                          return;  // Skip non-bundle entries
                      }

                      // Parse symbolic name and version from filename
                      // Format: symbolic.name_version
                      int lastUnderscore = nameWithoutExt.lastIndexOf('_');
                      if (lastUnderscore > 0) {
                          String symbolicName = nameWithoutExt.substring(0, lastUnderscore);
                          String version = nameWithoutExt.substring(lastUnderscore + 1);

                          // Determine start level and autostart
                          int startLevel = 4;  // Default start level
                          boolean autoStart = false;

                          // Core bundles that need to start early
                          if (symbolicName.equals("org.eclipse.osgi")) {
                              startLevel = -1;  // Framework bundle
                              autoStart = true;
                          } else if (symbolicName.equals("org.eclipse.equinox.simpleconfigurator")) {
                              startLevel = 1;
                              autoStart = true;
                          } else if (symbolicName.equals("org.eclipse.equinox.common")) {
                              startLevel = 2;
                              autoStart = true;
                          } else if (symbolicName.equals("org.eclipse.equinox.ds") ||
                                     symbolicName.equals("org.apache.felix.scr")) {
                              startLevel = 2;
                              autoStart = true;
                          } else if (symbolicName.equals("org.eclipse.core.runtime")) {
                              startLevel = 4;
                              autoStart = true;
                          } else if (symbolicName.equals("org.eclipse.equinox.event")) {
                              startLevel = 2;
                              autoStart = true;
                          } else if (symbolicName.startsWith("ch.qos.logback")) {
                              startLevel = 2;
                              autoStart = symbolicName.equals("ch.qos.logback.classic");
                          }

                          // Build the entry with file: URI for cross-platform compatibility
                          String location = PathHelper.toFileUri(bundlePath);
                          sb.append(symbolicName).append(",")
                            .append(version).append(",")
                            .append(location).append(",")
                            .append(startLevel).append(",")
                            .append(autoStart).append("\n");
                      }
                  });
        }

        Files.writeString(bundlesInfoFile, sb.toString());
        log.info("Written: {} with bundle entries", bundlesInfoFile);
    }

    /**
     * Find a bundle JAR by symbolic name.
     */
    private Path findBundleJar(String symbolicName) {
        if (eclipseDepsDir == null || !Files.exists(eclipseDepsDir)) {
            return null;
        }

        try (var stream = Files.list(eclipseDepsDir)) {
            return stream
                .filter(p -> p.getFileName().toString().startsWith(symbolicName + "_"))
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            log.warn("Error searching for bundle {}: {}", symbolicName, e.getMessage());
            return null;
        }
    }
}
