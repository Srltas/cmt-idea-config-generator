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

    public Feature parseFeature(Path featureDir) throws IOException {
        Path featureFile = featureDir.resolve("feature.xml");
        if (!Files.exists(featureFile)) {
            throw new IOException("feature.xml not found in " + featureDir);
        }
        return parse(featureFile, featureDir);
    }

    private Feature parse(Path featureFile, Path featureDir) throws IOException {
        log.debug("Parsing feature: {}", featureFile);

        try {
            Document doc = XmlHelper.parseFile(featureFile);
            Element root = doc.getDocumentElement();

            if (!"feature".equals(root.getTagName())) {
                throw new IOException("Invalid feature.xml: root element is not 'feature'");
            }

            String id = root.getAttribute("id");
            if (id == null || id.isBlank()) {
                throw new IOException("Missing 'id' attribute in feature.xml: " + featureFile);
            }

            Feature feature = new Feature(id, root.getAttribute("version"), featureDir);
            feature.setLabel(root.getAttribute("label"));

            for (Element include : XmlHelper.getChildElements(root, "includes")) {
                FeatureReference ref = parseInclude(include);
                if (ref != null) {
                    feature.addIncludedFeature(ref);
                }
            }

            XmlHelper.getChildElement(root, "requires").ifPresent(requires -> {
                for (Element imp : XmlHelper.getChildElements(requires, "import")) {
                    PluginImport pi = parseImport(imp);
                    if (pi != null) {
                        feature.addRequiredPlugin(pi);
                    }
                }
            });

            for (Element plugin : XmlHelper.getChildElements(root, "plugin")) {
                String pluginId = plugin.getAttribute("id");
                if (pluginId != null && !pluginId.isBlank()) {
                    feature.addPlugin(new PluginReference(pluginId));
                }
            }

            log.debug("Parsed feature: {} with {} plugins", feature.getId(), feature.getPlugins().size());
            return feature;

        } catch (SAXException e) {
            throw new IOException("Failed to parse feature.xml: " + featureFile, e);
        }
    }

    private FeatureReference parseInclude(Element element) {
        String id = element.getAttribute("id");
        if (id == null || id.isBlank()) {
            return null;
        }
        FeatureReference ref = new FeatureReference(id);
        ref.setOptional("true".equals(element.getAttribute("optional")));
        return ref;
    }

    private PluginImport parseImport(Element element) {
        String plugin = element.getAttribute("plugin");
        if (plugin == null || plugin.isBlank()) {
            return null; // feature imports are not handled
        }
        return new PluginImport(plugin);
    }
}
