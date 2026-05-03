package com.cubrid.tools.ideaconfig.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper for XML parsing and generation.
 */
public final class XmlHelper {

    private static final Logger log = LoggerFactory.getLogger(XmlHelper.class);

    private static final DocumentBuilderFactory DOC_FACTORY;
    private static final TransformerFactory TRANS_FACTORY;

    static {
        DOC_FACTORY = DocumentBuilderFactory.newInstance();
        DOC_FACTORY.setNamespaceAware(true);
        try {
            DOC_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DOC_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
            DOC_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            log.warn("Could not set XML security features", e);
        }
        TRANS_FACTORY = TransformerFactory.newInstance();
    }

    private XmlHelper() {
    }

    public static Document createDocument() {
        try {
            return DOC_FACTORY.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create document builder", e);
        }
    }

    public static Document parseFile(Path file) throws IOException, SAXException {
        try {
            return DOC_FACTORY.newDocumentBuilder().parse(file.toFile());
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create document builder", e);
        }
    }

    public static void writeDocument(Document doc, Path file) throws IOException {
        try {
            Transformer transformer = TRANS_FACTORY.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "no");

            Files.createDirectories(file.getParent());
            transformer.transform(new DOMSource(doc), new StreamResult(file.toFile()));
            log.debug("Wrote XML file: {}", file);
        } catch (TransformerException e) {
            throw new IOException("Failed to write XML file: " + file, e);
        }
    }

    public static Optional<Element> getChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
                return Optional.of((Element) node);
            }
        }
        return Optional.empty();
    }

    public static List<Element> getChildElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    public static String getChildText(Element parent, String tagName) {
        return getChildElement(parent, tagName)
                .map(Element::getTextContent)
                .orElse(null);
    }
}
