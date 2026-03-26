package com.cubrid.tools.ideaconfig.eclipse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight parser for Maven pom.xml files.
 * Extracts dependency artifactIds for standalone (non-OSGi) modules
 * that define their dependencies in pom.xml instead of MANIFEST.MF Require-Bundle.
 */
public class PomDependencyParser {

    private static final Logger log = LoggerFactory.getLogger(PomDependencyParser.class);

    /**
     * Parse dependency artifactIds from a module's pom.xml.
     *
     * @param moduleDir the module directory containing pom.xml
     * @return list of dependency artifactIds, or empty list if pom.xml not found
     */
    public List<String> parseDependencyArtifactIds(Path moduleDir) {
        Path pomFile = moduleDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            log.debug("No pom.xml found in {}", moduleDir);
            return Collections.emptyList();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Security: disable external entities
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile.toFile());

            List<String> artifactIds = new ArrayList<>();
            NodeList dependencyNodes = doc.getElementsByTagName("dependency");

            for (int i = 0; i < dependencyNodes.getLength(); i++) {
                Element dependency = (Element) dependencyNodes.item(i);
                // Only process direct children of <dependencies>, not plugin dependencies
                if (dependency.getParentNode() != null
                        && "dependencies".equals(dependency.getParentNode().getNodeName())) {
                    String artifactId = getChildText(dependency, "artifactId");
                    if (artifactId != null && !artifactId.isBlank()) {
                        artifactIds.add(artifactId.trim());
                    }
                }
            }

            log.debug("Parsed {} dependencies from {}", artifactIds.size(), pomFile);
            return artifactIds;

        } catch (Exception e) {
            log.warn("Failed to parse pom.xml in {}: {}", moduleDir, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get the text content of a child element.
     */
    private String getChildText(Element parent, String childTag) {
        NodeList children = parent.getElementsByTagName(childTag);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent();
        }
        return null;
    }
}
