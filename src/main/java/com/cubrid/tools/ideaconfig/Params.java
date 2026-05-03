package com.cubrid.tools.ideaconfig;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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

    @Option(
        names = {"-c", "--config"},
        description = "Path to the configuration properties file (e.g., osgi-app.properties)",
        required = true
    )
    private Path configFile;

    @Option(
        names = {"-p", "--projects-folder"},
        description = "Root folder containing the Eclipse RCP projects",
        required = true
    )
    private Path projectsFolder;

    @Option(
        names = {"-o", "--output"},
        description = "Output directory for generated IDEA configuration",
        required = true
    )
    private Path outputDir;

    @Option(
        names = {"-e", "--eclipse"},
        description = "Eclipse dependencies folder (default: <projects-folder>/../workspace/dependencies)"
    )
    private Path eclipseDepsDir;

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

    public Path getConfigFile() {
        return configFile;
    }

    public Path getProjectsFolder() {
        return projectsFolder;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public Path getEclipseDepsDir() {
        if (eclipseDepsDir != null) {
            return eclipseDepsDir.toAbsolutePath().normalize();
        }
        // Default: <projects-folder>/../workspace/dependencies
        Path absProjectsFolder = projectsFolder.toAbsolutePath().normalize();
        Path parent = absProjectsFolder.getParent();
        if (parent == null) {
            parent = absProjectsFolder;
        }
        return parent.resolve("workspace").resolve("dependencies");
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
                "configFile=" + configFile +
                ", projectsFolder=" + projectsFolder +
                ", outputDir=" + outputDir +
                ", eclipseDepsDir=" + getEclipseDepsDir() +
                ", debug=" + debug +
                ", dryRun=" + dryRun +
                '}';
    }
}
