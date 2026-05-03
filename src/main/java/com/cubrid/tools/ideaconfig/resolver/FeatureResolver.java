package com.cubrid.tools.ideaconfig.resolver;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.Feature;
import com.cubrid.tools.ideaconfig.model.Feature.FeatureReference;
import com.cubrid.tools.ideaconfig.model.Feature.PluginImport;
import com.cubrid.tools.ideaconfig.model.Feature.PluginReference;
import com.cubrid.tools.ideaconfig.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves which plugins (local + external) are required by a product.
 */
public class FeatureResolver {

    private static final Logger log = LoggerFactory.getLogger(FeatureResolver.class);

    private final Map<String, Feature> featuresById = new LinkedHashMap<>();
    private final Map<String, Bundle> bundlesByName = new LinkedHashMap<>();

    public void indexFeatures(List<Feature> features) {
        for (Feature feature : features) {
            featuresById.put(feature.getId(), feature);
        }
        log.debug("Indexed {} features", features.size());
    }

    public void indexBundles(List<Bundle> bundles) {
        for (Bundle bundle : bundles) {
            bundlesByName.put(bundle.getSymbolicName(), bundle);
        }
        log.debug("Indexed {} bundles", bundles.size());
    }

    /**
     * Resolve all plugins required by a product (recursively expanding features).
     */
    public Set<String> resolveProductPlugins(Product product) {
        Set<String> requiredPlugins = new LinkedHashSet<>();
        Set<String> processedFeatures = new HashSet<>();

        for (String featureId : product.getFeatureIds()) {
            collectFeaturePlugins(featureId, requiredPlugins, processedFeatures);
        }
        requiredPlugins.addAll(product.getPluginIds());

        log.info("Product {} requires {} plugins from {} features",
            product.getName(), requiredPlugins.size(), processedFeatures.size());
        return requiredPlugins;
    }

    private void collectFeaturePlugins(String featureId, Set<String> plugins,
                                       Set<String> processedFeatures) {
        if (!processedFeatures.add(featureId)) {
            return;
        }
        Feature feature = featuresById.get(featureId);
        if (feature == null) {
            log.warn("Feature not found: {}", featureId);
            return;
        }

        for (PluginReference plugin : feature.getPlugins()) {
            plugins.add(plugin.getId());
        }
        for (FeatureReference ref : feature.getIncludedFeatures()) {
            collectFeaturePlugins(ref.getId(), plugins, processedFeatures);
        }
        for (PluginImport imp : feature.getRequiredPlugins()) {
            plugins.add(imp.getId());
        }
    }

    /**
     * Plugins required by the product that don't map to a local bundle.
     */
    public Set<String> resolveExternalPlugins(Product product) {
        Set<String> requiredPlugins = resolveProductPlugins(product);
        Set<String> externalPlugins = new LinkedHashSet<>();
        for (String pluginId : requiredPlugins) {
            if (!bundlesByName.containsKey(pluginId)) {
                externalPlugins.add(pluginId);
            }
        }
        return externalPlugins;
    }

    public void printFeatureHierarchy(Product product) {
        log.info("Feature hierarchy for product: {}", product.getName());
        Set<String> printed = new HashSet<>();
        for (String featureId : product.getFeatureIds()) {
            printFeatureTree(featureId, 0, printed);
        }
    }

    private void printFeatureTree(String featureId, int indent, Set<String> printed) {
        String prefix = "  ".repeat(indent);
        Feature feature = featuresById.get(featureId);
        if (feature == null) {
            log.info("{}[?] {} (not found)", prefix, featureId);
            return;
        }
        if (!printed.add(featureId)) {
            log.info("{}[*] {} (already shown)", prefix, featureId);
            return;
        }
        log.info("{}[F] {} ({} plugins)", prefix, featureId, feature.getPlugins().size());
        for (PluginReference plugin : feature.getPlugins()) {
            boolean isLocal = bundlesByName.containsKey(plugin.getId());
            log.info("{}  [P] {} {}", prefix, plugin.getId(), isLocal ? "(local)" : "(external)");
        }
        for (FeatureReference ref : feature.getIncludedFeatures()) {
            printFeatureTree(ref.getId(), indent + 1, printed);
        }
    }
}
