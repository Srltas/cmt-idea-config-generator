package com.cubrid.tools.ideaconfig.producer;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.DependencyGraph;
import com.cubrid.tools.ideaconfig.util.VersionHelper;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces IntelliJ IDEA .iml module files for Eclipse bundles.
 */
public class IMLProducer {

    private static final Logger log = LoggerFactory.getLogger(IMLProducer.class);

    private final Path projectsFolder;
    private final Path modulesDir;
    private final DependencyGraph dependencyGraph;
    private final String jdkVersion;

    // Extra external bundles that must be included for specific modules
    // Key: module symbolic name, Value: set of external bundle names
    // (e.g., org.eclipse.equinox.launcher for the app module)
    private final Map<String, Set<String>> perModuleExtraExternalBundles = new HashMap<>();

    public IMLProducer(Path projectsFolder, Path modulesDir, DependencyGraph dependencyGraph, String jdkVersion) {
        this.projectsFolder = projectsFolder;
        this.modulesDir = modulesDir;
        this.dependencyGraph = dependencyGraph;
        this.jdkVersion = jdkVersion != null ? jdkVersion : "21";
    }

    /**
     * Add an external bundle that should be included for a specific module.
     *
     * @param moduleName the module's symbolic name
     * @param bundleName the external bundle to add
     */
    public void addExtraExternalBundle(String moduleName, String bundleName) {
        perModuleExtraExternalBundles.computeIfAbsent(moduleName, k -> new HashSet<>()).add(bundleName);
    }

    /**
     * Generate .iml files for all bundles.
     *
     * @param bundles the bundles to generate .iml files for
     * @throws IOException if file writing fails
     */
    public void generateAll(List<Bundle> bundles) throws IOException {
        log.info("Generating .iml files for {} bundles", bundles.size());

        Files.createDirectories(modulesDir);

        for (Bundle bundle : bundles) {
            generateIML(bundle);
        }

        log.info("Generated {} .iml files in {}", bundles.size(), modulesDir);
    }

    /**
     * Generate a single .iml file for a bundle.
     *
     * @param bundle the bundle
     * @throws IOException if file writing fails
     */
    public void generateIML(Bundle bundle) throws IOException {
        String moduleName = bundle.getSymbolicName();
        Path imlFile = modulesDir.resolve(moduleName + ".iml");

        log.debug("Generating .iml for {}", moduleName);

        Document doc = XmlHelper.createDocument();

        // Root module element
        Element module = doc.createElement("module");
        module.setAttribute("type", "JAVA_MODULE");
        module.setAttribute("version", "4");
        doc.appendChild(module);

        // Component: NewModuleRootManager
        Element component = doc.createElement("component");
        component.setAttribute("name", "NewModuleRootManager");
        component.setAttribute("LANGUAGE_LEVEL", VersionHelper.toIdeaLanguageLevel(jdkVersion));
        module.appendChild(component);

        // Content root - calculate bundle path first
        Path bundleDir = bundle.getLocation();
        String bundleRelPath = getRelativePath(bundleDir);

        // Output paths - point to bundle's bin folder
        String outputFolder = bundle.getOutputFolder();
        if (outputFolder == null || outputFolder.isBlank()) {
            outputFolder = "bin";
        }
        String outputPath = "file://$MODULE_DIR$/" + bundleRelPath + "/" + outputFolder;

        Element output = doc.createElement("output");
        output.setAttribute("url", outputPath);
        component.appendChild(output);

        Element outputTest = doc.createElement("output-test");
        outputTest.setAttribute("url", outputPath);
        component.appendChild(outputTest);

        // Exclude output
        Element excludeOutput = doc.createElement("exclude-output");
        component.appendChild(excludeOutput);

        // Content root
        Element content = doc.createElement("content");
        content.setAttribute("url", "file://$MODULE_DIR$/" + bundleRelPath);
        component.appendChild(content);

        // Source folders
        for (String sourceFolder : bundle.getSourceFolders()) {
            Element sourceRoot = doc.createElement("sourceFolder");
            sourceRoot.setAttribute("url", "file://$MODULE_DIR$/" + bundleRelPath + "/" + sourceFolder);
            sourceRoot.setAttribute("isTestSource", "false");
            content.appendChild(sourceRoot);
        }

        // Exclude folders (target, bin)
        addExcludeFolder(content, bundleRelPath, "target");
        addExcludeFolder(content, bundleRelPath, "bin");

        // Order entries

        // 1. Inherited JDK
        Element inheritedJdk = doc.createElement("orderEntry");
        inheritedJdk.setAttribute("type", "inheritedJdk");
        component.appendChild(inheritedJdk);

        // 2. Source folder
        Element sourceEntry = doc.createElement("orderEntry");
        sourceEntry.setAttribute("type", "sourceFolder");
        sourceEntry.setAttribute("forTests", "false");
        component.appendChild(sourceEntry);

        // 3. Module dependencies (local bundles) - include transitive dependencies
        //    For standalone apps (non-OSGi with Main-Class), use pom.xml dependencies
        //    plus their transitive dependencies from the dependency graph
        Set<String> allDeps;
        if (bundle.isStandaloneApp() && !bundle.getPomDependencyArtifactIds().isEmpty()) {
            allDeps = new HashSet<>();
            for (String pomDep : bundle.getPomDependencyArtifactIds()) {
                if (dependencyGraph.isLocalBundle(pomDep)) {
                    allDeps.add(pomDep);
                    // Also add transitive dependencies of each pom dependency
                    allDeps.addAll(dependencyGraph.getTransitiveDependencies(pomDep));
                }
            }
        } else {
            Set<String> directDeps = dependencyGraph.getDependencies(moduleName);
            Set<String> transitiveDeps = dependencyGraph.getTransitiveDependencies(moduleName);
            allDeps = new HashSet<>(directDeps);
            allDeps.addAll(transitiveDeps);
        }

        for (String depName : allDeps) {
            if (dependencyGraph.isLocalBundle(depName)) {
                Element moduleEntry = doc.createElement("orderEntry");
                moduleEntry.setAttribute("type", "module");
                moduleEntry.setAttribute("module-name", depName);
                moduleEntry.setAttribute("exported", "");
                component.appendChild(moduleEntry);
            }
        }

        // 4. External bundles as library references
        //    For ALL modules (both OSGi and standalone), include only the external
        //    deps actually needed: direct + transitive from self and local deps,
        //    expanded via re-export closure (e.g., org.eclipse.ui re-exports
        //    org.eclipse.swt, org.eclipse.jface, org.eclipse.core.commands, etc.)
        //    This keeps each module's classpath precise, preventing ServiceLoader
        //    conflicts (like M2ELogbackConfigurator) from leaking across modules
        //    via IDEA's transitive module dependency resolution.
        Set<String> addedLibraries = new HashSet<>();
        Set<String> neededExternals = new HashSet<>();

        // Scan self + all local deps for external dependencies
        Set<String> modulesToScan = new HashSet<>();
        modulesToScan.add(moduleName);
        for (String dep : allDeps) {
            if (dependencyGraph.isLocalBundle(dep)) {
                modulesToScan.add(dep);
            }
        }
        for (String mod : modulesToScan) {
            for (String dep : dependencyGraph.getDependencies(mod)) {
                if (dependencyGraph.isExternalBundle(dep)) {
                    neededExternals.add(dep);
                }
            }
            for (String dep : dependencyGraph.getTransitiveDependencies(mod)) {
                if (dependencyGraph.isExternalBundle(dep)) {
                    neededExternals.add(dep);
                }
            }
        }

        // Add per-module extra external bundles (e.g., equinox launcher for app module only)
        Set<String> moduleExtras = perModuleExtraExternalBundles.getOrDefault(moduleName, Collections.emptySet());
        neededExternals.addAll(moduleExtras);

        // Expand via re-export closure: if org.eclipse.ui re-exports org.eclipse.swt,
        // org.eclipse.jface, etc., include those as well
        neededExternals = dependencyGraph.getReExportClosure(neededExternals);

        for (String extName : neededExternals) {
            if (dependencyGraph.getExternalBundle(extName) != null && addedLibraries.add(extName)) {
                Element libraryEntry = doc.createElement("orderEntry");
                libraryEntry.setAttribute("type", "library");
                libraryEntry.setAttribute("name", extName);
                libraryEntry.setAttribute("level", "project");
                component.appendChild(libraryEntry);
            }
        }

        // 5. Embedded libraries (Bundle-ClassPath JARs)
        for (String embeddedLib : bundle.getEmbeddedLibraries()) {
            Element libEntry = doc.createElement("orderEntry");
            libEntry.setAttribute("type", "module-library");
            component.appendChild(libEntry);

            Element library = doc.createElement("library");
            libEntry.appendChild(library);

            Element classes = doc.createElement("CLASSES");
            library.appendChild(classes);

            Element root = doc.createElement("root");
            root.setAttribute("url", "jar://$MODULE_DIR$/" + bundleRelPath + "/" + embeddedLib + "!/");
            classes.appendChild(root);

            Element javadoc = doc.createElement("JAVADOC");
            library.appendChild(javadoc);

            Element sources = doc.createElement("SOURCES");
            library.appendChild(sources);
        }

        // Write file
        XmlHelper.writeDocument(doc, imlFile);
        log.debug("  Written: {}", imlFile.getFileName());
    }

    /**
     * Get the relative path from modules directory to the bundle.
     */
    private String getRelativePath(Path bundleDir) {
        try {
            Path relativePath = modulesDir.relativize(bundleDir);
            return relativePath.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // Paths on different roots, use absolute path
            return bundleDir.toAbsolutePath().toString().replace('\\', '/');
        }
    }

    /**
     * Add an exclude folder element.
     */
    private void addExcludeFolder(Element content, String bundleRelPath, String folderName) {
        Element exclude = content.getOwnerDocument().createElement("excludeFolder");
        exclude.setAttribute("url", "file://$MODULE_DIR$/" + bundleRelPath + "/" + folderName);
        content.appendChild(exclude);
    }
}
