package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.TestModule;
import com.cubrid.tools.ideaconfig.model.TestModule.SourceFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestModuleParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesSourceFoldersAndLocalDependencies() throws Exception {
        Path moduleDir = tempDir.resolve("my-test-module");
        Files.createDirectories(moduleDir.resolve("src/test/java"));
        Files.createDirectories(moduleDir.resolve("src/test/resources"));
        Files.createDirectories(moduleDir.resolve("src/main/java"));

        Files.writeString(moduleDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>my-test-module</artifactId>
              <version>1.0-SNAPSHOT</version>
              <dependencies>
                <dependency>
                  <groupId>com.cubrid.cubridmigration</groupId>
                  <artifactId>com.cubrid.cubridmigration.core</artifactId>
                  <version>1.0.0-SNAPSHOT</version>
                </dependency>
                <dependency>
                  <groupId>com.cubrid.cubridmigration</groupId>
                  <artifactId>not.a.local.bundle</artifactId>
                  <version>1.0.0-SNAPSHOT</version>
                </dependency>
                <dependency>
                  <groupId>org.example</groupId>
                  <artifactId>some-external</artifactId>
                  <version>1.0.0</version>
                </dependency>
              </dependencies>
            </project>
            """);

        TestModuleParser parser = new TestModuleParser(
            "com.cubrid.cubridmigration",
            tempDir.resolve("nonexistent-m2") // disable .m2 fallback for this test
        );
        TestModule module = parser.parse(moduleDir, Set.of("com.cubrid.cubridmigration.core"));

        assertThat(module).isNotNull();
        assertThat(module.getName()).isEqualTo("my-test-module");
        assertThat(module.getSourceFolders())
            .extracting(SourceFolder::kind)
            .containsExactlyInAnyOrder(
                SourceFolder.Kind.MAIN_JAVA,
                SourceFolder.Kind.TEST_JAVA,
                SourceFolder.Kind.TEST_RESOURCES);
        assertThat(module.getLocalModuleDependencies())
            .containsExactly("com.cubrid.cubridmigration.core");
    }

    @Test
    void resolvesExternalLibrariesViaLocalRepoFallback() throws Exception {
        Path moduleDir = tempDir.resolve("test-module");
        Files.createDirectories(moduleDir.resolve("src/test/java"));

        // Build a fake ~/.m2 with junit-jupiter-api-5.11.0.jar present.
        Path m2 = tempDir.resolve(".m2/repository");
        Path junitJar = m2.resolve("org/junit/jupiter/junit-jupiter-api/5.11.0/junit-jupiter-api-5.11.0.jar");
        Files.createDirectories(junitJar.getParent());
        Files.writeString(junitJar, "fake jar");

        Files.writeString(moduleDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>test-module</artifactId>
              <version>1.0-SNAPSHOT</version>
              <properties>
                <junit.version>5.11.0</junit.version>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-api</artifactId>
                  <version>${junit.version}</version>
                </dependency>
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>missing-from-cache</artifactId>
                  <version>1.0.0</version>
                </dependency>
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>no-version</artifactId>
                </dependency>
              </dependencies>
            </project>
            """);

        // Force the .m2 fallback by giving an unreachable groupPrefix that matches no deps,
        // and by relying on the fact that mvn will fail (no executable for this fake pom).
        TestModuleParser parser = new TestModuleParser("zzz.no.local", m2);
        TestModule module = parser.parse(moduleDir, Set.of());

        assertThat(module).isNotNull();
        // Only the in-cache dep should be picked up.
        assertThat(module.getExternalLibraries()).hasSize(1);
        assertThat(module.getExternalLibraries().get(0)).isEqualTo(junitJar);
    }

    @Test
    void returnsNullWhenPomMissing() throws Exception {
        Path moduleDir = tempDir.resolve("no-pom");
        Files.createDirectories(moduleDir);

        TestModuleParser parser = new TestModuleParser(
            "com.cubrid.cubridmigration",
            tempDir.resolve("nonexistent-m2")
        );
        assertThat(parser.parse(moduleDir, Set.of())).isNull();
    }
}
