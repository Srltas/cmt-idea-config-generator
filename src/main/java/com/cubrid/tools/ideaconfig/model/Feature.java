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

    private final List<PluginReference> plugins = new ArrayList<>();
    private final List<FeatureReference> includedFeatures = new ArrayList<>();
    private final List<PluginImport> requiredPlugins = new ArrayList<>();

    public Feature(String id) {
        this.id = Objects.requireNonNull(id, "id is required");
    }

    public String getId() {
        return id;
    }

    public List<PluginReference> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    public void addPlugin(PluginReference plugin) {
        if (plugin != null) {
            plugins.add(plugin);
        }
    }

    public List<FeatureReference> getIncludedFeatures() {
        return Collections.unmodifiableList(includedFeatures);
    }

    public void addIncludedFeature(FeatureReference feature) {
        if (feature != null) {
            includedFeatures.add(feature);
        }
    }

    public List<PluginImport> getRequiredPlugins() {
        return Collections.unmodifiableList(requiredPlugins);
    }

    public void addRequiredPlugin(PluginImport plugin) {
        if (plugin != null) {
            requiredPlugins.add(plugin);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Feature feature = (Feature) o;
        return id.equals(feature.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Feature{" +
                "id='" + id + '\'' +
                ", plugins=" + plugins.size() +
                ", includedFeatures=" + includedFeatures.size() +
                '}';
    }

    /** Reference to a plugin in a feature. */
    public static class PluginReference {
        private final String id;

        public PluginReference(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** Reference to an included feature. */
    public static class FeatureReference {
        private final String id;
        private boolean optional;

        public FeatureReference(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public String getId() {
            return id;
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

    /** Import of a required plugin (from <requires><import plugin=.../>). */
    public static class PluginImport {
        private final String id;

        public PluginImport(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
