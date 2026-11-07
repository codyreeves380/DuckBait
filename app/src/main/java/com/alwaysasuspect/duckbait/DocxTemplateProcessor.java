package com.alwaysasuspect.duckbait;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Replaces placeholders in a DOCX's main document using Android's built-in ZIP/XML APIs. */
final class DocxTemplateProcessor {
    private static final String WORD_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
    private static final String DOCUMENT_XML = "word/document.xml";

    private DocxTemplateProcessor() {
    }

    static void generate(InputStream template, OutputStream output,
                         Map<String, String> replacements) throws Exception {
        try (ZipInputStream zipInput = new ZipInputStream(template);
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            boolean foundDocument = false;
            while ((entry = zipInput.getNextEntry()) != null) {
                ZipEntry outputEntry = new ZipEntry(entry.getName());
                if (entry.getTime() >= 0) {
                    outputEntry.setTime(entry.getTime());
                }
                zipOutput.putNextEntry(outputEntry);

                if (DOCUMENT_XML.equals(entry.getName())) {
                    foundDocument = true;
                    ByteArrayOutputStream xml = new ByteArrayOutputStream();
                    int count;
                    while ((count = zipInput.read(buffer)) != -1) {
                        xml.write(buffer, 0, count);
                    }
                    zipOutput.write(replaceDocument(xml.toByteArray(), replacements));
                } else {
                    int count;
                    while ((count = zipInput.read(buffer)) != -1) {
                        zipOutput.write(buffer, 0, count);
                    }
                }

                zipOutput.closeEntry();
                zipInput.closeEntry();
            }
            if (!foundDocument) {
                throw new IllegalArgumentException("Selected file is not a valid DOCX: word/document.xml is missing.");
            }
        }
    }

    private static byte[] replaceDocument(byte[] xmlBytes,
                                          Map<String, String> replacements) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);

        // The Android Harmony parser does not support the XXE-hardening feature URIs
        // used by some desktop parsers. Block external entity resolution directly.
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId)
                    throws SAXException {
                throw new SAXException("External XML entities are not allowed.");
            }
        });

        Document document = builder.parse(
                new InputSource(new ByteArrayInputStream(xmlBytes)));
        NodeList paragraphs = document.getElementsByTagNameNS(WORD_NS, "p");
        for (int p = 0; p < paragraphs.getLength(); p++) {
            Element paragraph = (Element) paragraphs.item(p);
            List<Element> textElements = textElementsForParagraph(paragraph);
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                replaceAcrossRuns(textElements, replacement.getKey(), replacement.getValue());
            }
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(result));
        return result.toByteArray();
    }

    private static List<Element> textElementsForParagraph(Element paragraph) {
        List<Element> result = new ArrayList<>();
        NodeList descendants = paragraph.getElementsByTagNameNS(WORD_NS, "t");
        for (int i = 0; i < descendants.getLength(); i++) {
            Element text = (Element) descendants.item(i);
            Node parent = text.getParentNode();
            while (parent != null && parent != paragraph
                    && !(parent instanceof Element
                    && WORD_NS.equals(parent.getNamespaceURI())
                    && "p".equals(parent.getLocalName()))) {
                parent = parent.getParentNode();
            }
            if (parent == paragraph) {
                result.add(text);
            }
        }
        return result;
    }

    private static void replaceAcrossRuns(List<Element> textElements,
                                          String placeholder, String replacement) {
        if (placeholder == null || placeholder.length() == 0) {
            return;
        }
        String safeReplacement = replacement == null ? "" : replacement;
        while (true) {
            StringBuilder combined = new StringBuilder();
            for (Element text : textElements) {
                combined.append(text.getTextContent());
            }
            int start = combined.lastIndexOf(placeholder);
            if (start < 0) {
                return;
            }
            int end = start + placeholder.length();
            int startNode = -1;
            int endNode = -1;
            int startInNode = 0;
            int endInNode = 0;
            int offset = 0;
            for (int i = 0; i < textElements.size(); i++) {
                int nextOffset = offset + textElements.get(i).getTextContent().length();
                if (startNode < 0 && start < nextOffset) {
                    startNode = i;
                    startInNode = start - offset;
                }
                if (endNode < 0 && end <= nextOffset) {
                    endNode = i;
                    endInNode = end - offset;
                    break;
                }
                offset = nextOffset;
            }
            if (startNode < 0 || endNode < 0) {
                return;
            }

            String first = textElements.get(startNode).getTextContent();
            if (startNode == endNode) {
                setText(textElements.get(startNode),
                        first.substring(0, startInNode) + safeReplacement
                                + first.substring(endInNode));
            } else {
                setText(textElements.get(startNode),
                        first.substring(0, startInNode) + safeReplacement);
                for (int i = startNode + 1; i < endNode; i++) {
                    setText(textElements.get(i), "");
                }
                String last = textElements.get(endNode).getTextContent();
                setText(textElements.get(endNode), last.substring(endInNode));
            }
        }
    }

    private static void setText(Element textElement, String value) {
        textElement.setTextContent(value);
        if (!value.isEmpty() && (Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1)))) {
            textElement.setAttributeNS(XML_NS, "xml:space", "preserve");
        }
    }
}

