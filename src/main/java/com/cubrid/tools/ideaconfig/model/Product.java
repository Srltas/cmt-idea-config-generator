package com.cubrid.tools.ideaconfig.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Eclipse Product parsed from .product file.
 */
public class Product {

    private final String id;

    private String name;
    private String application;

    private String vmArgs;
    private String vmArgsMac;
    private String vmArgsWin;
    private String vmArgsLinux;

    private String programArgs;

    private final List<String> featureIds = new ArrayList<>();
    private final List<String> pluginIds = new ArrayList<>();

    public Product(String id) {
        this.id = Objects.requireNonNull(id, "id is required");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null ? name : id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public void setVmArgs(String vmArgs) {
        this.vmArgs = vmArgs;
    }

    public void setVmArgsMac(String vmArgsMac) {
        this.vmArgsMac = vmArgsMac;
    }

    public void setVmArgsWin(String vmArgsWin) {
        this.vmArgsWin = vmArgsWin;
    }

    public void setVmArgsLinux(String vmArgsLinux) {
        this.vmArgsLinux = vmArgsLinux;
    }

    public String getProgramArgs() {
        return programArgs;
    }

    public void setProgramArgs(String programArgs) {
        this.programArgs = programArgs;
    }

    public List<String> getFeatureIds() {
        return Collections.unmodifiableList(featureIds);
    }

    public void addFeatureId(String featureId) {
        if (featureId != null && !featureId.isBlank()) {
            featureIds.add(featureId.trim());
        }
    }

    public List<String> getPluginIds() {
        return Collections.unmodifiableList(pluginIds);
    }

    public void addPluginId(String pluginId) {
        if (pluginId != null && !pluginId.isBlank()) {
            pluginIds.add(pluginId.trim());
        }
    }

    /**
     * Get combined VM arguments (cross-platform args + platform-specific args).
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Product{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", application='" + application + '\'' +
                ", features=" + featureIds.size() +
                ", plugins=" + pluginIds.size() +
                '}';
    }
}
