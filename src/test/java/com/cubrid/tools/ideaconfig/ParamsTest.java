package com.cubrid.tools.ideaconfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ParamsTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseRequiredArgs() {
        String[] args = {
            "-c", "/path/to/config.properties",
            "-p", "/path/to/projects",
            "-o", "/path/to/output"
        };

        Params params = Params.parse(args);

        assertThat(params).isNotNull();
        assertThat(params.getConfigFile()).isEqualTo(Path.of("/path/to/config.properties"));
        assertThat(params.getProjectsFolder()).isEqualTo(Path.of("/path/to/projects"));
        assertThat(params.getOutputDir()).isEqualTo(Path.of("/path/to/output"));
    }

    @Test
    void testParseWithOptionalArgs() {
        String[] args = {
            "--config", "/path/to/config.properties",
            "--projects-folder", "/path/to/projects",
            "--output", "/path/to/output",
            "--eclipse", "/path/to/eclipse-deps",
            "--debug",
            "--dry-run"
        };

        Params params = Params.parse(args);

        assertThat(params).isNotNull();
        assertThat(params.getEclipseDepsDir()).isEqualTo(Path.of("/path/to/eclipse-deps"));
        assertThat(params.isDebug()).isTrue();
        assertThat(params.isDryRun()).isTrue();
    }

    @Test
    void testDefaultEclipseDepsDir() {
        String[] args = {
            "-c", "/path/to/config.properties",
            "-p", "/path/to/projects",
            "-o", "/path/to/output"
        };

        Params params = Params.parse(args);

        assertThat(params).isNotNull();
        // Default should be <projects-folder>/../workspace/dependencies
        assertThat(params.getEclipseDepsDir())
            .isEqualTo(Path.of("/path/to/workspace/dependencies"));
    }

    @Test
    void testParseHelp() {
        String[] args = {"--help"};

        Params params = Params.parse(args);

        // When help is requested, parse returns null
        assertThat(params).isNull();
    }

    @Test
    void testParseMissingRequired() {
        String[] args = {"-c", "/path/to/config.properties"};

        Params params = Params.parse(args);

        // When required args are missing, parse returns null
        assertThat(params).isNull();
    }
}
