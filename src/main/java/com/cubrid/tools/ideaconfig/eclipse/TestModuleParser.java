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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a Maven-style test module: extracts source folders, classifies
 * dependencies as local OSGi bundles vs external Maven JARs, and resolves
 * external JAR paths.
 *
 * <p>Two strategies for external resolution, in order:
 * <ol>
 *   <li>{@code mvn dependency:build-classpath} (handles BOMs and transitive deps)</li>
 *   <li>Direct {@code ~/.m2/repository} lookup of each pom direct dependency</li>
 * </ol>
 * The fallback runs when Maven is unavailable or fails (e.g. missing local SNAPSHOT
 * deps that haven't been installed yet).
 */
public class TestModuleParser {

    private static final Logger log = LoggerFactory.getLogger(TestModuleParser.class);

    private static final String LOCAL_DEPENDENCY_GROUP_PREFIX = "com.cubrid.cubridmigration";

    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^}]+)}");

    private final String localGroupPrefix;
    private final Path mavenLocalRepo;

    public TestModuleParser() {
        this(LOCAL_DEPENDENCY_GROUP_PREFIX, defaultMavenLocalRepo());
    }

    public TestModuleParser(String localGroupPrefix, Path mavenLocalRepo) {
        this.localGroupPrefix = localGroupPrefix;
        this.mavenLocalRepo = mavenLocalRepo;
    }

    private static Path defaultMavenLocalRepo() {
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
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

        boolean resolvedViaMaven = resolveViaMaven(pomFile, module);
        if (!resolvedViaMaven) {
            resolveViaLocalRepo(pomDoc, module);
        }

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

    /**
     * Try {@code mvn dependency:build-classpath} for accurate (transitive, BOM-aware)
     * resolution. Returns true if Maven succeeded and at least one external JAR was added.
     */
    private boolean resolveViaMaven(Path pomFile, TestModule module) {
        Path tempFile;
        try {
            tempFile = Files.createTempFile("cmt-classpath-", ".txt");
        } catch (IOException e) {
            log.debug("  Could not create temp file for classpath resolution: {}", e.getMessage());
            return false;
        }

        ProcessBuilder pb = new ProcessBuilder(
            "mvn", "-q", "-f", pomFile.toString(),
            "dependency:build-classpath",
            "-DincludeScope=test",
            "-DexcludeGroupIds=" + localGroupPrefix,
            "-Dmdep.outputFile=" + tempFile.toAbsolutePath(),
            "-Dmdep.pathSeparator=" + System.getProperty("path.separator")
        );
        pb.redirectErrorStream(true);

        try {
            log.info("  Resolving Maven test classpath for {} (this may take a while)...", module.getName());
            Process process = pb.start();
            byte[] output = process.getInputStream().readAllBytes();
            boolean done = process.waitFor(5, TimeUnit.MINUTES);
            if (!done) {
                process.destroyForcibly();
                log.warn("  mvn dependency:build-classpath timed out for {}", module.getName());
                return false;
            }
            if (process.exitValue() != 0) {
                log.warn("  mvn dependency:build-classpath failed (exit {}) for {}; falling back to ~/.m2 lookup",
                    process.exitValue(), module.getName());
                log.debug("  Maven output:\n{}", new String(output, StandardCharsets.UTF_8));
                return false;
            }

            String classpath = Files.readString(tempFile, StandardCharsets.UTF_8).trim();
            if (classpath.isEmpty()) {
                return true; // Maven succeeded with empty classpath; no fallback needed.
            }
            for (String entry : classpath.split(Pattern.quote(System.getProperty("path.separator")))) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                Path jarPath = Path.of(trimmed);
                if (Files.exists(jarPath)) {
                    module.addExternalLibrary(jarPath);
                }
            }
            return true;
        } catch (IOException e) {
            log.warn("  Could not run Maven for {}: {}; falling back to ~/.m2 lookup",
                module.getName(), e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Fallback: parse direct pom dependencies and resolve each against {@code ~/.m2/repository}.
     * Skips local-groupId deps (covered by module deps) and deps without an explicit version
     * (BOM-managed; cannot be resolved without running Maven).
     */
    private void resolveViaLocalRepo(Document pomDoc, TestModule module) {
        if (mavenLocalRepo == null || !Files.isDirectory(mavenLocalRepo)) {
            log.warn("  Maven local repo not found: {}", mavenLocalRepo);
            return;
        }

        Map<String, String> properties = parsePomProperties(pomDoc);
        Element project = pomDoc.getDocumentElement();

        int found = 0;
        int skipped = 0;
        Set<String> seen = new LinkedHashSet<>();

        List<Element> deps = XmlHelper.getChildElement(project, "dependencies")
            .map(d -> XmlHelper.getChildElements(d, "dependency"))
            .orElse(List.of());

        for (Element dep : deps) {
            String groupId = substitute(XmlHelper.getChildText(dep, "groupId"), properties);
            String artifactId = substitute(XmlHelper.getChildText(dep, "artifactId"), properties);
            String version = substitute(XmlHelper.getChildText(dep, "version"), properties);

            if (groupId == null || artifactId == null) continue;
            if (groupId.startsWith(localGroupPrefix)) continue;
            if (version == null || version.isBlank()) {
                log.debug("    No version for {}:{} (likely BOM-managed); skipping", groupId, artifactId);
                skipped++;
                continue;
            }

            String key = groupId + ":" + artifactId + ":" + version;
            if (!seen.add(key)) continue;

            Path jar = mavenLocalRepo
                .resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar");

            if (Files.exists(jar)) {
                module.addExternalLibrary(jar);
                found++;
            } else {
                log.debug("    Not in local repo: {}", jar);
                skipped++;
            }
        }

        log.info("  Local-repo fallback for {}: {} resolved, {} skipped", module.getName(), found, skipped);
    }

    private Map<String, String> parsePomProperties(Document pomDoc) {
        Map<String, String> properties = new HashMap<>();
        Element project = pomDoc.getDocumentElement();
        XmlHelper.getChildElement(project, "properties").ifPresent(propsEl -> {
            org.w3c.dom.NodeList children = propsEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node node = children.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    properties.put(node.getNodeName(), node.getTextContent().trim());
                }
            }
        });
        return properties;
    }

    private static String substitute(String value, Map<String, String> properties) {
        if (value == null || !value.contains("${")) {
            return value == null ? null : value.trim();
        }
        Matcher matcher = PROPERTY_REF.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = properties.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString().trim();
    }
}
