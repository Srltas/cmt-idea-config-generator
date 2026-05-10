package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.TestModule;
import com.cubrid.tools.ideaconfig.model.TestModule.SourceFolder;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Parses a Maven-style test module: extracts source folders, classifies dependencies as local
 * OSGi bundles vs external Maven JARs, and resolves external JAR paths via
 * {@code mvn dependency:build-classpath}.
 *
 * <p>To avoid requiring the user to {@code mvn install} local SNAPSHOT bundles before running
 * the generator, dependencies whose groupId starts with {@link #localGroupPrefix} are stripped
 * from a stub pom written next to the original. Maven then resolves only third-party deps
 * (with full BOM and transitive support); local bundles are wired up separately as IDEA module
 * dependencies.
 */
public class TestModuleParser {

    private static final Logger log = LoggerFactory.getLogger(TestModuleParser.class);

    private static final String LOCAL_DEPENDENCY_GROUP_PREFIX = "com.cubrid.cubridmigration";

    private final String localGroupPrefix;

    public TestModuleParser() {
        this(LOCAL_DEPENDENCY_GROUP_PREFIX);
    }

    public TestModuleParser(String localGroupPrefix) {
        this.localGroupPrefix = localGroupPrefix;
    }

    public TestModule parse(Path moduleDir, Set<String> localBundleNames) throws IOException {
        Path pomFile = moduleDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            log.warn("No pom.xml in test module dir: {}", moduleDir);
            return null;
        }

        TestModule module = new TestModule(moduleDir.getFileName().toString(), moduleDir);

        addSourceFolderIfPresent(moduleDir, "src/main/java", SourceFolder.Kind.MAIN_JAVA, module);
        addSourceFolderIfPresent(moduleDir, "src/main/resources", SourceFolder.Kind.MAIN_RESOURCES, module);
        addSourceFolderIfPresent(moduleDir, "src/test/java", SourceFolder.Kind.TEST_JAVA, module);
        addSourceFolderIfPresent(moduleDir, "src/test/resources", SourceFolder.Kind.TEST_RESOURCES, module);

        Document pomDoc;
        try {
            pomDoc = XmlHelper.parseFile(pomFile);
        } catch (SAXException e) {
            throw new IOException("Failed to parse " + pomFile, e);
        }

        addLocalDependencies(pomDoc, localBundleNames, module);
        resolveExternalLibraries(pomFile, pomDoc, module);

        log.info("  Test module: {} ({} src folders, {} local deps, {} external libs)",
            module.getName(),
            module.getSourceFolders().size(),
            module.getLocalModuleDependencies().size(),
            module.getExternalLibraries().size());

        return module;
    }

    private void addSourceFolderIfPresent(Path moduleDir, String relative,
                                          SourceFolder.Kind kind, TestModule module) {
        if (Files.isDirectory(moduleDir.resolve(relative))) {
            module.addSourceFolder(new SourceFolder(relative, kind));
        }
    }

    private void addLocalDependencies(Document pomDoc, Set<String> localBundleNames, TestModule module) {
        Element project = pomDoc.getDocumentElement();
        XmlHelper.getChildElement(project, "dependencies").ifPresent(deps -> {
            for (Element dep : XmlHelper.getChildElements(deps, "dependency")) {
                String groupId = XmlHelper.getChildText(dep, "groupId");
                String artifactId = XmlHelper.getChildText(dep, "artifactId");
                if (groupId == null || artifactId == null) continue;

                if (groupId.startsWith(localGroupPrefix) && localBundleNames.contains(artifactId.trim())) {
                    module.addLocalModuleDependency(artifactId.trim());
                }
            }
        });
    }

    protected void resolveExternalLibraries(Path pomFile, Document pomDoc, TestModule module) {
        Path stubPom;
        try {
            stubPom = writeStubPom(pomFile, pomDoc);
        } catch (IOException e) {
            log.error("  Could not write stub pom for {}: {}; external libraries will be missing",
                module.getName(), e.getMessage());
            return;
        }

        try {
            runMavenBuildClasspath(stubPom, module);
        } catch (IOException e) {
            log.error("  Maven classpath resolution failed for {}: {}; external libraries will be missing",
                module.getName(), e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(stubPom);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Clone the original pom and remove top-level {@code <dependency>} entries whose groupId
     * starts with {@link #localGroupPrefix}. {@code <dependencyManagement>} (BOM imports) and
     * {@code <properties>} are preserved so Maven can still resolve BOM-managed versions
     * correctly. The stub is written as a sibling of the original so {@code ${project.basedir}}
     * remains valid for profile activation and {@code <systemPath>} references.
     */
    Path writeStubPom(Path originalPom, Document originalPomDoc) throws IOException {
        Document stubDoc = (Document) originalPomDoc.cloneNode(true);
        Element project = stubDoc.getDocumentElement();
        XmlHelper.getChildElement(project, "dependencies").ifPresent(deps -> {
            List<Element> toRemove = new ArrayList<>();
            for (Element dep : XmlHelper.getChildElements(deps, "dependency")) {
                String groupId = XmlHelper.getChildText(dep, "groupId");
                if (groupId != null && groupId.trim().startsWith(localGroupPrefix)) {
                    toRemove.add(dep);
                }
            }
            for (Element dep : toRemove) {
                deps.removeChild(dep);
            }
        });

        Path stubPom = originalPom.resolveSibling("pom-cmt-stub-" + UUID.randomUUID() + ".xml");
        XmlHelper.writeDocument(stubDoc, stubPom);
        return stubPom;
    }

    private void runMavenBuildClasspath(Path stubPom, TestModule module) throws IOException {
        Path tempFile = Files.createTempFile("cmt-classpath-", ".txt");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "mvn", "-q", "-f", stubPom.toString(),
                "dependency:build-classpath",
                "-DincludeScope=test",
                "-Dmdep.outputFile=" + tempFile.toAbsolutePath(),
                "-Dmdep.pathSeparator=" + System.getProperty("path.separator")
            );
            pb.redirectErrorStream(true);

            log.info("  Resolving Maven test classpath for {} (this may take a while)...", module.getName());
            Process process = pb.start();
            byte[] output = process.getInputStream().readAllBytes();
            boolean done = process.waitFor(5, TimeUnit.MINUTES);
            if (!done) {
                process.destroyForcibly();
                throw new IOException("mvn dependency:build-classpath timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("mvn dependency:build-classpath exited "
                    + process.exitValue() + ":\n" + new String(output, StandardCharsets.UTF_8));
            }

            String classpath = Files.readString(tempFile, StandardCharsets.UTF_8).trim();
            if (classpath.isEmpty()) {
                return;
            }
            for (String entry : classpath.split(Pattern.quote(System.getProperty("path.separator")))) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                Path jarPath = Path.of(trimmed);
                if (Files.exists(jarPath)) {
                    module.addExternalLibrary(jarPath);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running mvn", e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }
}
