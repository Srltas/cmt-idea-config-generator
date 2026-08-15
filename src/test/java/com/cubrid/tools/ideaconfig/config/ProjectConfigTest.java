package com.cubrid.tools.ideaconfig.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectConfigTest {

    @TempDir
    Path projectDir;

    @BeforeEach
    void createRcpLayout() throws IOException {
        Files.createDirectories(projectDir.resolve("plugins/com.example.app/META-INF"));
        Files.createDirectories(projectDir.resolve("features/com.example.feature"));
        Files.createDirectories(projectDir.resolve("product/com.example.desktop"));
        Files.writeString(projectDir.resolve("product/com.example.desktop/example.product"), "<product/>");
    }

    @Test
    void discoversTheStandardLayout() throws IOException {
        ProjectConfig config = ProjectConfig.discover(projectDir);

        assertThat(config.getBundlesPaths()).containsExactly("plugins");
        assertThat(config.getFeaturesPaths()).containsExactly("features");
        assertThat(config.getProductsPaths())
                .containsExactly("product/com.example.desktop/example.product");
        assertThat(config.getWorkspaceName()).isEqualTo("cubrid-migration-idea");
    }

    @Test
    void discoversMavenTestModulesOnly() throws IOException {
        Files.createDirectories(projectDir.resolve("tests/unit-test/src/test/java"));
        Files.writeString(projectDir.resolve("tests/unit-test/pom.xml"), "<project/>");
        Files.createDirectories(projectDir.resolve("tests/e2e"));
        Files.writeString(projectDir.resolve("tests/e2e/pom.xml"), "<project/>");
        // No pom.xml, so not a Maven module.
        Files.createDirectories(projectDir.resolve("tests/fixtures"));

        ProjectConfig config = ProjectConfig.discover(projectDir);

        assertThat(config.getTestModuleRoots()).containsExactly("tests/e2e", "tests/unit-test");
    }

    @Test
    void ignoresProductFilesUnderBuildOutput() throws IOException {
        Files.createDirectories(projectDir.resolve("product/target"));
        Files.writeString(projectDir.resolve("product/target/copied.product"), "<product/>");

        ProjectConfig config = ProjectConfig.discover(projectDir);

        assertThat(config.getProductsPaths())
                .containsExactly("product/com.example.desktop/example.product");
    }

    @Test
    void toleratesMissingOptionalDirectories() throws IOException {
        ProjectConfig config = ProjectConfig.discover(projectDir);

        assertThat(config.getTestModuleRoots()).isEmpty();
    }

    @Test
    void rejectsAFolderWithoutBundles(@TempDir Path empty) throws IOException {
        ProjectConfig config = ProjectConfig.discover(empty);

        assertThatThrownBy(config::validate)
                .isInstanceOf(ProjectConfig.ConfigurationException.class)
                .hasMessageContaining("plugins");
    }

    @Test
    void rejectsAFolderWithoutAProduct(@TempDir Path noProduct) throws IOException {
        Files.createDirectories(noProduct.resolve("plugins"));

        ProjectConfig config = ProjectConfig.discover(noProduct);

        assertThatThrownBy(config::validate)
                .isInstanceOf(ProjectConfig.ConfigurationException.class)
                .hasMessageContaining("product");
    }
}
