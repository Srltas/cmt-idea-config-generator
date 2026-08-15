package com.cubrid.tools.ideaconfig.provision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Materializes the Eclipse dependency folder from Tycho's local p2 cache, so that
 * generating an IDEA configuration needs nothing but a Maven build of the project
 * itself - no Eclipse IDE installation and no assembled product.
 *
 * <p>Tycho stores the resolved target platform as
 * {@code <maven-repo>/p2/osgi/bundle/<name>/<version>/<name>-<version>.jar}, while
 * Eclipse tooling expects a flat folder of {@code <name>_<version>.jar}. Bundles whose
 * {@code Bundle-ClassPath} points at nested JARs are exploded into directories, because
 * IntelliJ IDEA cannot read a JAR inside a JAR; Eclipse ships those bundles unpacked for
 * the same reason.
 */
public class P2Provisioner {

    private static final Logger log = LoggerFactory.getLogger(P2Provisioner.class);

    private static final String SOURCE_BUNDLE_SUFFIX = ".source";
    private static final String OSGI_FRAMEWORK_PREFIX = "org.eclipse.osgi_";

    private final Path p2BundleDir;
    private final Path targetDir;

    public P2Provisioner(Path mavenRepo, Path targetDir) {
        this.p2BundleDir = mavenRepo.resolve("p2").resolve("osgi").resolve("bundle");
        this.targetDir = targetDir;
    }

    public Path getP2BundleDir() {
        return p2BundleDir;
    }

    public boolean isCacheAvailable() {
        return Files.isDirectory(p2BundleDir);
    }

    /**
     * Copy every bundle missing from the target folder. Existing entries are left alone,
     * so a folder curated by hand is never overwritten.
     *
     * @return number of bundles newly materialized
     */
    public int provision() throws IOException {
        Files.createDirectories(targetDir);

        int created = 0;
        int kept = 0;
        for (Path bundleDir : listDirectories(p2BundleDir)) {
            String name = bundleDir.getFileName().toString();
            if (name.endsWith(SOURCE_BUNDLE_SUFFIX)) {
                continue;
            }
            for (Path versionDir : listDirectories(bundleDir)) {
                String version = versionDir.getFileName().toString();
                Path jar = versionDir.resolve(name + "-" + version + ".jar");
                if (!Files.isRegularFile(jar)) {
                    continue;
                }
                if (materialize(jar, name + "_" + version)) {
                    created++;
                } else {
                    kept++;
                }
            }
        }

        log.info("Provisioned {} bundles from {} ({} already present)", created, p2BundleDir, kept);
        return created;
    }

    /** The OSGi framework JAR must be there, otherwise nothing will launch. */
    public boolean hasOsgiFramework() throws IOException {
        if (!Files.isDirectory(targetDir)) {
            return false;
        }
        try (var stream = Files.list(targetDir)) {
            return stream.anyMatch(p -> p.getFileName().toString().startsWith(OSGI_FRAMEWORK_PREFIX));
        }
    }

    private boolean materialize(Path jar, String flatName) throws IOException {
        Path asJar = targetDir.resolve(flatName + ".jar");
        Path asDir = targetDir.resolve(flatName);
        if (Files.exists(asJar) || Files.isDirectory(asDir)) {
            return false;
        }
        if (hasNestedClassPath(jar)) {
            explode(jar, asDir);
            log.debug("  Unpacked (nested Bundle-ClassPath): {}", flatName);
        } else {
            Files.copy(jar, asJar, StandardCopyOption.REPLACE_EXISTING);
            log.debug("  Copied: {}", flatName);
        }
        return true;
    }

    private static boolean hasNestedClassPath(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return false;
            }
            String classPath = manifest.getMainAttributes().getValue("Bundle-ClassPath");
            if (classPath == null) {
                return false;
            }
            for (String entry : classPath.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty() && !".".equals(trimmed)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void explode(Path jar, Path dir) throws IOException {
        Files.createDirectories(dir);
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path out = dir.resolve(entry.getName()).normalize();
                if (!out.startsWith(dir)) {
                    log.warn("  Skipping entry outside bundle directory: {}", entry.getName());
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = jarFile.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static List<Path> listDirectories(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory).sorted().toList();
        }
    }
}
