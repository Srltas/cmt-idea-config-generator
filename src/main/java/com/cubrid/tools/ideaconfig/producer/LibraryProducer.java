package com.cubrid.tools.ideaconfig.producer;

import com.cubrid.tools.ideaconfig.model.DependencyGraph;
import com.cubrid.tools.ideaconfig.model.DependencyGraph.ExternalBundle;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Produces IntelliJ IDEA library configuration files for external bundles.
 */
public class LibraryProducer {

    private static final Logger log = LoggerFactory.getLogger(LibraryProducer.class);

    private final Path librariesDir;
    private final Path eclipseDepsDir;

    public LibraryProducer(Path librariesDir, Path eclipseDepsDir) {
        this.librariesDir = librariesDir;
        this.eclipseDepsDir = eclipseDepsDir;
    }

    /**
     * Generate library XML files for all external bundles.
     *
     * @param externalBundles the external bundles
     * @throws IOException if file writing fails
     */
    public void generateAll(Collection<ExternalBundle> externalBundles) throws IOException {
        log.info("Generating library configurations for {} external bundles", externalBundles.size());

        Files.createDirectories(librariesDir);

        int generated = 0;
        for (ExternalBundle bundle : externalBundles) {
            if (generateLibrary(bundle)) {
                generated++;
            }
        }

        log.info("Generated {} library files in {}", generated, librariesDir);
    }

    /**
     * Generate a library XML file for a single external bundle.
     *
     * @param bundle the external bundle
     * @return true if file was generated
     * @throws IOException if file writing fails
     */
    public boolean generateLibrary(ExternalBundle bundle) throws IOException {
        String bundleName = bundle.getSymbolicName();

        // Convert bundle name to safe filename
        String safeFileName = bundleName.replace('.', '_');
        Path libraryFile = librariesDir.resolve(safeFileName + ".xml");

        log.debug("Generating library for {}", bundleName);

        Document doc = XmlHelper.createDocument();

        // Root component element
        Element component = doc.createElement("component");
        component.setAttribute("name", "libraryTable");
        doc.appendChild(component);

        // Library element
        Element library = doc.createElement("library");
        library.setAttribute("name", bundleName);
        component.appendChild(library);

        // CLASSES
        Element classes = doc.createElement("CLASSES");
        library.appendChild(classes);

        // Add JAR path if known
        Path jarPath = bundle.getJarPath();
        if (jarPath != null && Files.exists(jarPath)) {
            Element root = doc.createElement("root");
            root.setAttribute("url", "jar://" + jarPath.toAbsolutePath().toString().replace('\\', '/') + "!/");
            classes.appendChild(root);
        } else if (eclipseDepsDir != null) {
            // Try to find JAR or folder in eclipse deps directory
            List<Path> foundJars = findJarsInDepsDir(bundleName);
            if (!foundJars.isEmpty()) {
                for (Path foundJar : foundJars) {
                    Element root = doc.createElement("root");
                    root.setAttribute("url", "jar://" + foundJar.toAbsolutePath().toString().replace('\\', '/') + "!/");
                    classes.appendChild(root);
                }
            } else {
                // Placeholder - JAR needs to be downloaded
                log.debug("  No JAR found for {}, creating placeholder", bundleName);
                Element root = doc.createElement("root");
                root.setAttribute("url", "jar://$PROJECT_DIR$/lib/" + bundleName + ".jar!/");
                classes.appendChild(root);
            }
        }

        // JAVADOC (empty)
        Element javadoc = doc.createElement("JAVADOC");
        library.appendChild(javadoc);

        // SOURCES (empty)
        Element sources = doc.createElement("SOURCES");
        library.appendChild(sources);

        // Write file
        XmlHelper.writeDocument(doc, libraryFile);
        log.debug("  Written: {}", libraryFile.getFileName());

        return true;
    }

    /**
     * Find JAR files for a bundle in the Eclipse dependencies directory.
     * This includes:
     * - Direct JAR files (bundleName_version.jar or bundleName.jar)
     * - Folder-based bundles with lib/*.jar files inside
     */
    private List<Path> findJarsInDepsDir(String bundleName) {
        List<Path> result = new ArrayList<>();

        if (eclipseDepsDir == null || !Files.exists(eclipseDepsDir)) {
            return result;
        }

        try (var stream = Files.walk(eclipseDepsDir, 2)) {
            stream.forEach(p -> {
                String fileName = p.getFileName().toString();

                // Check for direct JAR file
                if (fileName.endsWith(".jar") &&
                    (fileName.startsWith(bundleName + "_") ||
                     fileName.equals(bundleName + ".jar"))) {
                    result.add(p);
                }

                // Check for folder-based bundle
                if (Files.isDirectory(p) &&
                    (fileName.startsWith(bundleName + "_") ||
                     fileName.equals(bundleName))) {
                    // Look for lib/*.jar inside the folder
                    Path libDir = p.resolve("lib");
                    if (Files.exists(libDir) && Files.isDirectory(libDir)) {
                        try (var libStream = Files.list(libDir)) {
                            libStream.filter(jar -> jar.toString().endsWith(".jar"))
                                     .forEach(result::add);
                        } catch (IOException e) {
                            log.warn("Error listing lib folder for {}: {}", bundleName, e.getMessage());
                        }
                    }
                }
            });
        } catch (IOException e) {
            log.warn("Error searching for JAR {}: {}", bundleName, e.getMessage());
        }

        return result;
    }

    /**
     * Generate a combined library for all Eclipse platform dependencies.
     *
     * @param externalBundles the external bundles
     * @throws IOException if file writing fails
     */
    public void generateCombinedEclipseLibrary(Collection<ExternalBundle> externalBundles) throws IOException {
        Path libraryFile = librariesDir.resolve("Eclipse_Platform.xml");

        log.info("Generating combined Eclipse platform library");

        Document doc = XmlHelper.createDocument();

        // Root component element
        Element component = doc.createElement("component");
        component.setAttribute("name", "libraryTable");
        doc.appendChild(component);

        // Library element
        Element library = doc.createElement("library");
        library.setAttribute("name", "Eclipse Platform");
        component.appendChild(library);

        // CLASSES
        Element classes = doc.createElement("CLASSES");
        library.appendChild(classes);

        // Add all JARs
        if (eclipseDepsDir != null && Files.exists(eclipseDepsDir)) {
            try (var stream = Files.walk(eclipseDepsDir, 2)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .forEach(jarPath -> {
                          Element root = doc.createElement("root");
                          root.setAttribute("url", "jar://" +
                              jarPath.toAbsolutePath().toString().replace('\\', '/') + "!/");
                          classes.appendChild(root);
                      });
            }
        }

        // JAVADOC (empty)
        Element javadoc = doc.createElement("JAVADOC");
        library.appendChild(javadoc);

        // SOURCES (empty)
        Element sources = doc.createElement("SOURCES");
        library.appendChild(sources);

        // Write file
        XmlHelper.writeDocument(doc, libraryFile);
        log.info("Written: {}", libraryFile);
    }
}
