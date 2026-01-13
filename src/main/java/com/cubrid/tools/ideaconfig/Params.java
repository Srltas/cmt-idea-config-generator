package com.cubrid.tools.ideaconfig;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command line parameters for the CMT IDEA Config Generator.
 * Uses PicoCLI for argument parsing.
 */
@Command(
    name = "cmt-idea-config-generator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Generates IntelliJ IDEA configuration files for CUBRID Migration Toolkit (Eclipse RCP project)"
)
public class Params implements Callable<Integer> {

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
        names = {"--update-workspace"},
        description = "Update existing workspace.xml if present",
        defaultValue = "false"
    )
    private boolean updateWorkspace;

    @Option(
        names = {"--single-core"},
        description = "Run in single-threaded mode (for debugging)",
        defaultValue = "false"
    )
    private boolean singleCoreMode;

    @Option(
        names = {"--no-tree"},
        description = "Skip dependency tree generation",
        defaultValue = "false"
    )
    private boolean noTree;

    @Option(
        names = {"-d", "--debug"},
        description = "Enable debug logging",
        defaultValue = "false"
    )
    private boolean debug;

    @Option(
        names = {"--dry-run"},
        description = "Analyze and report without generating files",
        defaultValue = "false"
    )
    private boolean dryRun;

    // Callback to the actual generator
    private Runnable generateAction;

    public void setGenerateAction(Runnable action) {
        this.generateAction = action;
    }

    @Override
    public Integer call() {
        if (generateAction != null) {
            generateAction.run();
        }
        return 0;
    }

    // Getters

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
            // Fallback if no parent (root directory)
            parent = absProjectsFolder;
        }
        return parent.resolve("workspace").resolve("dependencies");
    }

    public boolean isUpdateWorkspace() {
        return updateWorkspace;
    }

    public boolean isSingleCoreMode() {
        return singleCoreMode;
    }

    public boolean isNoTree() {
        return noTree;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Parse command line arguments and return Params instance.
     *
     * @param args command line arguments
     * @return parsed Params or null if parsing failed
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
            System.err.println(ex.getMessage());
            cmd.usage(System.err);
            return null;
        }
    }

    /**
     * Execute with the given arguments.
     *
     * @param args command line arguments
     * @return exit code
     */
    public static int execute(String[] args) {
        return new CommandLine(new Params()).execute(args);
    }

    @Override
    public String toString() {
        return "Params{" +
                "configFile=" + configFile +
                ", projectsFolder=" + projectsFolder +
                ", outputDir=" + outputDir +
                ", eclipseDepsDir=" + getEclipseDepsDir() +
                ", updateWorkspace=" + updateWorkspace +
                ", singleCoreMode=" + singleCoreMode +
                ", noTree=" + noTree +
                ", debug=" + debug +
                ", dryRun=" + dryRun +
                '}';
    }
}
