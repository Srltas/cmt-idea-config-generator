package com.cubrid.tools.ideaconfig.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Eclipse Feature parsed from feature.xml.
 */
public class Feature {

    private final String id;
    private final String version;
    private final Path location;

    private String label;
    private String providerName;
    private String primaryPlugin;

    private final List<PluginReference> plugins = new ArrayList<>();
    private final List<FeatureReference> includedFeatures = new ArrayList<>();
    private final List<PluginImport> requiredPlugins = new ArrayList<>();

    public Feature(String id, String version, Path location) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.version = version != null ? version : "0.0.0";
        this.location = location;
    }

    // Basic properties

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public Path getLocation() {
        return location;
    }

    public String getLabel() {
        return label != null ? label : id;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getPrimaryPlugin() {
        return primaryPlugin;
    }

    public void setPrimaryPlugin(String primaryPlugin) {
        this.primaryPlugin = primaryPlugin;
    }

    // Plugins

    public List<PluginReference> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public void addPlugin(PluginReference plugin) {
        if (plugin != null) {
            plugins.add(plugin);
        }
    }

    // Included features

    public List<FeatureReference> getIncludedFeatures() {
        return Collections.unmodifiableList(includedFeatures);
    }

    public void addIncludedFeature(FeatureReference feature) {
        if (feature != null) {
            includedFeatures.add(feature);
        }
    }

    // Required plugins

    public List<PluginImport> getRequiredPlugins() {
        return Collections.unmodifiableList(requiredPlugins);
    }

    public void addRequiredPlugin(PluginImport plugin) {
        if (plugin != null) {
            requiredPlugins.add(plugin);
        }
    }

    /**
     * Get all plugin IDs referenced by this feature.
     */
    public List<String> getAllPluginIds() {
        List<String> ids = new ArrayList<>();
        for (PluginReference pr : plugins) {
            ids.add(pr.getId());
        }
        return ids;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Feature feature = (Feature) o;
        return id.equals(feature.id) && version.equals(feature.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }

    @Override
    public String toString() {
        return "Feature{" +
                "id='" + id + '\'' +
                ", version='" + version + '\'' +
                ", plugins=" + plugins.size() +
                ", includedFeatures=" + includedFeatures.size() +
                '}';
    }

    /**
     * Reference to a plugin in a feature.
     */
    public static class PluginReference {
        private final String id;
        private String version;
        private boolean unpack;
        private boolean fragment;

        public PluginReference(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public boolean isUnpack() {
            return unpack;
        }

        public void setUnpack(boolean unpack) {
            this.unpack = unpack;
        }

        public boolean isFragment() {
            return fragment;
        }

        public void setFragment(boolean fragment) {
            this.fragment = fragment;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /**
     * Reference to an included feature.
     */
    public static class FeatureReference {
        private final String id;
        private String version;
        private boolean optional;

        public FeatureReference(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public boolean isOptional() {
            return optional;
        }

        public void setOptional(boolean optional) {
            this.optional = optional;
        }

        @Override
        public String toString() {
            return id + (optional ? " (optional)" : "");
        }
    }

    /**
     * Import of a required plugin.
     */
    public static class PluginImport {
        private final String id;
        private String version;
        private String match; // compatible, equivalent, greaterOrEqual, perfect

        public PluginImport(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getMatch() {
            return match;
        }

        public void setMatch(String match) {
            this.match = match;
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
