package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestParserTest {

    @TempDir
    Path tempDir;

    private ManifestParser parser;
    private Path bundleDir;
    private Path metaInfDir;

    @BeforeEach
    void setUp() throws IOException {
        parser = new ManifestParser();
        bundleDir = tempDir.resolve("test.bundle");
        metaInfDir = bundleDir.resolve("META-INF");
        Files.createDirectories(metaInfDir);
    }

    @Test
    void testParseBasicManifest() throws IOException {
        Path manifestFile = metaInfDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-SymbolicName: com.example.test
            Bundle-Version: 1.0.0
            """);

        Bundle bundle = parser.parseBundle(bundleDir);

        assertThat(bundle.getSymbolicName()).isEqualTo("com.example.test");
        assertThat(bundle.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void testParseSymbolicNameStripsDirectives() throws IOException {
        Path manifestFile = metaInfDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-SymbolicName: com.example.test;singleton:=true
            Bundle-Version: 1.0.0
            """);

        Bundle bundle = parser.parseBundle(bundleDir);

        assertThat(bundle.getSymbolicName()).isEqualTo("com.example.test");
    }

    @Test
    void testParseRequireBundle() throws IOException {
        Path manifestFile = metaInfDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-SymbolicName: com.example.test
            Bundle-Version: 1.0.0
            Require-Bundle: org.eclipse.core.runtime,
             com.example.dep1;bundle-version="[1.0.0,2.0.0)",
             com.example.dep2;resolution:=optional,
             com.example.dep3;visibility:=reexport
            """);

        Bundle bundle = parser.parseBundle(bundleDir);

        assertThat(bundle.getRequiredBundles()).hasSize(4);
        assertThat(bundle.getRequiredBundles().get(0).getBundleName())
            .isEqualTo("org.eclipse.core.runtime");
        assertThat(bundle.getRequiredBundles().get(1).getBundleName())
            .isEqualTo("com.example.dep1");
        assertThat(bundle.getRequiredBundles().get(1).getVersionRange())
            .isEqualTo("[1.0.0,2.0.0)");
        assertThat(bundle.getRequiredBundles().get(2).isOptional()).isTrue();
        assertThat(bundle.getRequiredBundles().get(3).isReexport()).isTrue();
    }

    @Test
    void testParseBundleClasspath() throws IOException {
        Path manifestFile = metaInfDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-SymbolicName: com.example.test
            Bundle-Version: 1.0.0
            Bundle-ClassPath: .,lib/commons.jar,lib/util.jar
            """);

        Bundle bundle = parser.parseBundle(bundleDir);

        assertThat(bundle.getEmbeddedLibraries()).containsExactly("lib/commons.jar", "lib/util.jar");
    }

    @Test
    void testParseExportPackage() throws IOException {
        Path manifestFile = metaInfDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-SymbolicName: com.example.test
            Bundle-Version: 1.0.0
            Export-Package: com.example.api,
             com.example.util;version="1.0.0",
             com.example.internal;x-internal:=true
            """);

        Bundle bundle = parser.parseBundle(bundleDir);

        assertThat(bundle.getExportedPackages())
            .containsExactly("com.example.api", "com.example.util", "com.example.internal");
    }

    @Test
    void testParseMissingSymbolicNameFallsBackToDirectoryName() throws IOException {
        Path manifestFile = metaInfDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-Version: 1.0.0
            """);

        Bundle bundle = parser.parseBundle(bundleDir);

        // Falls back to directory name when Bundle-SymbolicName is missing
        assertThat(bundle.getSymbolicName()).isEqualTo(bundleDir.getFileName().toString());
        assertThat(bundle.getVersion()).isEqualTo("1.0.0");
        assertThat(bundle.isStandaloneApp()).isFalse();
    }

    @Test
    void testParseMainClass() throws IOException {
        Path manifestFile = metaInfDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Bundle-Name: Test Command
            Bundle-Version: 1.0.0
            Main-Class: com.example.command.Main
            """);

        Bundle bundle = parser.parseBundle(bundleDir);

        assertThat(bundle.getSymbolicName()).isEqualTo(bundleDir.getFileName().toString());
        assertThat(bundle.getMainClass()).isEqualTo("com.example.command.Main");
        assertThat(bundle.isStandaloneApp()).isTrue();
    }

    @Test
    void testParseMissingManifest() {
        assertThatThrownBy(() -> parser.parseBundle(bundleDir))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("MANIFEST.MF not found");
    }
}
