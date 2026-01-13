package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.Feature;
import com.cubrid.tools.ideaconfig.model.Feature.FeatureReference;
import com.cubrid.tools.ideaconfig.model.Feature.PluginImport;
import com.cubrid.tools.ideaconfig.model.Feature.PluginReference;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Parser for Eclipse feature.xml files.
 */
public class FeatureParser {

    private static final Logger log = LoggerFactory.getLogger(FeatureParser.class);

    public FeatureParser() {
    }

    /**
     * Parse a feature.xml file.
     *
     * @param featureFile path to feature.xml
     * @param featureDir the feature directory
     * @return parsed Feature
     * @throws IOException if file cannot be read
     */
    public Feature parse(Path featureFile, Path featureDir) throws IOException {
        log.debug("Parsing feature: {}", featureFile);

        try {
            Document doc = XmlHelper.parseFile(featureFile);
            Element root = doc.getDocumentElement();

            if (!"feature".equals(root.getTagName())) {
                throw new IOException("Invalid feature.xml: root element is not 'feature'");
            }

            // Extract basic attributes
            String id = root.getAttribute("id");
            String version = root.getAttribute("version");
            String label = root.getAttribute("label");
            String providerName = root.getAttribute("provider-name");
            String primaryPlugin = root.getAttribute("plugin");

            if (id == null || id.isBlank()) {
                throw new IOException("Missing 'id' attribute in feature.xml: " + featureFile);
            }

            Feature feature = new Feature(id, version, featureDir);
            feature.setLabel(label);
            feature.setProviderName(providerName);
            feature.setPrimaryPlugin(primaryPlugin);

            // Parse included features
            List<Element> includes = XmlHelper.getChildElements(root, "includes");
            for (Element include : includes) {
                FeatureReference ref = parseInclude(include);
                if (ref != null) {
                    feature.addIncludedFeature(ref);
                }
            }

            // Parse requires section
            XmlHelper.getChildElement(root, "requires").ifPresent(requires -> {
                List<Element> imports = XmlHelper.getChildElements(requires, "import");
                for (Element imp : imports) {
                    PluginImport pi = parseImport(imp);
                    if (pi != null) {
                        feature.addRequiredPlugin(pi);
                    }
                }
            });

            // Parse plugins
            List<Element> plugins = XmlHelper.getChildElements(root, "plugin");
            for (Element plugin : plugins) {
                PluginReference pr = parsePlugin(plugin);
                if (pr != null) {
                    feature.addPlugin(pr);
                }
            }

            log.debug("Parsed feature: {} with {} plugins", feature.getId(), feature.getPlugins().size());

            return feature;

        } catch (SAXException e) {
            throw new IOException("Failed to parse feature.xml: " + featureFile, e);
        }
    }

    /**
     * Parse an included feature reference.
     */
    private FeatureReference parseInclude(Element element) {
        String id = element.getAttribute("id");
        if (id == null || id.isBlank()) {
            return null;
        }

        FeatureReference ref = new FeatureReference(id);
        ref.setVersion(element.getAttribute("version"));
        ref.setOptional("true".equals(element.getAttribute("optional")));

        return ref;
    }

    /**
     * Parse a plugin import.
     */
    private PluginImport parseImport(Element element) {
        String plugin = element.getAttribute("plugin");
        if (plugin == null || plugin.isBlank()) {
            // Could be a feature import
            String feature = element.getAttribute("feature");
            if (feature == null || feature.isBlank()) {
                return null;
            }
            // For now, we only handle plugin imports
            return null;
        }

        PluginImport pi = new PluginImport(plugin);
        pi.setVersion(element.getAttribute("version"));
        pi.setMatch(element.getAttribute("match"));

        return pi;
    }

    /**
     * Parse a plugin reference.
     */
    private PluginReference parsePlugin(Element element) {
        String id = element.getAttribute("id");
        if (id == null || id.isBlank()) {
            return null;
        }

        PluginReference pr = new PluginReference(id);
        pr.setVersion(element.getAttribute("version"));
        pr.setUnpack(!"false".equals(element.getAttribute("unpack")));
        pr.setFragment("true".equals(element.getAttribute("fragment")));

        return pr;
    }

    /**
     * Parse a feature from its directory.
     *
     * @param featureDir feature directory containing feature.xml
     * @return parsed Feature
     * @throws IOException if feature.xml cannot be read
     */
    public Feature parseFeature(Path featureDir) throws IOException {
        Path featureFile = featureDir.resolve("feature.xml");
        if (!Files.exists(featureFile)) {
            throw new IOException("feature.xml not found in " + featureDir);
        }
        return parse(featureFile, featureDir);
    }
}
