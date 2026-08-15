package com.cubrid.tools.ideaconfig;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ParamsTest {

    @Test
    void takesTheProjectFolderAsThePositionalArgument() {
        Params params = Params.parse(new String[] {"/path/to/projects"});

        assertThat(params).isNotNull();
        assertThat(params.getProjectsFolder()).isEqualTo(Path.of("/path/to/projects"));
    }

    @Test
    void defaultsOutputNextToTheProjectFolder() {
        Params params = Params.parse(new String[] {"/path/to/projects"});

        assertThat(params).isNotNull();
        assertThat(params.getOutputDir()).isEqualTo(Path.of("/path/to"));
    }

    @Test
    void defaultsTheBundleFolderBesideTheProject() {
        Params params = Params.parse(new String[] {"/path/to/projects"});

        assertThat(params).isNotNull();
        assertThat(params.getEclipseDepsDir())
                .isEqualTo(Path.of("/path/to/workspace/dependencies"));
    }

    @Test
    void defaultsTheMavenRepoToTheUserHome() {
        Params params = Params.parse(new String[] {"/path/to/projects"});

        assertThat(params).isNotNull();
        assertThat(params.getMavenRepo())
                .isEqualTo(Path.of(System.getProperty("user.home"), ".m2", "repository"));
    }

    @Test
    void acceptsExplicitOverrides() {
        String[] args = {
            "/path/to/projects",
            "--output", "/path/to/output",
            "--maven-repo", "/opt/m2",
            "--debug",
            "--dry-run"
        };

        Params params = Params.parse(args);

        assertThat(params).isNotNull();
        assertThat(params.getOutputDir()).isEqualTo(Path.of("/path/to/output"));
        assertThat(params.getMavenRepo()).isEqualTo(Path.of("/opt/m2"));
        assertThat(params.isDebug()).isTrue();
        assertThat(params.isDryRun()).isTrue();
    }

    @Test
    void returnsNullForHelp() {
        assertThat(Params.parse(new String[] {"--help"})).isNull();
    }

    @Test
    void returnsNullWhenTheProjectFolderIsMissing() {
        assertThat(Params.parse(new String[] {"--debug"})).isNull();
    }
}
