package com.cubrid.tools.ideaconfig.resolver;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.Feature;
import com.cubrid.tools.ideaconfig.model.Feature.PluginReference;
import com.cubrid.tools.ideaconfig.model.Feature.FeatureReference;
import com.cubrid.tools.ideaconfig.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Resolves feature dependencies and determines which bundles are required for a product.
 */
public class FeatureResolver {

    private static final Logger log = LoggerFactory.getLogger(FeatureResolver.class);

    private final Map<String, Feature> featuresById = new LinkedHashMap<>();
    private final Map<String, Bundle> bundlesByName = new LinkedHashMap<>();

    public FeatureResolver() {
    }

    /**
     * Index features for lookup.
     *
     * @param features the features to index
     */
    public void indexFeatures(List<Feature> features) {
        for (Feature feature : features) {
            featuresById.put(feature.getId(), feature);
        }
        log.debug("Indexed {} features", features.size());
    }

    /**
     * Index bundles for lookup.
     *
     * @param bundles the bundles to index
     */
    public void indexBundles(List<Bundle> bundles) {
        for (Bundle bundle : bundles) {
            bundlesByName.put(bundle.getSymbolicName(), bundle);
        }
        log.debug("Indexed {} bundles", bundles.size());
    }

    /**
     * Resolve all plugins required by a product.
     * This includes plugins from features and any included features.
     *
     * @param product the product
     * @return set of required plugin IDs
     */
    public Set<String> resolveProductPlugins(Product product) {
        Set<String> requiredPlugins = new LinkedHashSet<>();
        Set<String> processedFeatures = new HashSet<>();

        log.debug("Resolving plugins for product: {}", product.getName());

        // Process features referenced by product
        for (String featureId : product.getFeatureIds()) {
            collectFeaturePlugins(featureId, requiredPlugins, processedFeatures);
        }

        // Add plugins directly referenced by product
        requiredPlugins.addAll(product.getPluginIds());

        log.info("Product {} requires {} plugins from {} features",
            product.getName(), requiredPlugins.size(), processedFeatures.size());

        return requiredPlugins;
    }

    /**
     * Recursively collect plugins from a feature and its included features.
     */
    private void collectFeaturePlugins(String featureId, Set<String> plugins,
                                       Set<String> processedFeatures) {
        if (processedFeatures.contains(featureId)) {
            return; // Already processed
        }
        processedFeatures.add(featureId);

        Feature feature = featuresById.get(featureId);
        if (feature == null) {
            log.warn("Feature not found: {}", featureId);
            return;
        }

        log.debug("  Processing feature: {} ({} plugins, {} included features)",
            featureId, feature.getPlugins().size(), feature.getIncludedFeatures().size());

        // Add plugins from this feature
        for (PluginReference plugin : feature.getPlugins()) {
            plugins.add(plugin.getId());
        }

        // Process included features
        for (FeatureReference ref : feature.getIncludedFeatures()) {
            collectFeaturePlugins(ref.getId(), plugins, processedFeatures);
        }

        // Process required plugins (imports)
        for (Feature.PluginImport imp : feature.getRequiredPlugins()) {
            plugins.add(imp.getId());
        }
    }

    /**
     * Get all features required by a product (recursively).
     *
     * @param product the product
     * @return list of required features in dependency order
     */
    public List<Feature> resolveProductFeatures(Product product) {
        List<Feature> result = new ArrayList<>();
        Set<String> processedFeatures = new HashSet<>();

        for (String featureId : product.getFeatureIds()) {
            collectFeatures(featureId, result, processedFeatures);
        }

        return result;
    }

    /**
     * Recursively collect features in dependency order.
     */
    private void collectFeatures(String featureId, List<Feature> result,
                                 Set<String> processedFeatures) {
        if (processedFeatures.contains(featureId)) {
            return;
        }
        processedFeatures.add(featureId);

        Feature feature = featuresById.get(featureId);
        if (feature == null) {
            log.warn("Feature not found: {}", featureId);
            return;
        }

        // Process dependencies first
        for (FeatureReference ref : feature.getIncludedFeatures()) {
            collectFeatures(ref.getId(), result, processedFeatures);
        }

        result.add(feature);
    }

    /**
     * Get local bundles required by a product.
     *
     * @param product the product
     * @return list of local bundles required by the product
     */
    public List<Bundle> resolveProductBundles(Product product) {
        Set<String> requiredPlugins = resolveProductPlugins(product);
        List<Bundle> result = new ArrayList<>();

        for (String pluginId : requiredPlugins) {
            Bundle bundle = bundlesByName.get(pluginId);
            if (bundle != null) {
                result.add(bundle);
            } else {
                log.debug("Plugin {} is not a local bundle (likely external)", pluginId);
            }
        }

        log.info("Product {} has {} local bundles out of {} required plugins",
            product.getName(), result.size(), requiredPlugins.size());

        return result;
    }

    /**
     * Get external plugins required by a product (plugins not in local bundles).
     *
     * @param product the product
     * @return set of external plugin IDs
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

    /**
     * Get a feature by ID.
     *
     * @param featureId the feature ID
     * @return the feature, or null if not found
     */
    public Feature getFeature(String featureId) {
        return featuresById.get(featureId);
    }

    /**
     * Get all indexed features.
     *
     * @return collection of all features
     */
    public Collection<Feature> getAllFeatures() {
        return Collections.unmodifiableCollection(featuresById.values());
    }

    /**
     * Print feature hierarchy for debugging.
     */
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

        if (printed.contains(featureId)) {
            log.info("{}[*] {} (already shown)", prefix, featureId);
            return;
        }
        printed.add(featureId);

        log.info("{}[F] {} ({} plugins)", prefix, featureId, feature.getPlugins().size());

        for (PluginReference plugin : feature.getPlugins()) {
            boolean isLocal = bundlesByName.containsKey(plugin.getId());
            log.info("{}  [P] {} {}", prefix, plugin.getId(),
                isLocal ? "(local)" : "(external)");
        }

        for (FeatureReference ref : feature.getIncludedFeatures()) {
            printFeatureTree(ref.getId(), indent + 1, printed);
        }
    }
}
