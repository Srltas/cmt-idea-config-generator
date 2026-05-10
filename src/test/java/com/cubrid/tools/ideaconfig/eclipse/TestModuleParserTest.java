package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.TestModule;
import com.cubrid.tools.ideaconfig.model.TestModule.SourceFolder;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests skip the real {@code mvn dependency:build-classpath} call by overriding
 * {@link TestModuleParser#resolveExternalLibraries} — exercising it would make tests slow,
 * order-dependent on the local Maven cache, and dependent on a {@code mvn} executable being
 * on PATH. End-to-end coverage of that path is provided by running the generator against
 * the real cubrid-migration tree.
 */

class TestModuleParserTest {

    @TempDir
    Path tempDir;

    private static TestModuleParser parserWithoutMaven(String localGroupPrefix) {
        return new TestModuleParser(localGroupPrefix) {
            @Override
            protected void resolveExternalLibraries(Path pomFile, Document pomDoc, TestModule module) {
                // no-op for unit tests
            }
        };
    }

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

        TestModuleParser parser = parserWithoutMaven("com.cubrid.cubridmigration");
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
    void writeStubPomStripsLocalGroupDepsAndPreservesEverythingElse() throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>stub-test</artifactId>
              <version>1.0-SNAPSHOT</version>
              <properties>
                <junit.version>5.11.0</junit.version>
              </properties>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.junit</groupId>
                    <artifactId>junit-bom</artifactId>
                    <version>${junit.version}</version>
                    <type>pom</type>
                    <scope>import</scope>
                  </dependency>
                </dependencies>
              </dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>com.cubrid.cubridmigration</groupId>
                  <artifactId>com.cubrid.cubridmigration.core</artifactId>
                  <version>1.0.0-SNAPSHOT</version>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter</artifactId>
                </dependency>
                <dependency>
                  <groupId>org.assertj</groupId>
                  <artifactId>assertj-core</artifactId>
                  <version>3.27.7</version>
                </dependency>
              </dependencies>
            </project>
            """);

        Document pomDoc = XmlHelper.parseFile(pomFile);
        TestModuleParser parser = new TestModuleParser("com.cubrid.cubridmigration");

        Path stubPom = parser.writeStubPom(pomFile, pomDoc);
        try {
            assertThat(stubPom).exists();
            assertThat(stubPom.getParent()).isEqualTo(tempDir);

            Document stubDoc = XmlHelper.parseFile(stubPom);
            Element project = stubDoc.getDocumentElement();

            List<Element> deps = XmlHelper.getChildElement(project, "dependencies")
                .map(d -> XmlHelper.getChildElements(d, "dependency"))
                .orElseThrow();
            assertThat(deps).extracting(d -> XmlHelper.getChildText(d, "artifactId"))
                .containsExactly("junit-jupiter", "assertj-core");

            // dependencyManagement (BOM import) must survive — it's the only way Maven can
            // resolve the version-less junit-jupiter dep above.
            List<Element> managed = XmlHelper.getChildElement(project, "dependencyManagement")
                .flatMap(dm -> XmlHelper.getChildElement(dm, "dependencies"))
                .map(d -> XmlHelper.getChildElements(d, "dependency"))
                .orElseThrow();
            assertThat(managed).hasSize(1);
            assertThat(XmlHelper.getChildText(managed.get(0), "artifactId")).isEqualTo("junit-bom");

            // Original pom must be untouched.
            assertThat(Files.readString(pomFile)).contains("com.cubrid.cubridmigration.core");
        } finally {
            Files.deleteIfExists(stubPom);
        }
    }

    @Test
    void returnsNullWhenPomMissing() throws Exception {
        Path moduleDir = tempDir.resolve("no-pom");
        Files.createDirectories(moduleDir);

        TestModuleParser parser = parserWithoutMaven("com.cubrid.cubridmigration");
        assertThat(parser.parse(moduleDir, Set.of())).isNull();
    }
}
