package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.Product;
import com.cubrid.tools.ideaconfig.model.Product.PluginConfiguration;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Parser for Eclipse .product files.
 */
public class ProductParser {

    private static final Logger log = LoggerFactory.getLogger(ProductParser.class);

    public ProductParser() {
    }

    /**
     * Parse a .product file.
     *
     * @param productFile path to .product file
     * @return parsed Product
     * @throws IOException if file cannot be read
     */
    public Product parse(Path productFile) throws IOException {
        log.debug("Parsing product: {}", productFile);

        try {
            Document doc = XmlHelper.parseFile(productFile);
            Element root = doc.getDocumentElement();

            if (!"product".equals(root.getTagName())) {
                throw new IOException("Invalid product file: root element is not 'product'");
            }

            // Extract basic attributes
            String uid = root.getAttribute("uid");
            String id = root.getAttribute("id");
            String name = root.getAttribute("name");
            String version = root.getAttribute("version");
            String application = root.getAttribute("application");
            boolean useFeatures = "true".equals(root.getAttribute("useFeatures"));
            boolean includeLaunchers = "true".equals(root.getAttribute("includeLaunchers"));

            if ((uid == null || uid.isBlank()) && (id == null || id.isBlank())) {
                throw new IOException("Missing 'uid' or 'id' attribute in product file: " + productFile);
            }

            Product product = new Product(uid, id, productFile.getParent());
            product.setName(name);
            product.setVersion(version);
            product.setApplication(application);
            product.setUseFeatures(useFeatures);
            product.setIncludeLaunchers(includeLaunchers);

            // Parse splash
            XmlHelper.getChildElement(root, "splash").ifPresent(splash -> {
                product.setSplashLocation(splash.getAttribute("location"));
            });

            // Parse launcher
            XmlHelper.getChildElement(root, "launcher").ifPresent(launcher -> {
                product.setLauncherName(launcher.getAttribute("name"));
            });

            // Parse launcherArgs
            XmlHelper.getChildElement(root, "launcherArgs").ifPresent(args -> {
                // VM args
                product.setVmArgs(XmlHelper.getChildText(args, "vmArgs"));
                product.setVmArgsMac(XmlHelper.getChildText(args, "vmArgsMac"));
                product.setVmArgsWin(XmlHelper.getChildText(args, "vmArgsWin"));
                product.setVmArgsLinux(XmlHelper.getChildText(args, "vmArgsLinux"));

                // Program args
                product.setProgramArgs(XmlHelper.getChildText(args, "programArgs"));
                product.setProgramArgsMac(XmlHelper.getChildText(args, "programArgsMac"));
                product.setProgramArgsWin(XmlHelper.getChildText(args, "programArgsWin"));
                product.setProgramArgsLinux(XmlHelper.getChildText(args, "programArgsLinux"));
            });

            // Parse features
            XmlHelper.getChildElement(root, "features").ifPresent(features -> {
                List<Element> featureElements = XmlHelper.getChildElements(features, "feature");
                for (Element feature : featureElements) {
                    String featureId = feature.getAttribute("id");
                    if (featureId != null && !featureId.isBlank()) {
                        product.addFeatureId(featureId);
                    }
                }
            });

            // Parse plugins
            XmlHelper.getChildElement(root, "plugins").ifPresent(plugins -> {
                List<Element> pluginElements = XmlHelper.getChildElements(plugins, "plugin");
                for (Element plugin : pluginElements) {
                    String pluginId = plugin.getAttribute("id");
                    if (pluginId != null && !pluginId.isBlank()) {
                        product.addPluginId(pluginId);
                    }
                }
            });

            // Parse configurations
            XmlHelper.getChildElement(root, "configurations").ifPresent(configurations -> {
                List<Element> pluginElements = XmlHelper.getChildElements(configurations, "plugin");
                for (Element plugin : pluginElements) {
                    PluginConfiguration config = parsePluginConfiguration(plugin);
                    if (config != null) {
                        product.addPluginConfiguration(config);
                    }
                }
            });

            log.debug("Parsed product: {} with {} features and {} plugins",
                product.getName(), product.getFeatureIds().size(), product.getPluginIds().size());

            return product;

        } catch (SAXException e) {
            throw new IOException("Failed to parse product file: " + productFile, e);
        }
    }

    /**
     * Parse a plugin configuration element.
     */
    private PluginConfiguration parsePluginConfiguration(Element element) {
        String id = element.getAttribute("id");
        if (id == null || id.isBlank()) {
            return null;
        }

        PluginConfiguration config = new PluginConfiguration(id);
        config.setAutoStart("true".equals(element.getAttribute("autoStart")));

        String startLevel = element.getAttribute("startLevel");
        if (startLevel != null && !startLevel.isBlank()) {
            try {
                config.setStartLevel(Integer.parseInt(startLevel));
            } catch (NumberFormatException e) {
                log.warn("Invalid startLevel for plugin {}: {}", id, startLevel);
            }
        }

        return config;
    }
}
