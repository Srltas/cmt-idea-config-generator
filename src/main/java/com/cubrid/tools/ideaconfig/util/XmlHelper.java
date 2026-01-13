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
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper class for XML parsing and generation.
 */
public final class XmlHelper {

    private static final Logger log = LoggerFactory.getLogger(XmlHelper.class);

    private static final DocumentBuilderFactory DOC_FACTORY;
    private static final TransformerFactory TRANS_FACTORY;

    static {
        DOC_FACTORY = DocumentBuilderFactory.newInstance();
        DOC_FACTORY.setNamespaceAware(true);

        // Security: disable external entities
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
        // Utility class
    }

    /**
     * Create a new empty XML document.
     *
     * @return new Document
     */
    public static Document createDocument() {
        try {
            DocumentBuilder builder = DOC_FACTORY.newDocumentBuilder();
            return builder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create document builder", e);
        }
    }

    /**
     * Parse an XML file into a Document.
     *
     * @param file path to the XML file
     * @return parsed Document
     * @throws IOException if file cannot be read
     * @throws SAXException if XML is malformed
     */
    public static Document parseFile(Path file) throws IOException, SAXException {
        try {
            DocumentBuilder builder = DOC_FACTORY.newDocumentBuilder();
            return builder.parse(file.toFile());
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create document builder", e);
        }
    }

    /**
     * Write a Document to a file.
     *
     * @param doc the document to write
     * @param file destination file path
     * @throws IOException if file cannot be written
     */
    public static void writeDocument(Document doc, Path file) throws IOException {
        try {
            Transformer transformer = TRANS_FACTORY.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "no");

            // Ensure parent directory exists
            Files.createDirectories(file.getParent());

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(file.toFile());
            transformer.transform(source, result);

            log.debug("Wrote XML file: {}", file);
        } catch (TransformerException e) {
            throw new IOException("Failed to write XML file: " + file, e);
        }
    }

    /**
     * Convert a Document to a string.
     *
     * @param doc the document
     * @return XML string
     */
    public static String toString(Document doc) {
        try {
            Transformer transformer = TRANS_FACTORY.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new RuntimeException("Failed to convert document to string", e);
        }
    }

    /**
     * Get a child element by tag name.
     *
     * @param parent parent element
     * @param tagName child tag name
     * @return Optional containing the child element, or empty if not found
     */
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

    /**
     * Get all child elements with the specified tag name.
     *
     * @param parent parent element
     * @param tagName child tag name
     * @return list of matching child elements
     */
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

    /**
     * Get all child elements.
     *
     * @param parent parent element
     * @return list of all child elements
     */
    public static List<Element> getAllChildElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /**
     * Get text content of a child element.
     *
     * @param parent parent element
     * @param tagName child tag name
     * @return text content or null if element not found
     */
    public static String getChildText(Element parent, String tagName) {
        return getChildElement(parent, tagName)
                .map(Element::getTextContent)
                .orElse(null);
    }

    /**
     * Create an element with text content.
     *
     * @param doc the document
     * @param tagName element tag name
     * @param textContent text content
     * @return created element
     */
    public static Element createElementWithText(Document doc, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        element.setTextContent(textContent);
        return element;
    }

    /**
     * Create an element with attributes.
     *
     * @param doc the document
     * @param tagName element tag name
     * @param attributes attribute name-value pairs (alternating names and values)
     * @return created element
     */
    public static Element createElement(Document doc, String tagName, String... attributes) {
        Element element = doc.createElement(tagName);
        for (int i = 0; i < attributes.length - 1; i += 2) {
            element.setAttribute(attributes[i], attributes[i + 1]);
        }
        return element;
    }

    /**
     * Safely get an attribute value.
     *
     * @param element the element
     * @param attrName attribute name
     * @return attribute value or null if not present
     */
    public static String getAttribute(Element element, String attrName) {
        if (element.hasAttribute(attrName)) {
            return element.getAttribute(attrName);
        }
        return null;
    }

    /**
     * Safely get an attribute value with default.
     *
     * @param element the element
     * @param attrName attribute name
     * @param defaultValue default value if attribute not present
     * @return attribute value or default
     */
    public static String getAttribute(Element element, String attrName, String defaultValue) {
        String value = getAttribute(element, attrName);
        return value != null ? value : defaultValue;
    }
}
