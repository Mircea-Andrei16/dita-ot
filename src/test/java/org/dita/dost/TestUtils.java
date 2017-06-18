/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2011 Jarno Elovirta
 *
 * See the accompanying LICENSE file for applicable license.
 */
package org.dita.dost;

import nu.validator.htmlparser.dom.HtmlDocumentBuilder;
import org.dita.dost.log.DITAOTLogger;
import org.dita.dost.util.CatalogUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.io.FileUtils.copyFile;

/**
 * Test utilities.
 * 
 * @author Jarno Elovirta
 */
public class TestUtils {

    public static final File testStub = new File("src" + File.separator + "test" + File.separator + "resources");
    
    /**
     * Get test resource directory
     * 
     * @param testClass test class
     * @return resource directory
     * @throws RuntimeException if retrieving the directory failed
     */
    public static File getResourceDir(final Class<?> testClass) throws RuntimeException {
        final URL dir = ClassLoader.getSystemResource(testClass.getSimpleName());
        if (dir == null) {
            throw new RuntimeException("Failed to find resource for " + testClass.getSimpleName());
        }
        try {
            return new File(dir.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to find resource for " + testClass.getSimpleName() + ":" + e.getMessage(), e);
        }
    }
    
    /**
     * Create temporary directory based on test class.
     * 
     * @param testClass test class
     * @return temporary directory
     * @throws IOException if creating directory failed
     */
    public static File createTempDir(final Class<?> testClass) throws IOException {
        final File tempDir = new File(System.getProperty("java.io.tmpdir"),
                testClass.getName());
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new IOException("Unable to create temporary directory " + tempDir.getAbsolutePath());
        }
        return tempDir;
    }

    /**
     * Read file contents into a string.
     * 
     * @param file file to read
     * @return contents of the file
     * @throws IOException if reading file failed
     */
    public static String readFileToString(final File file) throws IOException {
        return readFileToString(file, false);
    }

    /**
     * Read file contents into a string.
     * 
     * @param file file to read
     * @param ignoreHead ignore first row
     * @return contents of the file
     * @throws IOException if reading file failed
     */
    public static String readFileToString(final File file, final boolean ignoreHead) throws IOException {
        final StringBuilder std = new StringBuilder();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            boolean firstLine = true;
            if (ignoreHead) {
                in.readLine();
            }
            String str;
            while ((str = in.readLine()) != null) {
                if (!firstLine) {
                    std.append("\n");
                } else {
                    firstLine = false;
                }
                std.append(str);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return std.toString();
    }

    /**
     * Read XML file contents into a string.
     * 
     * @param file XML file to read
     * @param normalize normalize whitespace
     * @return contents of the file
     * @throws Exception if parsing the file failed
     */
    public static String readXmlToString(final File file, final boolean normalize, final boolean clean)
            throws Exception {
        final Writer std = new CharArrayWriter();
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
            final Transformer t = TransformerFactory.newInstance().newTransformer();
            XMLReader p = XMLReaderFactory.createXMLReader();
            p.setEntityResolver(CatalogUtils.getCatalogResolver());
            if (normalize) {
                t.setOutputProperty(OutputKeys.INDENT, "yes");
                p = new NormalizingXMLFilterImpl(p);
            }
            if (clean) {
                p = new CleaningXMLFilterImpl(p);
            }
            t.transform(new SAXSource(p, new InputSource(in)),
                    new StreamResult(std));
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return std.toString();
    }

    private static class NormalizingXMLFilterImpl extends XMLFilterImpl {

        NormalizingXMLFilterImpl(final XMLReader parent) {
            super(parent);
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            final char[] res = new String(ch, start, length).replaceAll("\\s+", " ").trim().toCharArray();
            getContentHandler().characters(res, 0, res.length);
        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
            //NOOP
        }

    }

    private static class CleaningXMLFilterImpl extends XMLFilterImpl {

        CleaningXMLFilterImpl(final XMLReader parent) {
            super(parent);
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                final Attributes atts) throws SAXException {
            final AttributesImpl attsBuf = new AttributesImpl(atts);
            for (final String a: new String[] {"class", "domains", "xtrf", "xtrc"}) {
                final int i = attsBuf.getIndex(a);
                if (i != -1) {
                    attsBuf.removeAttribute(i);
                }
            }
            getContentHandler().startElement(uri, localName, qName, attsBuf);
        }

        @Override
        public void processingInstruction(final String target, final String data) throws SAXException {
            // Ignore
        }

    }

    /**
     * Deletes a file. If file is a directory, delete it and all sub-directories.
     * 
     * @param file file or directory to delete, must not be null
     * @throws IOException in case deletion is unsuccessful
     */
    public static void forceDelete(final File file) throws IOException {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (final File c: file.listFiles()) {
                    forceDelete(c);
                }
            }
            if (!file.delete()) {
                throw new IOException("Failed to delete " + file.getAbsolutePath());
            }
        }
    }

    /**
     * Copy directories recursively.
     * 
     * @param src source directory
     * @param dst destination directory
     * @throws IOException if copying failed
     */
    public static void copy(final File src, final File dst) throws IOException {
        if (src.isFile()) {
            copyFile(src, dst);
        } else {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IOException("Failed to create directory " + dst);
            }
            for (final File s: src.listFiles()) {
                copy(s, new File(dst, s.getName()));
            }
        }
    }

    /**
     * Normalize XML file.
     * 
     * @param src source XML file
     * @param dst destination XML file
     * @throws Exception if parsing or serializing failed
     */
    public static void normalize(final File src, final File dst) throws Exception {
        CatalogUtils.setDitaDir(new File("src" + File.separator + "main").getAbsoluteFile());
        final Transformer serializer = TransformerFactory.newInstance().newTransformer();
        final XMLReader parser = XMLReaderFactory.createXMLReader();
        parser.setEntityResolver(CatalogUtils.getCatalogResolver());
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new BufferedInputStream(new FileInputStream(src));
            out = new BufferedOutputStream(new FileOutputStream(dst));
            serializer.transform(new SAXSource(parser, new InputSource(in)),
                                 new StreamResult(out));
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    public static void assertXMLEqual(Document exp, Document act) {
        final Diff d = DiffBuilder
                .compare(exp)
                .withTest(act)
                .ignoreWhitespace()
                .ignoreComments()
                .normalizeWhitespace()
                .withNodeFilter(node -> node.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE)
                .build();
        if (d.hasDifferences()) {
            throw new AssertionError(d.toString());
        }
    }

    public static void assertXMLEqual(InputSource exp, InputSource act) {
        final Diff d = DiffBuilder
                .compare(new SAXSource(exp))
                .withTest(new SAXSource(act))
                .ignoreWhitespace()
                .ignoreComments()
                .withNodeFilter(node -> node.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE)
                .build();
        if (d.hasDifferences()) {
            throw new AssertionError(d.toString());
        }
    }

    public static void assertHtmlEqual(InputSource exp, InputSource act) {
        final Diff d = DiffBuilder
                .compare(new SAXSource(exp))
                .withDocumentBuilderFactory(new HTMLDocumentBuilderFactory())
                .withTest(new SAXSource(act))
                .ignoreWhitespace()
                .ignoreComments()
                .normalizeWhitespace()
                .withNodeFilter(node -> node.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE)
                .build();
        if (d.hasDifferences()) {
            throw new AssertionError(d.toString());
        }
    }

    private static class HTMLDocumentBuilderFactory extends DocumentBuilderFactory {
        @Override
        public Object getAttribute(final String arg0) throws IllegalArgumentException {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean getFeature(final String arg0) throws ParserConfigurationException {
            throw new UnsupportedOperationException();
        }
        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            return new HtmlDocumentBuilder();
        }
        @Override
        public void setAttribute(final String arg0, final Object arg1) throws IllegalArgumentException {
            throw new UnsupportedOperationException();
        }
        @Override
        public void setFeature(final String arg0, final boolean arg1) throws ParserConfigurationException {
            throw new UnsupportedOperationException();
        }
    }

    public static Document buildControlDocument(String content) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * DITA-OT logger that will throw an assertion error for error messages.
     */
    public static class TestLogger implements DITAOTLogger {

        private boolean failOnError;
        
        public TestLogger() {
            this.failOnError = true;
        }
        
        public TestLogger(final boolean failOnError) {
            this.failOnError = failOnError;
        }
        
        public void info(final String msg) {
            //System.out.println(msg);
        }

        public void warn(final String msg) {
            //System.err.println(msg);
        }

        public void error(final String msg) {
            if (failOnError) {
                throw new AssertionError("Error message was thrown: " + msg);
            }
        }

        public void error(final String msg, final Throwable t) {
            if (failOnError) {
                t.printStackTrace();
                throw new AssertionError("Error message was thrown: " + msg);
            }
        }

        public void debug(final String msg) {
            //System.out.println(msg);
        }

    }
    
    /**
     * DITA-OT logger that will cache messages.
     */
    public static final class CachingLogger implements DITAOTLogger {

        final boolean strict;

        public CachingLogger() {
            this(false);
        }

        public CachingLogger(final boolean strict) {
            this.strict = strict;
        }

        private List<Message> buf = new ArrayList<Message>();
        
        public void info(final String msg) {
            buf.add(new Message(Message.Level.INFO, msg, null));
        }

        public void warn(final String msg) {
            buf.add(new Message(Message.Level.WARN, msg, null));
        }

        public void error(final String msg) {
            if (strict) {
                throw new RuntimeException();
            } else {
                buf.add(new Message(Message.Level.ERROR, msg, null));
            }
        }
        
        public void error(final String msg, final Throwable t) {
            if (strict) {
                throw new RuntimeException(t);
            } else {
                buf.add(new Message(Message.Level.ERROR, msg, null));
            }
        }

        public void logFatal(final String msg) {
            buf.add(new Message(Message.Level.FATAL, msg, null));
        }

        public void debug(final String msg) {
            buf.add(new Message(Message.Level.DEBUG, msg, null));
        }

        public void logException(final Throwable t) {
            buf.add(new Message(Message.Level.ERROR, t.getMessage(), t));
        }
        
        public static final class Message {
            public enum Level { DEBUG, INFO, WARN, ERROR, FATAL }
            public final Level level;
            public final String message;
            public final Throwable exception;
            public Message(final Level level, final String message, final Throwable exception) {
                this.level = level;
                this.message = message;
                this.exception = exception;
            }
        }
        
        public List<Message> getMessages() {
        	return Collections.unmodifiableList(buf);
        }
        
    }

}
