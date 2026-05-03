package com.cubrid.tools.ideaconfig.eclipse;

import com.cubrid.tools.ideaconfig.model.Product;
import com.cubrid.tools.ideaconfig.util.XmlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Parser for Eclipse .product files.
 */
public class ProductParser {

    private static final Logger log = LoggerFactory.getLogger(ProductParser.class);

    public Product parse(Path productFile) throws IOException {
        log.debug("Parsing product: {}", productFile);

        try {
            Document doc = XmlHelper.parseFile(productFile);
            Element root = doc.getDocumentElement();

            if (!"product".equals(root.getTagName())) {
                throw new IOException("Invalid product file: root element is not 'product'");
            }

            String uid = root.getAttribute("uid");
            String id = root.getAttribute("id");
            if ((uid == null || uid.isBlank()) && (id == null || id.isBlank())) {
                throw new IOException("Missing 'uid' or 'id' attribute in product file: " + productFile);
            }

            Product product = new Product(uid, id, productFile.getParent());
            product.setName(root.getAttribute("name"));
            product.setVersion(root.getAttribute("version"));
            product.setApplication(root.getAttribute("application"));

            XmlHelper.getChildElement(root, "launcherArgs").ifPresent(args -> {
                product.setVmArgs(XmlHelper.getChildText(args, "vmArgs"));
                product.setVmArgsMac(XmlHelper.getChildText(args, "vmArgsMac"));
                product.setVmArgsWin(XmlHelper.getChildText(args, "vmArgsWin"));
                product.setVmArgsLinux(XmlHelper.getChildText(args, "vmArgsLinux"));
                product.setProgramArgs(XmlHelper.getChildText(args, "programArgs"));
            });

            XmlHelper.getChildElement(root, "features").ifPresent(features -> {
                for (Element feature : XmlHelper.getChildElements(features, "feature")) {
                    String featureId = feature.getAttribute("id");
                    if (featureId != null && !featureId.isBlank()) {
                        product.addFeatureId(featureId);
                    }
                }
            });

            XmlHelper.getChildElement(root, "plugins").ifPresent(plugins -> {
                for (Element plugin : XmlHelper.getChildElements(plugins, "plugin")) {
                    String pluginId = plugin.getAttribute("id");
                    if (pluginId != null && !pluginId.isBlank()) {
                        product.addPluginId(pluginId);
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
}
