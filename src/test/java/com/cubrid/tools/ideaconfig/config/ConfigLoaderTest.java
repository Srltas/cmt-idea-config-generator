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
            repositories=https://example.com/p2/
            """);

        ProjectConfig config = loader.load(configFile);

        assertThat(config.getWorkspaceName()).isEqualTo("my-workspace");
        assertThat(config.getBundlesPaths()).containsExactly("plugins");
        assertThat(config.getProductsPaths()).containsExactly("product/my.product");
        assertThat(config.getRepositories()).containsExactly("https://example.com/p2/");
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
            repositories=https://example.com/p2/
            """);

        ProjectConfig config = loader.load(configFile);

        assertThat(config.getBundlesPaths())
            .containsExactly("plugins/core", "plugins/ui", "plugins/app");
    }

    @Test
    void testLoadWithVariableSubstitution() throws Exception {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, """
            workspaceName=my-workspace
            bundlesPaths=plugins
            productsPaths=product/my.product
            repositories=https://download.eclipse.org/releases/${eclipse-version}/
            """);

        loader.addVariable("eclipse-version", "2025-03");
        ProjectConfig config = loader.load(configFile);

        assertThat(config.getRepositories())
            .containsExactly("https://download.eclipse.org/releases/2025-03/");
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
            repositories=https://example.com/p2/
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
    void testVariableSubstitutionWithAddVariable() throws Exception {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, """
            workspaceName=my-workspace
            bundlesPaths=plugins
            productsPaths=product/my.product
            repositories=https://download.eclipse.org/releases/${my-var}/
            """);

        ConfigLoader loaderWithVar = new ConfigLoader();
        loaderWithVar.addVariable("my-var", "test-value");
        ProjectConfig config = loaderWithVar.load(configFile);

        assertThat(config.getRepositories())
            .containsExactly("https://download.eclipse.org/releases/test-value/");
    }

    @Test
    void testLoadAllConfigProperties() throws Exception {
        Path configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, """
            workspaceName=full-test
            featuresPaths=features/main;features/optional
            bundlesPaths=plugins
            repositories=https://example.com/p2/
            productsPaths=product/my.product
            testBundlePaths=test/unit;test/integration
            testLibraries=org.junit;org.mockito
            ideaConfigurationFilesPaths=.idea
            additionalModuleRoots=modules/extra
            excludeOutputs=target;bin
            optionalFeatureRepositories=features/optional
            """);

        ProjectConfig config = loader.load(configFile);

        assertThat(config.getWorkspaceName()).isEqualTo("full-test");
        assertThat(config.getFeaturesPaths()).containsExactly("features/main", "features/optional");
        assertThat(config.getTestBundlePaths()).containsExactly("test/unit", "test/integration");
        assertThat(config.getTestLibraries()).containsExactly("org.junit", "org.mockito");
        assertThat(config.getIdeaConfigurationFilesPaths()).containsExactly(".idea");
        assertThat(config.getAdditionalModuleRoots()).containsExactly("modules/extra");
        assertThat(config.getExcludeOutputs()).containsExactly("target", "bin");
    }
}
