package com.xmlcalabash.core;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.tree.iter.NamespaceIterator;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NamePool;

import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.util.HashSet;
import java.util.logging.Level;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.InputStream;
import java.io.File;

import com.xmlcalabash.functions.BaseURI;
import com.xmlcalabash.functions.Cwd;
import com.xmlcalabash.functions.IterationPosition;
import com.xmlcalabash.functions.IterationSize;
import com.xmlcalabash.functions.ResolveURI;
import com.xmlcalabash.functions.StepAvailable;
import com.xmlcalabash.functions.SystemProperty;
import com.xmlcalabash.functions.ValueAvailable;
import com.xmlcalabash.functions.VersionAvailable;
import com.xmlcalabash.functions.XPathVersionAvailable;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.DocumentSequence;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.DefaultXProcMessageListener;
import com.xmlcalabash.util.StepErrorListener;
import com.xmlcalabash.util.URIUtils;
import com.xmlcalabash.util.S9apiUtils;
import com.xmlcalabash.util.RelevantNodes;
import com.xmlcalabash.util.LogOptions;
import com.xmlcalabash.util.XProcURIResolver;
import com.xmlcalabash.model.Step;

import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: Nov 11, 2008
 * Time: 7:47:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class XProcConfiguration {
    public static final QName _prefix = new QName("", "prefix");
    public static final QName _uri = new QName("", "uri");
    public static final QName _class_name = new QName("", "class-name");
    public static final QName _type = new QName("", "type");
    public static final QName _port = new QName("", "port");
    public static final QName _href = new QName("", "href");
    public static final QName _level = new QName("", "level");
    public static final QName _name = new QName("", "name");
    public static final QName _value = new QName("", "value");
    public static final QName _exclude_inline_prefixes = new QName("", "exclude-inline-prefixes");

    public boolean schemaAware = false;
    public Hashtable<String,String> nsBindings = new Hashtable<String,String> ();
    public boolean debug = false;
    public Hashtable<String,Vector<ReadablePipe>> inputs = new Hashtable<String,Vector<ReadablePipe>> ();
    public Hashtable<String,Level> logLevel = new Hashtable<String,Level> ();
    public ReadablePipe pipeline = null;
    public Hashtable<String,String> outputs = new Hashtable<String,String> ();
    public Hashtable<String,Hashtable<QName,String>> params = new Hashtable<String,Hashtable<QName,String>> ();
    public Hashtable<QName,String> options = new Hashtable<QName,String> ();
    public boolean safeMode = false;
    public String stepName = null;
    public String entityResolver = null;
    public String uriResolver = null;
    public String errorListener = null;
    public Hashtable<String,String> serializationOptions = new Hashtable<String,String>();
    public LogOptions logOpt = LogOptions.WRAPPED;
    public Vector<String> extensionFunctions = new Vector<String>();

    public boolean extensionValues = false;
    
    private Hashtable<QName,String> implementations = new Hashtable<QName,String> ();
    
    private Processor cfgProcessor = null;
    private XProcURIResolver resolver;
    private XProcMessageListener msgListener;
    private boolean firstInput = false;
    private boolean firstOutput = false;
    
    private ThreadLocal<XProcRuntime> runtime = new ThreadLocal<XProcRuntime>();

    public XProcConfiguration() {
        cfgProcessor = new Processor(false);
        loadConfiguration();

        if (schemaAware) {
            // Bugger. We have to restart with a schema-aware processor
            nsBindings.clear();
            inputs.clear();
            outputs.clear();
            params.clear();
            options.clear();
            implementations.clear();
            extensionFunctions.clear();

            cfgProcessor = new Processor(true);
            loadConfiguration();
        }
        setup();
    }

    public XProcConfiguration(boolean schemaAware) {
        cfgProcessor = new Processor(schemaAware);
        boolean sa = cfgProcessor.isSchemaAware();

        /*
        Properties prop = System.getProperties();
        System.err.println(prop.getProperty("java.class.path", null));
        System.err.println(cfgProcessor.getUnderlyingConfiguration().getClass().getName());
        */

        if (schemaAware && !sa) {
            System.err.println("Failed to obtain schema-aware processor.");
        }

        loadConfiguration();
        setup();
    }

    public XProcConfiguration(Processor processor) {
        cfgProcessor = processor;
        loadConfiguration();
        if (schemaAware != processor.isSchemaAware()) {
            throw new XProcException("Schema awareness in configuration conflicts with specified processor.");
        }
        setup();
    }
    
    public void setCurrentRuntime(XProcRuntime runtime){
        if (this.runtime.get()==null){
            this.runtime.set(runtime);
        }
    }
    public XProcRuntime getCurrentRuntime(){
        return runtime.get();
    }

    public Processor getProcessor() {
        return cfgProcessor;
//        Processor processor = new Processor(cfgProcessor.isSchemaAware());
//        processor.getUnderlyingConfiguration().setStripsAllWhiteSpace(false);
//        processor.getUnderlyingConfiguration().setStripsWhiteSpace(Whitespace.NONE);
//        return processor;
    }
    
    public XProcURIResolver getResolver(){
        return resolver;
    }
    
    public void setURIResolver(URIResolver uriResolver){
        resolver.setUnderlyingURIResolver(uriResolver);
    }

    public void setEntityResolver(EntityResolver entityResolver){
        resolver.setUnderlyingEntityResolver(entityResolver);
    }
    
    public XProcMessageListener getMessageListner(){
        return msgListener;
    }
    
    public void setMessageListener(XProcMessageListener msgListener){
        this.msgListener =msgListener;
    }
    
    private void setup(){
        cfgProcessor.registerExtensionFunction(new Cwd(this));
        cfgProcessor.registerExtensionFunction(new BaseURI(this));
        cfgProcessor.registerExtensionFunction(new ResolveURI(this));
        cfgProcessor.registerExtensionFunction(new SystemProperty(this));
        cfgProcessor.registerExtensionFunction(new StepAvailable(this));
        cfgProcessor.registerExtensionFunction(new IterationSize(this));
        cfgProcessor.registerExtensionFunction(new IterationPosition(this));
        cfgProcessor.registerExtensionFunction(new ValueAvailable(this));
        cfgProcessor.registerExtensionFunction(new VersionAvailable(this));
        cfgProcessor.registerExtensionFunction(new XPathVersionAvailable(this));
        
        Configuration saxonConfig = cfgProcessor.getUnderlyingConfiguration();
        resolver = new XProcURIResolver(this);
        saxonConfig.setURIResolver(resolver);

        try {
            if (uriResolver != null) {
                resolver.setUnderlyingURIResolver((URIResolver) Class.forName(uriResolver).newInstance());
            }
            if (entityResolver != null) {
                resolver.setUnderlyingEntityResolver((EntityResolver) Class.forName(entityResolver).newInstance());
            }

            if (errorListener != null) {
                msgListener = (XProcMessageListener) Class.forName(errorListener).newInstance();
            } else {
                msgListener = new DefaultXProcMessageListener();
            }
        } catch (Exception e) {
            throw new XProcException(e);
        }

        StepErrorListener errListener = new StepErrorListener(this);
        saxonConfig.setErrorListener(errListener);


        for (String className : extensionFunctions) {
            try {
                ExtensionFunctionDefinition def = (ExtensionFunctionDefinition) Class.forName(className).newInstance();
                msgListener.fine(null, null, "Instantiated: " + className);
                cfgProcessor.registerExtensionFunction(def);
            } catch (NoClassDefFoundError ncdfe) {
                msgListener.fine(null, null, "Failed to instantiate extension function: " + className);
            } catch (Exception e) {
                msgListener.fine(null, null, "Failed to instantiate extension function: " + className);
            }
        }
    }

    private void loadConfiguration() {
        URI home = URIUtils.homeAsURI();
        URI cwd = URIUtils.cwdAsURI();
        URI puri = home;

        cfgProcessor.getUnderlyingConfiguration().setStripsAllWhiteSpace(false);
        cfgProcessor.getUnderlyingConfiguration().setStripsWhiteSpace(Whitespace.NONE);

        try {
            InputStream instream = getClass().getResourceAsStream("/etc/configuration.xml");
            if (instream == null) {
                throw new UnsupportedOperationException("Failed to load configuration from JAR file");
            }
            SAXSource source = new SAXSource(new InputSource(instream));
            DocumentBuilder builder = cfgProcessor.newDocumentBuilder();
            builder.setLineNumbering(true);
            builder.setBaseURI(puri);
            parse(builder.build(source));
        } catch (SaxonApiException sae) {
            throw new XProcException(sae);
        }

        try {
            XdmNode cnode = readXML(".calabash", home.toASCIIString());
            parse(cnode);
        } catch (XProcException xe) {
            if (XProcConstants.dynamicError(11).equals(xe.getErrorCode())) {
                // nop; file not found is ok
            } else {
                throw xe;
            }
        }

        try {
            XdmNode cnode = readXML(".calabash", cwd.toASCIIString());
            parse(cnode);
        } catch (XProcException xe) {
            if (XProcConstants.dynamicError(11).equals(xe.getErrorCode())) {
                // nop; file not found is ok
            } else {
                throw xe;
            }
        }
    }

    public XdmNode readXML(String href, String base) {
        Source source = null;
        href = URIUtils.encode(href);

        try {
            URI baseURI = new URI(base);
            source = new SAXSource(new InputSource(baseURI.resolve(href).toASCIIString()));
        } catch (URISyntaxException use) {
            throw new XProcException(use);
        }

        DocumentBuilder builder = cfgProcessor.newDocumentBuilder();
        builder.setLineNumbering(true);

        try {
            return builder.build(source);
        } catch (SaxonApiException sae) {
            throw new XProcException(XProcConstants.dynamicError(11), sae);
        }
    }


    public void parse(XdmNode doc) {
        if (doc.getNodeKind() == XdmNodeKind.DOCUMENT) {
            doc = S9apiUtils.getDocumentElement(doc);
        }

        for (XdmNode node : new RelevantNodes(null, doc, Axis.CHILD)) {
            String uri = node.getNodeName().getNamespaceURI();
            String localName = node.getNodeName().getLocalName();

            if (XProcConstants.NS_CALABASH_CONFIG.equals(uri)
                    || XProcConstants.NS_EXPROC_CONFIG.equals(uri)) {
                if ("implementation".equals(localName)) {
                    parseImplementation(node);
                } else if ("schema-aware".equals(localName)) {
                    parseSchemaAware(node);
                } else if ("namespace-binding".equals(localName)) {
                    parseNamespaceBinding(node);
                } else if ("debug".equals(localName)) {
                    parseDebug(node);
                } else if ("entity-resolver".equals(localName)) {
                    parseEntityResolver(node);
                } else if ("input".equals(localName)) {
                    parseInput(node);
                } else if ("output".equals(localName)) {
                    parseOutput(node);
                } else if ("with-option".equals(localName)) {
                    parseWithOption(node);
                } else if ("with-param".equals(localName)) {
                    parseWithParam(node);
                } else if ("safe-mode".equals(localName)) {
                    parseSafeMode(node);
                } else if ("step-name".equals(localName)) {
                    parseStepName(node);
                } else if ("uri-resolver".equals(localName)) {
                    parseURIResolver(node);
                } else if ("step-error-listener".equals(localName)) {
                    parseErrorListener(node);
                } else if ("pipeline".equals(localName)) {
                    parsePipeline(node);
                } else if ("serialization".equals(localName)) {
                    parseSerialization(node);
                } else if ("extension-function".equals(localName)) {
                    parseExtensionFunction(node);
                } else {
                    throw new XProcException(doc, "Unexpected configuration option: " + localName);
                }
            }
        }

        firstInput = true;
        firstOutput = true;
    }
    
    public boolean isStepAvailable(QName type) {
		return implementations.containsKey(type);
	}
	
	public XProcStep newStep(XProcRuntime runtime,XAtomicStep step){
        String className = implementations.get(step.getType());
        if (className == null) {
            throw new UnsupportedOperationException("Misconfigured. No 'class' in configuration for " + step.getType());
        }

        // FIXME: This isn't really very secure...
        if (runtime.getSafeMode() && !className.startsWith("com.xmlcalabash.")) {
            throw XProcException.dynamicError(21);
        }
        
		try {
			Constructor<?> constructor = Class.forName(className).getConstructor(XProcRuntime.class, XAtomicStep.class);
			return (XProcStep) constructor.newInstance(runtime,step);
		} catch (NoSuchMethodException nsme) {
			throw new UnsupportedOperationException("No such method: " + className, nsme);
		} catch (ClassNotFoundException cfne) {
			throw new UnsupportedOperationException("Class not found: " + className, cfne);
		} catch (InstantiationException ie) {
			throw new UnsupportedOperationException("Instantiation error", ie);
		} catch (IllegalAccessException iae) {
			throw new UnsupportedOperationException("Illegal access error", iae);
		} catch (InvocationTargetException ite) {
			throw new UnsupportedOperationException("Invocation target exception", ite);
		}
	}

    private void parseSchemaAware(XdmNode node) {
        String value = node.getStringValue().trim();

        if (!"true".equals(value) && !"false".equals(value)) {
            throw new XProcException(node, "Invalid configuration value for schema-aware: "+ value);
        }

        schemaAware = "true".equals(value);
    }

    private void parseNamespaceBinding(XdmNode node) {
        String aname = node.getAttributeValue(_prefix);
        String avalue = node.getAttributeValue(_uri);
        nsBindings.put(aname,avalue);
    }

    private void parseDebug(XdmNode node) {
        String value = node.getStringValue().trim();
        debug = "true".equals(value);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new XProcException(node, "Invalid configuration value for debug: "+ value);
        }
    }

    private void parseEntityResolver(XdmNode node) {
        String value = node.getAttributeValue(_class_name);
        entityResolver = value;
    }

    private void parseExtensionFunction(XdmNode node) {
        String value = node.getAttributeValue(_class_name);
        extensionFunctions.add(value);
    }

    private void parseInput(XdmNode node) {
        String port = node.getAttributeValue(_port);
        String href = node.getAttributeValue(_href);
        Vector<XdmValue> docnodes = new Vector<XdmValue> ();
        boolean sawElement = false;

        for (XdmNode child : new RelevantNodes(null, node, Axis.CHILD)) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                if (sawElement) {
                    throw new XProcException(node, "Invalid configuration value for input '" + port + "': content is not a valid XML document.");
                }
                sawElement = true;
            }
            docnodes.add(child);
        }

        if (firstInput) {
            inputs.clear();
            firstInput = false;
        }

        if (!inputs.containsKey(port)) {
            inputs.put(port, new Vector<ReadablePipe> ());
        }

        Vector<ReadablePipe> documents = inputs.get(port);

        if (href != null) {
            if (docnodes.size() > 0) {
                throw new XProcException(node, "Invalid configuration value for input '" + port + "': href and content on input.");
            }

            documents.add(new ConfigDocument(href, node.getBaseURI().toASCIIString()));
        } else {
            HashSet<String> excludeURIs = readExcludeInlinePrefixes(node, node.getAttributeValue(_exclude_inline_prefixes));
            documents.add(new ConfigDocument(docnodes, excludeURIs));
        }
    }

    private HashSet<String> readExcludeInlinePrefixes(XdmNode node, String prefixList) {
        HashSet<String> excludeURIs = new HashSet<String> ();
        excludeURIs.add(XProcConstants.NS_XPROC);

        if (prefixList != null) {
            // FIXME: Surely there's a better way to do this?
            NodeInfo inode = node.getUnderlyingNode();
            NamePool pool = inode.getNamePool();
            int inscopeNS[] = NamespaceIterator.getInScopeNamespaceCodes(inode);

            for (String pfx : prefixList.split("\\s+")) {
                boolean found = false;

                for (int pos = 0; pos < inscopeNS.length; pos++) {
                    int ns = inscopeNS[pos];
                    String nspfx = pool.getPrefixFromNamespaceCode(ns);
                    String nsuri = pool.getURIFromNamespaceCode(ns);

                    if (pfx.equals(nspfx)) {
                        found = true;
                        excludeURIs.add(nsuri);
                    }
                }

                if (!found) {
                    throw new XProcException(XProcConstants.staticError(57), "No binding for '" + pfx + ":'");
                }
            }
        }

        return excludeURIs;
    }

    private void parsePipeline(XdmNode node) {
        String href = node.getAttributeValue(_href);
        Vector<XdmValue> docnodes = new Vector<XdmValue> ();
        boolean sawElement = false;

        for (XdmNode child : new RelevantNodes(null, node, Axis.CHILD)) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                if (sawElement) {
                    throw new XProcException(node, "Content of pipeline is not a valid XML document.");
                }
                sawElement = true;
            }
            docnodes.add(child);
        }

        if (href != null) {
            if (docnodes.size() > 0) {
                throw new XProcException(node, "XProcConfiguration error: href and content on pipeline");
            }
            pipeline = new ConfigDocument(href, node.getBaseURI().toASCIIString());
        } else {
            HashSet<String> excludeURIs = readExcludeInlinePrefixes(node, node.getAttributeValue(_exclude_inline_prefixes));
            pipeline = new ConfigDocument(docnodes, excludeURIs);
        }
    }

    private void parseOutput(XdmNode node) {
        String port = node.getAttributeValue(_port);
        String href = node.getAttributeValue(_href);

        for (XdmNode child : new RelevantNodes(null, node, Axis.CHILD)) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                throw new XProcException(node, "Output must be empty.");
            }
        }

        if (firstOutput) {
            outputs.clear();
            firstOutput = false;
        }

        href = node.getBaseURI().resolve(href).toASCIIString();

        if ("-".equals(href) || href.startsWith("http:") || href.startsWith("https:") || href.startsWith("file:")) {
            outputs.put(port, href);
        } else {
            File f = new File(href);
            String fn = URIUtils.encode(f.getAbsolutePath());
            // FIXME: HACK!
            if ("\\".equals(System.getProperty("file.separator"))) {
                fn = "/" + fn;
            }
            outputs.put(port, "file://" + fn);
        }
    }

    private void parseWithOption(XdmNode node) {
        String nameStr = node.getAttributeValue(_name);
        String value = node.getAttributeValue(_value);

        QName name = new QName(nameStr,node);

        options.put(name,value);
    }

    private void parseWithParam(XdmNode node) {
        String port = node.getAttributeValue(_port);
        String nameStr = node.getAttributeValue(_name);
        String value = node.getAttributeValue(_value);

        QName name = new QName(nameStr,node);

        if (port == null) {
            port = "*";
        }

        Hashtable<QName,String> pvalues;
        if (params.containsKey(port)) {
            pvalues = params.get(port);
        } else {
            pvalues = new Hashtable<QName,String> ();
        }

        pvalues.put(name, value);

        params.put(port,pvalues);
    }

    private void parseSafeMode(XdmNode node) {
        String value = node.getStringValue().trim();

        safeMode = "true".equals(value);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new XProcException(node, "Unexpected configuration value for safe-mode: "+ value);
        }
    }


    private void parseStepName(XdmNode node) {
        String value = node.getStringValue().trim();
        stepName = value;
    }

    private void parseURIResolver(XdmNode node) {
        String value = node.getAttributeValue(_class_name);
        uriResolver = value;
    }

    private void parseErrorListener(XdmNode node) {
        String value = node.getAttributeValue(_class_name);
        errorListener = value;
    }

    private void parseImplementation(XdmNode node) {
        String nameStr = node.getAttributeValue(_type);
        String value = node.getAttributeValue(_class_name);

        if (nameStr == null || value == null) {
            throw new XProcException(node, "Unexpected implementation in configuration; must have both type and class-name attributes");
        }

        QName name = new QName(nameStr,node);

        implementations.put(name, value);
    }

    private void parseSerialization(XdmNode node) {
        String[] attributeNames = new String[] {"byte-order-mark", "cdata-section-elements",
                "doctype-public", "doctype-system", "encoding", "escape-uri-attributes",
                "include-content-type", "indent", "media-type", "method", "normalization-form",
                "omit-xml-declaration", "standalone", "undeclare-prefixes", "version"};

        checkAttributes(node, attributeNames , false);

        for (String name : attributeNames) {
            String value = node.getAttributeValue(new QName(name));
            if (value == null) {
                continue;
            }

            if ("byte-order-mark".equals(name) || "escape-uri-attributes".equals(name)
                    || "include-content-type".equals(name) || "indent".equals(name)
                    || "omit-xml-declaration".equals(name) || "undeclare-prefixes".equals(name)) {
                checkBoolean(node, name, value);
                serializationOptions.put(name, value);
            } else if ("method".equals(name)) {
                QName methodName = new QName(value, node);
                if ("".equals(methodName.getPrefix())) {
                    String method = methodName.getLocalName();
                    if ("html".equals(method) || "xhtml".equals(method) || "text".equals(method) || "xml".equals(method)) {
                        serializationOptions.put(name, method);
                    } else {
                        throw new XProcException(node, "Configuration error: only the xml, xhtml, html, and text serialization methods are supported.");
                    }
                } else {
                    throw new XProcException(node, "Configuration error: only the xml, xhtml, html, and text serialization methods are supported.");
                }
            } else {
                serializationOptions.put(name, value);
            }

            for (XdmNode snode : new RelevantNodes(null, node, Axis.CHILD)) {
                throw new XProcException(node, "Configuration error: serialization must be empty");
            }
        }
    }

    private HashSet<String> checkAttributes(XdmNode node, String[] attrs, boolean optionShortcutsOk) {
        HashSet<String> hash = null;
        if (attrs != null) {
            hash = new HashSet<String> ();
            for (String attr : attrs) {
                hash.add(attr);
            }
        }
        HashSet<String> options = null;

        for (XdmNode attr : new RelevantNodes(null, node, Axis.ATTRIBUTE)) {
            QName aname = attr.getNodeName();
            if ("".equals(aname.getNamespaceURI())) {
                if (hash.contains(aname.getLocalName())) {
                // ok
                } else if (optionShortcutsOk) {
                    if (options == null) {
                        options = new HashSet<String> ();
                    }
                    options.add(aname.getLocalName());
                } else {
                    throw new XProcException(node, "Configuration error: attribute \"" + aname + "\" not allowed on " + node.getNodeName());
                }
            } else if (XProcConstants.NS_XPROC.equals(aname.getNamespaceURI())) {
                throw new XProcException(node, "Configuration error: attribute \"" + aname + "\" not allowed on " + node.getNodeName());
            }
            // Everything else is ok
        }

        return options;
    }

    private void checkBoolean(XdmNode node, String name, String value) {
        if (value != null && !"true".equals(value) && !"false".equals(value)) {
            throw new XProcException(node, "Configuration error: " + name + " on serialization must be 'true' or 'false'");
        }
    }

    private class ConfigDocument implements ReadablePipe {
        private String href = null;
        private String base = null;
        private Vector<XdmValue> nodes = null;
        private boolean read = false;
        private XdmNode doc = null;
        private HashSet<String> excludeUris = null;

        public ConfigDocument(String href, String base) {
            this.href = href;
            this.base = base;
        }

        public ConfigDocument(Vector<XdmValue> nodes, HashSet<String> excludeUris) {
            this.nodes = nodes;
            this.excludeUris = excludeUris;
        }

        public void canReadSequence(boolean sequence) {
            // nop; always false
        }

        public XdmNode read() throws SaxonApiException {
            read = true;

            if (doc != null) {
                return doc;
            }

            if (nodes != null) {
                // Find the document element so we can get the base URI
                XdmNode node = null;
                for (int pos = 0; pos < nodes.size() && node == null; pos++) {
                    if (((XdmNode) nodes.get(pos)).getNodeKind() == XdmNodeKind.ELEMENT) {
                        node = (XdmNode) nodes.get(pos);
                    }
                }

                XdmDestination dest = new XdmDestination();
                try {
                    S9apiUtils.writeXdmValue(cfgProcessor, nodes, dest, node.getBaseURI());
                    doc = dest.getXdmNode();
                    if (excludeUris.size() != 0) {
                        doc = S9apiUtils.removeNamespaces(cfgProcessor, doc, excludeUris);
                    }
                } catch (SaxonApiException sae) {
                    throw new XProcException(sae);
                }
            } else {
                doc = readXML(href, base);
            }

            return doc;
        }

        public void setReader(Step step) {
            // I don't care
        }

        public void resetReader() {
            read = false;
        }

        public boolean moreDocuments() {
            return !read;
        }

        public boolean closed() {
            return false;
        }

        public int documentCount() {
            return 1;
        }

        public DocumentSequence documents() {
            throw new XProcException("You can't get the document sequence of an input from the config file!");
        }
    }


}
