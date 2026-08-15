package com.cubrid.tools.ideaconfig;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

/**
 * Command line parameters for the CMT IDEA Config Generator.
 */
@Command(
    name = "cmt-idea-config-generator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Generates IntelliJ IDEA configuration files for CUBRID Migration Toolkit (Eclipse RCP project)"
)
public class Params {

    @Parameters(
        index = "0",
        paramLabel = "<project-dir>",
        description = "Root folder of the Eclipse RCP project"
    )
    private Path projectsFolder;

    @Option(
        names = {"-o", "--output"},
        description = "Where to write the IDEA project (default: next to <project-dir>)"
    )
    private Path outputDir;

    @Option(
        names = {"-m", "--maven-repo"},
        description = "Local Maven repository holding Tycho's p2 cache (default: <user.home>/.m2/repository)"
    )
    private Path mavenRepo;

    @Option(
        names = {"-d", "--debug"},
        description = "Enable debug logging",
        defaultValue = "false"
    )
    private boolean debug;

    @Option(
        names = {"-n", "--dry-run"},
        description = "Analyze and report without generating files",
        defaultValue = "false"
    )
    private boolean dryRun;

    public Path getProjectsFolder() {
        return projectsFolder.toAbsolutePath().normalize();
    }

    /** Defaults to the parent of the project folder, so the IDEA project sits beside it. */
    public Path getOutputDir() {
        if (outputDir != null) {
            return outputDir.toAbsolutePath().normalize();
        }
        return parentOf(getProjectsFolder());
    }

    public Path getMavenRepo() {
        if (mavenRepo != null) {
            return mavenRepo.toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    /** Bundle folder filled from the p2 cache: {@code <project-dir>/../workspace/dependencies}. */
    public Path getEclipseDepsDir() {
        return parentOf(getProjectsFolder()).resolve("workspace").resolve("dependencies");
    }

    private static Path parentOf(Path path) {
        Path parent = path.getParent();
        return parent != null ? parent : path;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Parse command line arguments.
     *
     * @return parsed Params, or null if parsing failed or help/version was requested
     */
    public static Params parse(String[] args) {
        Params params = new Params();
        CommandLine cmd = new CommandLine(params);

        try {
            CommandLine.ParseResult result = cmd.parseArgs(args);

            if (result.isUsageHelpRequested()) {
                cmd.usage(System.out);
                return null;
            }
            if (result.isVersionHelpRequested()) {
                cmd.printVersionHelp(System.out);
                return null;
            }
            return params;
        } catch (CommandLine.ParameterException ex) {
            System.err.println("Error: " + ex.getMessage());
            cmd.usage(System.err);
            return null;
        }
    }

    @Override
    public String toString() {
        return "Params{" +
                "projectsFolder=" + getProjectsFolder() +
                ", outputDir=" + getOutputDir() +
                ", eclipseDepsDir=" + getEclipseDepsDir() +
                ", mavenRepo=" + getMavenRepo() +
                ", debug=" + debug +
                ", dryRun=" + dryRun +
                '}';
    }
}
