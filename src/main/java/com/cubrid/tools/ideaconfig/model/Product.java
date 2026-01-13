package com.cubrid.tools.ideaconfig.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Eclipse Product parsed from .product file.
 */
public class Product {

    private final String uid;
    private final String id;
    private final Path location;

    private String name;
    private String version;
    private String application;
    private boolean useFeatures;
    private boolean includeLaunchers;

    private String splashLocation;
    private String launcherName;

    // VM arguments
    private String vmArgs;
    private String vmArgsMac;
    private String vmArgsWin;
    private String vmArgsLinux;

    // Program arguments
    private String programArgs;
    private String programArgsMac;
    private String programArgsWin;
    private String programArgsLinux;

    // Features and plugins
    private final List<String> featureIds = new ArrayList<>();
    private final List<String> pluginIds = new ArrayList<>();
    private final List<PluginConfiguration> pluginConfigurations = new ArrayList<>();

    public Product(String uid, String id, Path location) {
        this.uid = uid != null ? uid : id;
        this.id = Objects.requireNonNull(id, "id is required");
        this.location = location;
    }

    // Basic properties

    public String getUid() {
        return uid;
    }

    public String getId() {
        return id;
    }

    public Path getLocation() {
        return location;
    }

    public String getName() {
        return name != null ? name : id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public boolean isUseFeatures() {
        return useFeatures;
    }

    public void setUseFeatures(boolean useFeatures) {
        this.useFeatures = useFeatures;
    }

    public boolean isIncludeLaunchers() {
        return includeLaunchers;
    }

    public void setIncludeLaunchers(boolean includeLaunchers) {
        this.includeLaunchers = includeLaunchers;
    }

    // Splash and launcher

    public String getSplashLocation() {
        return splashLocation;
    }

    public void setSplashLocation(String splashLocation) {
        this.splashLocation = splashLocation;
    }

    public String getLauncherName() {
        return launcherName;
    }

    public void setLauncherName(String launcherName) {
        this.launcherName = launcherName;
    }

    // VM arguments

    public String getVmArgs() {
        return vmArgs;
    }

    public void setVmArgs(String vmArgs) {
        this.vmArgs = vmArgs;
    }

    public String getVmArgsMac() {
        return vmArgsMac;
    }

    public void setVmArgsMac(String vmArgsMac) {
        this.vmArgsMac = vmArgsMac;
    }

    public String getVmArgsWin() {
        return vmArgsWin;
    }

    public void setVmArgsWin(String vmArgsWin) {
        this.vmArgsWin = vmArgsWin;
    }

    public String getVmArgsLinux() {
        return vmArgsLinux;
    }

    public void setVmArgsLinux(String vmArgsLinux) {
        this.vmArgsLinux = vmArgsLinux;
    }

    // Program arguments

    public String getProgramArgs() {
        return programArgs;
    }

    public void setProgramArgs(String programArgs) {
        this.programArgs = programArgs;
    }

    public String getProgramArgsMac() {
        return programArgsMac;
    }

    public void setProgramArgsMac(String programArgsMac) {
        this.programArgsMac = programArgsMac;
    }

    public String getProgramArgsWin() {
        return programArgsWin;
    }

    public void setProgramArgsWin(String programArgsWin) {
        this.programArgsWin = programArgsWin;
    }

    public String getProgramArgsLinux() {
        return programArgsLinux;
    }

    public void setProgramArgsLinux(String programArgsLinux) {
        this.programArgsLinux = programArgsLinux;
    }

    // Features

    public List<String> getFeatureIds() {
        return Collections.unmodifiableList(featureIds);
    }

    public void addFeatureId(String featureId) {
        if (featureId != null && !featureId.isBlank()) {
            featureIds.add(featureId.trim());
        }
    }

    // Plugins

    public List<String> getPluginIds() {
        return Collections.unmodifiableList(pluginIds);
    }

    public void addPluginId(String pluginId) {
        if (pluginId != null && !pluginId.isBlank()) {
            pluginIds.add(pluginId.trim());
        }
    }

    // Plugin configurations

    public List<PluginConfiguration> getPluginConfigurations() {
        return Collections.unmodifiableList(pluginConfigurations);
    }

    public void addPluginConfiguration(PluginConfiguration config) {
        if (config != null) {
            pluginConfigurations.add(config);
        }
    }

    /**
     * Get combined VM arguments for a platform.
     */
    public String getCombinedVmArgs(String platform) {
        StringBuilder sb = new StringBuilder();
        if (vmArgs != null) {
            sb.append(vmArgs);
        }

        String platformArgs = switch (platform.toLowerCase()) {
            case "mac", "macos", "macosx" -> vmArgsMac;
            case "win", "windows" -> vmArgsWin;
            case "linux" -> vmArgsLinux;
            default -> null;
        };

        if (platformArgs != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(platformArgs);
        }

        return sb.toString().trim();
    }

    /**
     * Get combined program arguments for a platform.
     */
    public String getCombinedProgramArgs(String platform) {
        StringBuilder sb = new StringBuilder();
        if (programArgs != null) {
            sb.append(programArgs);
        }

        String platformArgs = switch (platform.toLowerCase()) {
            case "mac", "macos", "macosx" -> programArgsMac;
            case "win", "windows" -> programArgsWin;
            case "linux" -> programArgsLinux;
            default -> null;
        };

        if (platformArgs != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(platformArgs);
        }

        return sb.toString().trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return uid.equals(product.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }

    @Override
    public String toString() {
        return "Product{" +
                "uid='" + uid + '\'' +
                ", name='" + name + '\'' +
                ", application='" + application + '\'' +
                ", features=" + featureIds.size() +
                ", plugins=" + pluginIds.size() +
                '}';
    }

    /**
     * Plugin start configuration.
     */
    public static class PluginConfiguration {
        private final String pluginId;
        private boolean autoStart;
        private int startLevel = -1;

        public PluginConfiguration(String pluginId) {
            this.pluginId = Objects.requireNonNull(pluginId);
        }

        public String getPluginId() {
            return pluginId;
        }

        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }

        public int getStartLevel() {
            return startLevel;
        }

        public void setStartLevel(int startLevel) {
            this.startLevel = startLevel;
        }

        @Override
        public String toString() {
            return pluginId + "@" + startLevel + (autoStart ? ":start" : "");
        }
    }
}
