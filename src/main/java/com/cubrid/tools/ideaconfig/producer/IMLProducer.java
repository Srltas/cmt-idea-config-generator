package com.cubrid.tools.ideaconfig.producer;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.DependencyGraph;
import com.cubrid.tools.ideaconfig.model.TestModule;
import com.cubrid.tools.ideaconfig.model.TestModule.SourceFolder;
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
 * Produces IntelliJ IDEA .iml module files for Eclipse bundles and test modules.
 */
public class IMLProducer {

    private static final Logger log = LoggerFactory.getLogger(IMLProducer.class);

    private final Path modulesDir;
    private final DependencyGraph dependencyGraph;
    private final String languageLevel;

    // Per-module extra external bundles (e.g., org.eclipse.equinox.launcher
    // for the app module that the Desktop run config targets).
    private final Map<String, Set<String>> perModuleExtraExternalBundles = new HashMap<>();

    public IMLProducer(Path modulesDir, DependencyGraph dependencyGraph, String jdkVersion) {
        this.modulesDir = modulesDir;
        this.dependencyGraph = dependencyGraph;
        this.languageLevel = "JDK_" + (jdkVersion != null ? jdkVersion : "21");
    }

    public void addExtraExternalBundle(String moduleName, String bundleName) {
        perModuleExtraExternalBundles.computeIfAbsent(moduleName, k -> new HashSet<>()).add(bundleName);
    }

    public void generateAll(List<Bundle> bundles) throws IOException {
        log.info("Generating .iml files for {} bundles", bundles.size());
        Files.createDirectories(modulesDir);
        for (Bundle bundle : bundles) {
            generateBundleIml(bundle);
        }
        log.info("Generated {} .iml files in {}", bundles.size(), modulesDir);
    }

    public void generateTestModules(List<TestModule> testModules) throws IOException {
        if (testModules.isEmpty()) {
            return;
        }
        log.info("Generating .iml files for {} test modules", testModules.size());
        Files.createDirectories(modulesDir);
        for (TestModule module : testModules) {
            generateTestModuleIml(module);
        }
    }

    private void generateBundleIml(Bundle bundle) throws IOException {
        String moduleName = bundle.getSymbolicName();
        Path imlFile = modulesDir.resolve(moduleName + ".iml");

        Document doc = XmlHelper.createDocument();
        Element module = doc.createElement("module");
        module.setAttribute("type", "JAVA_MODULE");
        module.setAttribute("version", "4");
        doc.appendChild(module);

        Element component = doc.createElement("component");
        component.setAttribute("name", "NewModuleRootManager");
        component.setAttribute("LANGUAGE_LEVEL", languageLevel);
        module.appendChild(component);

        String bundleRelPath = relativizeFromModulesDir(bundle.getLocation());

        String outputFolder = bundle.getOutputFolder();
        if (outputFolder == null || outputFolder.isBlank()) {
            outputFolder = "bin";
        }
        String outputUrl = "file://$MODULE_DIR$/" + bundleRelPath + "/" + outputFolder;

        appendOutput(doc, component, outputUrl);

        Element content = doc.createElement("content");
        content.setAttribute("url", "file://$MODULE_DIR$/" + bundleRelPath);
        component.appendChild(content);

        for (String sourceFolder : bundle.getSourceFolders()) {
            Element sourceRoot = doc.createElement("sourceFolder");
            sourceRoot.setAttribute("url", "file://$MODULE_DIR$/" + bundleRelPath + "/" + sourceFolder);
            sourceRoot.setAttribute("isTestSource", "false");
            content.appendChild(sourceRoot);
        }
        appendExcludeFolder(content, bundleRelPath, "target");
        appendExcludeFolder(content, bundleRelPath, "bin");

        appendInheritedJdk(doc, component);
        appendSourceOrderEntry(doc, component);

        // Module dependencies (local bundles) — direct + transitive.
        // Standalone apps (Main-Class, no Require-Bundle) use pom.xml deps instead.
        Set<String> allDeps = computeAllDependencies(bundle, moduleName);
        for (String depName : allDeps) {
            if (dependencyGraph.isLocalBundle(depName)) {
                Element entry = doc.createElement("orderEntry");
                entry.setAttribute("type", "module");
                entry.setAttribute("module-name", depName);
                entry.setAttribute("exported", "");
                component.appendChild(entry);
            }
        }

        // External bundles, expanded via re-export closure to catch
        // org.eclipse.ui -> swt/jface/etc.; per-module extras (equinox launcher) added too.
        Set<String> neededExternals = collectNeededExternals(moduleName, allDeps);
        Set<String> added = new HashSet<>();
        for (String extName : neededExternals) {
            if (dependencyGraph.getExternalBundle(extName) != null && added.add(extName)) {
                Element entry = doc.createElement("orderEntry");
                entry.setAttribute("type", "library");
                entry.setAttribute("name", extName);
                entry.setAttribute("level", "project");
                component.appendChild(entry);
            }
        }

        // Embedded libraries from Bundle-ClassPath
        for (String embeddedLib : bundle.getEmbeddedLibraries()) {
            appendModuleLibrary(doc, component,
                "jar://$MODULE_DIR$/" + bundleRelPath + "/" + embeddedLib + "!/", null);
        }

        XmlHelper.writeDocument(doc, imlFile);
        log.debug("  Written: {}", imlFile.getFileName());
    }

    private Set<String> computeAllDependencies(Bundle bundle, String moduleName) {
        Set<String> allDeps = new HashSet<>();
        if (bundle.isStandaloneApp() && !bundle.getPomDependencyArtifactIds().isEmpty()) {
            for (String pomDep : bundle.getPomDependencyArtifactIds()) {
                if (dependencyGraph.isLocalBundle(pomDep)) {
                    allDeps.add(pomDep);
                    allDeps.addAll(dependencyGraph.getTransitiveDependencies(pomDep));
                }
            }
        } else {
            allDeps.addAll(dependencyGraph.getDependencies(moduleName));
            allDeps.addAll(dependencyGraph.getTransitiveDependencies(moduleName));
        }
        return allDeps;
    }

    private Set<String> collectNeededExternals(String moduleName, Set<String> allDeps) {
        Set<String> needed = new HashSet<>();
        Set<String> modulesToScan = new HashSet<>();
        modulesToScan.add(moduleName);
        for (String dep : allDeps) {
            if (dependencyGraph.isLocalBundle(dep)) {
                modulesToScan.add(dep);
            }
        }
        for (String mod : modulesToScan) {
            for (String dep : dependencyGraph.getDependencies(mod)) {
                if (dependencyGraph.isExternalBundle(dep)) needed.add(dep);
            }
            for (String dep : dependencyGraph.getTransitiveDependencies(mod)) {
                if (dependencyGraph.isExternalBundle(dep)) needed.add(dep);
            }
        }
        needed.addAll(perModuleExtraExternalBundles.getOrDefault(moduleName, Collections.emptySet()));
        return dependencyGraph.getReExportClosure(needed);
    }

    private void generateTestModuleIml(TestModule testModule) throws IOException {
        String moduleName = testModule.getName();
        Path imlFile = modulesDir.resolve(moduleName + ".iml");

        Document doc = XmlHelper.createDocument();
        Element module = doc.createElement("module");
        module.setAttribute("type", "JAVA_MODULE");
        module.setAttribute("version", "4");
        doc.appendChild(module);

        Element component = doc.createElement("component");
        component.setAttribute("name", "NewModuleRootManager");
        component.setAttribute("LANGUAGE_LEVEL", languageLevel);
        component.setAttribute("inherit-compiler-output", "false");
        module.appendChild(component);

        String moduleRelPath = relativizeFromModulesDir(testModule.getLocation());
        String outputUrl = "file://$MODULE_DIR$/" + moduleRelPath + "/target/classes";
        String testOutputUrl = "file://$MODULE_DIR$/" + moduleRelPath + "/target/test-classes";

        Element output = doc.createElement("output");
        output.setAttribute("url", outputUrl);
        component.appendChild(output);
        Element outputTest = doc.createElement("output-test");
        outputTest.setAttribute("url", testOutputUrl);
        component.appendChild(outputTest);
        component.appendChild(doc.createElement("exclude-output"));

        Element content = doc.createElement("content");
        content.setAttribute("url", "file://$MODULE_DIR$/" + moduleRelPath);
        component.appendChild(content);

        for (SourceFolder folder : testModule.getSourceFolders()) {
            Element src = doc.createElement("sourceFolder");
            src.setAttribute("url",
                "file://$MODULE_DIR$/" + moduleRelPath + "/" + folder.relativePath());
            if (folder.isResource()) {
                src.setAttribute("type",
                    folder.isTestSource() ? "java-test-resource" : "java-resource");
            } else {
                src.setAttribute("isTestSource", String.valueOf(folder.isTestSource()));
            }
            content.appendChild(src);
        }
        appendExcludeFolder(content, moduleRelPath, "target");

        appendInheritedJdk(doc, component);
        appendSourceOrderEntry(doc, component);

        for (String dep : testModule.getLocalModuleDependencies()) {
            Element entry = doc.createElement("orderEntry");
            entry.setAttribute("type", "module");
            entry.setAttribute("module-name", dep);
            entry.setAttribute("scope", "TEST");
            component.appendChild(entry);
        }

        for (Path jarPath : testModule.getExternalLibraries()) {
            String jarUrl = "jar://" + jarPath.toAbsolutePath().toString().replace('\\', '/') + "!/";
            appendModuleLibrary(doc, component, jarUrl, "TEST");
        }

        XmlHelper.writeDocument(doc, imlFile);
        log.debug("  Written test module .iml: {}", imlFile.getFileName());
    }

    private String relativizeFromModulesDir(Path target) {
        try {
            return modulesDir.relativize(target).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return target.toAbsolutePath().toString().replace('\\', '/');
        }
    }

    private static void appendOutput(Document doc, Element component, String outputUrl) {
        Element output = doc.createElement("output");
        output.setAttribute("url", outputUrl);
        component.appendChild(output);
        Element outputTest = doc.createElement("output-test");
        outputTest.setAttribute("url", outputUrl);
        component.appendChild(outputTest);
        component.appendChild(doc.createElement("exclude-output"));
    }

    private static void appendInheritedJdk(Document doc, Element component) {
        Element entry = doc.createElement("orderEntry");
        entry.setAttribute("type", "inheritedJdk");
        component.appendChild(entry);
    }

    private static void appendSourceOrderEntry(Document doc, Element component) {
        Element entry = doc.createElement("orderEntry");
        entry.setAttribute("type", "sourceFolder");
        entry.setAttribute("forTests", "false");
        component.appendChild(entry);
    }

    private static void appendExcludeFolder(Element content, String relPath, String folder) {
        Element exclude = content.getOwnerDocument().createElement("excludeFolder");
        exclude.setAttribute("url", "file://$MODULE_DIR$/" + relPath + "/" + folder);
        content.appendChild(exclude);
    }

    /**
     * Append a module-level library (used for embedded JARs and TEST jars).
     * If scope is non-null, sets it on the orderEntry.
     */
    private static void appendModuleLibrary(Document doc, Element component,
                                            String classesJarUrl, String scope) {
        Element entry = doc.createElement("orderEntry");
        entry.setAttribute("type", "module-library");
        if (scope != null) {
            entry.setAttribute("scope", scope);
        }
        component.appendChild(entry);

        Element library = doc.createElement("library");
        entry.appendChild(library);

        Element classes = doc.createElement("CLASSES");
        library.appendChild(classes);
        Element root = doc.createElement("root");
        root.setAttribute("url", classesJarUrl);
        classes.appendChild(root);

        library.appendChild(doc.createElement("JAVADOC"));
        library.appendChild(doc.createElement("SOURCES"));
    }
}
