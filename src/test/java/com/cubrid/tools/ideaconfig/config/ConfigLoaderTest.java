package com.cubrid.tools.ideaconfig.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private ConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ConfigLoader();
    }

    @Test
    void testLoadSimpleConfig() throws Exception {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, """
            workspaceName=my-workspace
            bundlesPaths=plugins
            productsPaths=product/my.product
            """);

        ProjectConfig config = loader.load(configFile);

        assertThat(config.getWorkspaceName()).isEqualTo("my-workspace");
        assertThat(config.getBundlesPaths()).containsExactly("plugins");
        assertThat(config.getProductsPaths()).containsExactly("product/my.product");
    }

    @Test
    void testLoadMultiLineContinuation() throws Exception {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, """
            workspaceName=my-workspace
            bundlesPaths=\\
              plugins/core;\\
              plugins/ui;\\
              plugins/app
            productsPaths=product/my.product
            """);

        ProjectConfig config = loader.load(configFile);

        assertThat(config.getBundlesPaths())
            .containsExactly("plugins/core", "plugins/ui", "plugins/app");
    }

    @Test
    void testLoadWithComments() throws Exception {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, """
            # This is a comment
            workspaceName=my-workspace

            # Another comment
            bundlesPaths=plugins
            productsPaths=product/my.product
            """);

        ProjectConfig config = loader.load(configFile);

        assertThat(config.getWorkspaceName()).isEqualTo("my-workspace");
    }

    @Test
    void testLoadNonExistentFile() {
        Path configFile = tempDir.resolve("nonexistent.properties");

        assertThatThrownBy(() -> loader.load(configFile))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void testLoadAllSupportedProperties() throws Exception {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, """
            workspaceName=full-test
            featuresPaths=features/main;features/optional
            bundlesPaths=plugins
            productsPaths=product/my.product
            additionalModuleRoots=plugins/standalone-app
            testModuleRoots=tests/unit-test;tests/e2e
            """);

        ProjectConfig config = loader.load(configFile);

        assertThat(config.getWorkspaceName()).isEqualTo("full-test");
        assertThat(config.getFeaturesPaths()).containsExactly("features/main", "features/optional");
        assertThat(config.getBundlesPaths()).containsExactly("plugins");
        assertThat(config.getAdditionalModuleRoots()).containsExactly("plugins/standalone-app");
        assertThat(config.getTestModuleRoots()).containsExactly("tests/unit-test", "tests/e2e");
    }

    @Test
    void testValidateMissingWorkspaceName() {
        ProjectConfig config = new ProjectConfig();
        config.addBundlesPath("plugins");
        config.addProductsPath("product/my.product");

        assertThatThrownBy(config::validate)
            .isInstanceOf(ProjectConfig.ConfigurationException.class)
            .hasMessageContaining("workspaceName");
    }
}
