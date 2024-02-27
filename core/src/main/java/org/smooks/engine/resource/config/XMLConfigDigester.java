/*-
 * ========================LICENSE_START=================================
 * Core
 * %%
 * Copyright (C) 2020 Smooks
 * %%
 * Licensed under the terms of the Apache License Version 2.0, or
 * the GNU Lesser General Public License version 3.0 or later.
 * 
 * SPDX-License-Identifier: Apache-2.0 OR LGPL-3.0-or-later
 * 
 * ======================================================================
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * ======================================================================
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * =========================LICENSE_END==================================
 */
package org.smooks.engine.resource.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smooks.Smooks;
import org.smooks.api.resource.config.Parameter;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.api.resource.config.ResourceConfigFactory;
import org.smooks.api.resource.config.ResourceConfigSeq;
import org.smooks.api.SmooksConfigException;
import org.smooks.engine.resource.extension.ExtensionContext;
import org.smooks.api.ExecutionContext;
import org.smooks.engine.DefaultApplicationContextBuilder;
import org.smooks.engine.delivery.AbstractParser;
import org.smooks.api.expression.ExpressionEvaluator;
import org.smooks.engine.expression.ExpressionEvaluatorFactory;
import org.smooks.support.StreamUtils;
import org.smooks.support.URIUtil;
import org.smooks.engine.profile.DefaultProfileSet;
import org.smooks.api.Registry;
import org.smooks.resource.URIResourceLocator;
import org.smooks.support.ClassUtils;
import org.smooks.support.DomUtils;
import org.smooks.support.XmlUtil;
import org.smooks.xml.XsdDOMValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Digester class for an XML {@link ResourceConfig} file (.cdrl).
 *
 * @author tfennelly
 */
@SuppressWarnings("WeakerAccess")
public final class XMLConfigDigester {

    public static final String XSD_V20 = "https://www.smooks.org/xsd/smooks-2.0.xsd";

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLConfigDigester.class);
    private static final ThreadLocal<Boolean> EXTENSION_DIGEST_ON = new ThreadLocal<>();

    private final ResourceConfigSeq resourceConfigSeq;
    private final Stack<SmooksConfig> configStack = new Stack<>();
    private final ExpressionEvaluatorFactory expressionEvaluatorFactory = new ExpressionEvaluatorFactory();

    private ClassLoader classLoader;
    private Map<String, Smooks> extendedConfigDigesters = new HashMap<>();

    /**
     * Private constructor.
     * <p/>
     * Never make this constructor (or any other) public.  It's only called from 2 places:
     * <ul>
     *  <li>From the {@link #digestConfig(java.io.InputStream, String)} method.</li>
     *  <li>From the {@link #getExtendedConfigDigester(String)} method.</li>
     * </ul>
     * <p>
     * The {@link #digestConfig(java.io.InputStream, String)} method is public and always calls
     * {@link #setExtensionDigestOff()}.  The {@link #getExtendedConfigDigester(String)} method is
     * private and always calls {@link #setExtensionDigestOn()}.  Playing with the
     * public/private nature of these methods may effect the behavior of the {@link #EXTENSION_DIGEST_ON}
     * ThreadLocal.
     *
     * @param resourceConfigSeq Config list.
     */
    public XMLConfigDigester(ResourceConfigSeq resourceConfigSeq) {
        this.resourceConfigSeq = resourceConfigSeq;
        configStack.push(new SmooksConfig("root-config"));
    }

    /**
     * Digest the XML Smooks configuration stream.
     *
     * @param stream                  The stream.
     * @param baseURI                 The base URI to be associated with the configuration stream.
     * @param extendedConfigDigesters Config digesters.
     * @return A {@link ResourceConfigSeq} containing the list of
     * {@link ResourceConfig ResourceConfigs} defined in the
     * XML configuration.
     * @throws SAXException          Error parsing the XML stream.
     * @throws IOException           Error reading the XML stream.
     * @throws SmooksConfigException Invalid configuration..
     */
    @SuppressWarnings("unused")
    public static ResourceConfigSeq digestConfig(InputStream stream, String baseURI, Map<String, Smooks> extendedConfigDigesters) throws SAXException, IOException, URISyntaxException, SmooksConfigException {
        return digestConfig(stream, baseURI, extendedConfigDigesters, null);
    }

    /**
     * Digest the XML Smooks configuration stream.
     *
     * @param stream                  The stream.
     * @param baseURI                 The base URI to be associated with the configuration stream.
     * @param extendedConfigDigesters Config digesters.
     * @param classLoader             The ClassLoader to be used.
     * @return A {@link ResourceConfigSeq} containing the list of
     * {@link ResourceConfig ResourceConfigs} defined in the
     * XML configuration.
     * @throws SAXException          Error parsing the XML stream.
     * @throws IOException           Error reading the XML stream.
     * @throws SmooksConfigException Invalid configuration..
     */
    public static ResourceConfigSeq digestConfig(InputStream stream, String baseURI, Map<String, Smooks> extendedConfigDigesters, ClassLoader classLoader) throws SAXException, IOException, URISyntaxException, SmooksConfigException {
        ResourceConfigSeq resourceConfigList = new DefaultResourceConfigSeq(baseURI);

        setExtensionDigestOff();
        XMLConfigDigester digester = new XMLConfigDigester(resourceConfigList);

        if (classLoader != null) {
            digester.classLoader = classLoader;
        }

        digester.extendedConfigDigesters = extendedConfigDigesters;
        digester.digestConfigRecursively(new InputStreamReader(stream), baseURI);

        return resourceConfigList;
    }

    /**
     * Digest the XML Smooks configuration stream.
     *
     * @param stream  The stream.
     * @param baseURI The base URI to be associated with the configuration stream.
     * @return A {@link ResourceConfigSeq} containing the list of
     * {@link ResourceConfig ResourceConfigs} defined in the
     * XML configuration.
     * @throws SAXException          Error parsing the XML stream.
     * @throws IOException           Error reading the XML stream.
     * @throws SmooksConfigException Invalid configuration..
     */
    public static ResourceConfigSeq digestConfig(InputStream stream, String baseURI) throws SAXException, IOException, URISyntaxException, SmooksConfigException {
        return digestConfig(stream, baseURI, (ClassLoader) null);
    }

    /**
     * Digest the XML Smooks configuration stream.
     *
     * @param stream  The stream.
     * @param baseURI The base URI to be associated with the configuration stream.
     * @return A {@link ResourceConfigSeq} containing the list of
     * {@link ResourceConfig ResourceConfigs} defined in the
     * XML configuration.
     * @throws SAXException          Error parsing the XML stream.
     * @throws IOException           Error reading the XML stream.
     * @throws SmooksConfigException Invalid configuration..
     */
    public static ResourceConfigSeq digestConfig(InputStream stream, String baseURI, ClassLoader classLoader) throws SAXException, IOException, URISyntaxException, SmooksConfigException {
        ResourceConfigSeq list = new DefaultResourceConfigSeq(baseURI);

        setExtensionDigestOff();
        XMLConfigDigester digester = new XMLConfigDigester(list);

        if (classLoader != null) {
            digester.classLoader = classLoader;
        }
        digester.digestConfigRecursively(new InputStreamReader(stream), baseURI);

        return list;
    }

    /**
     * Get the active resource configuration list.
     *
     * @return The active resource configuration list.
     */
    public ResourceConfigSeq getResourceList() {
        return resourceConfigSeq;
    }

    private void digestConfigRecursively(Reader stream, String baseURI) throws IOException, SAXException, URISyntaxException, SmooksConfigException {
        Document configDoc;
        String streamData = StreamUtils.readStream(stream);

        try {
            configDoc = XmlUtil.parseStream(new StringReader(streamData));
        } catch (ParserConfigurationException ee) {
            throw new SAXException("Unable to parse Smooks configuration.", ee);
        }

        XsdDOMValidator validator = new XsdDOMValidator(configDoc);
        String defaultNS = validator.getDefaultNamespace().toString();

        validator.validate();

        configStack.peek().defaultNS = defaultNS;
        if (XSD_V20.equals(defaultNS)) {
            digestV20XSDValidatedConfig(baseURI, configDoc);
        } else {
            throw new SAXException("Cannot parse Smooks configuration.  Unsupported default Namespace '" + defaultNS + "'.");
        }

        if (resourceConfigSeq.isEmpty()) {
            throw new SAXException("Invalid Content Delivery Resource archive definition file: 0 Content Delivery Resource definitions.");
        }
    }

    private void digestV20XSDValidatedConfig(String baseURI, Document configDoc) throws SAXException, URISyntaxException, SmooksConfigException {
        Element currentElement = configDoc.getDocumentElement();

        String defaultProfile = DomUtils.getAttributeValue(currentElement, "default-target-profile");
        String defaultConditionRef = DomUtils.getAttributeValue(currentElement, "default-condition-ref");

        NodeList configNodes = currentElement.getChildNodes();

        for (int i = 0; i < configNodes.getLength(); i++) {
            if (configNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element configElement = (Element) configNodes.item(i);

                // Make sure the element is permitted...
                assertElementPermitted(configElement);

                String elementName = DomUtils.getName(configElement);
                String namespaceURI = configElement.getNamespaceURI();
                if (namespaceURI == null || namespaceURI.equals(XSD_V20)) {
                    if (elementName.equals("params")) {
                        digestParams(configElement);
                    } else if (elementName.equals("conditions")) {
                        digestConditions(configElement);
                    } else if (elementName.equals("profiles")) {
                        digestProfiles(configElement);
                    } else if (elementName.equals("import")) {
                        digestImport(configElement, new URI(baseURI));
                    } else if (elementName.equals("reader")) {
                        digestReaderConfig(configElement, defaultProfile);
                    } else if (elementName.equals("resource-config")) {
                        digestResourceConfig(configElement, defaultProfile, defaultConditionRef);
                    }
                } else {
                    // It's an extended resource configuration element
                    digestExtendedResourceConfig(configElement, defaultProfile, defaultConditionRef);
                }
            }
        }
    }

    private void digestParams(Element paramsElement) {
        NodeList paramNodes = paramsElement.getElementsByTagName("param");

        if (paramNodes.getLength() > 0) {
            ResourceConfig globalParamsConfig = new DefaultResourceConfig(ParameterAccessor.GLOBAL_PARAMETERS, new Properties());

            digestParameters(paramsElement, globalParamsConfig);
            resourceConfigSeq.add(globalParamsConfig);
        }
    }

    private void assertElementPermitted(Element configElement) {
        if (isExtensionConfig()) {
            String elementName = DomUtils.getName(configElement);
            if (!elementName.equals("import") && !elementName.equals("resource-config")) {
                throw new SmooksConfigException("Configuration element '" + elementName + "' not supported in an extension configuration.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void digestImport(Element importElement, URI baseURI) throws SAXException, URISyntaxException, SmooksConfigException {
        String file = DomUtils.getAttributeValue(importElement, "file");
        URIResourceLocator resourceLocator;
        InputStream resourceStream;

        if (file == null) {
            throw new IllegalStateException("Invalid resource import.  'file' attribute must be specified.");
        }

        resourceLocator = new URIResourceLocator();
        resourceLocator.setBaseURI(baseURI);

        try {
            URI fileURI = resourceLocator.resolveURI(file);

            // Add the resource URI to the list.  Will fail if it was already loaded
            pushConfig(file, fileURI);
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Importing resource configuration '" + file + "' from inside '" + configStack.peek().configFile + "'.");
                }

                resourceStream = resourceLocator.getResource(file);
                try {
                    List<Element> importParams = DomUtils.getElements(importElement, "param", null);
                    if (!importParams.isEmpty()) {
                        // Inject parameters into import config...
                        String importConfig = StreamUtils.readStreamAsString(resourceStream, "UTF-8");

                        for (Element importParam : importParams) {
                            String paramName = DomUtils.getAttributeValue(importParam, "name");
                            String paramValue = XmlUtil.serialize(importParam.getChildNodes(), true);

                            importConfig = importConfig.replaceAll("@" + paramName + "@", paramValue);
                        }

                        digestConfigRecursively(new StringReader(importConfig), URIUtil.getParent(fileURI).toString()); // the file's parent URI becomes the new base URI.
                    } else {
                        digestConfigRecursively(new InputStreamReader(resourceStream), URIUtil.getParent(fileURI).toString()); // the file's parent URI becomes the new base URI.
                    }
                } finally {
                    resourceStream.close();
                }
            } finally {
                popConfig();
            }
        } catch (IOException e) {
            throw new SmooksConfigException("Failed to load Smooks configuration resource <import> '" + file + "': " + e.getMessage(), e);
        }
    }

    private void digestReaderConfig(Element configElement, String defaultProfile) {
        String profiles = DomUtils.getAttributeValue(configElement, "targetProfile");

        String readerClass = DomUtils.getAttributeValue(configElement, "class");

        ResourceConfig resourceConfig = new DefaultResourceConfig(AbstractParser.ORG_XML_SAX_DRIVER, new Properties(), (profiles != null ? profiles : defaultProfile), readerClass);

        // Add the reader resource...
        configureHandlers(configElement, resourceConfig);
        configureFeatures(configElement, resourceConfig);
        configureParams(configElement, resourceConfig);

        resourceConfigSeq.add(resourceConfig);
    }

    private void configureHandlers(Element configElement, ResourceConfig resourceConfig) {
        Element handlersElement = DomUtils.getElement(configElement, "handlers", 1);

        if (handlersElement != null) {
            NodeList handlers = handlersElement.getChildNodes();
            for (int i = 0; i < handlers.getLength(); i++) {
                if (handlers.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element handler = (Element) handlers.item(i);
                    String handlerClass = handler.getAttribute("class");

                    resourceConfig.setParameter("sax-handler", handlerClass);
                }
            }
        }
    }

    private void configureFeatures(Element configElement, ResourceConfig resourceConfig) {
        Element featuresElement = DomUtils.getElement(configElement, "features", 1);

        if (featuresElement != null) {
            NodeList features = featuresElement.getChildNodes();
            for (int i = 0; i < features.getLength(); i++) {
                if (features.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element feature = (Element) features.item(i);
                    String uri = feature.getAttribute("feature");

                    if (DomUtils.getName(feature).equals("setOn")) {
                        resourceConfig.setParameter("feature-on", uri);
                    } else {
                        resourceConfig.setParameter("feature-off", uri);
                    }
                }
            }
        }
    }

    private void configureParams(Element configElement, ResourceConfig resourceConfig) {
        Element paramsElement = DomUtils.getElement(configElement, "params", 1);

        if (paramsElement != null) {
            NodeList params = paramsElement.getChildNodes();
            for (int i = 0; i < params.getLength(); i++) {
                if (params.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element param = (Element) params.item(i);
                    String name = param.getAttribute("name");
                    String value = DomUtils.getAllText(param, true);

                    resourceConfig.setParameter(name, value);
                }
            }
        }
    }

    private void digestResourceConfig(Element configElement, String defaultProfile, String defaultConditionRef) {
        final String factory = DomUtils.getAttributeValue(configElement, "factory");

        final ResourceConfig resourceConfig;
        try {
            final Class<?> resourceConfigFactoryClass;
            if (factory != null) {
                resourceConfigFactoryClass = Class.forName(factory, true, classLoader);
            } else {
                resourceConfigFactoryClass = DefaultResourceConfigFactory.class;
            }
            try {
                ResourceConfigFactory resourceConfigFactory = (ResourceConfigFactory) resourceConfigFactoryClass.newInstance();
                resourceConfig = resourceConfigFactory.create(defaultProfile, configElement);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new SmooksConfigException(e.getMessage(), e);
            }

            // And add the condition, if defined...
            final Element conditionElement = DomUtils.getElementByTagName(configElement, "condition");
            if (conditionElement != null) {
                ExpressionEvaluator evaluator = digestCondition(conditionElement);
                resourceConfig.getSelectorPath().setConditionEvaluator(evaluator);
            } else if (defaultConditionRef != null) {
                ExpressionEvaluator evaluator = getConditionEvaluator(defaultConditionRef);
                resourceConfig.getSelectorPath().setConditionEvaluator(evaluator);
            }
        } catch (IllegalArgumentException | ClassNotFoundException e) {
            throw new SmooksConfigException("Invalid unit definition.", e);
        }

        // Add the parameters...
        digestParameters(configElement, resourceConfig);

        resourceConfigSeq.add(resourceConfig);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Adding smooks-resource config from [" + resourceConfigSeq.getName() + "]: " + resourceConfig);
        }
    }

    @SuppressWarnings("ConfusingArgumentToVarargsMethod")
    private void digestExtendedResourceConfig(Element configElement, String defaultProfile, String defaultConditionRef) {
        String configNamespace = configElement.getNamespaceURI();
        Smooks configDigester = getExtendedConfigDigester(configNamespace);
        ExecutionContext executionContext = configDigester.createExecutionContext();
        ExtensionContext extentionContext;
        Element conditionElement = DomUtils.getElement(configElement, "condition", 1);

        // Create the ExtenstionContext and set it on the ExecutionContext...
        if (conditionElement != null && conditionElement.getNamespaceURI().equals(XSD_V20)) {
            extentionContext = new ExtensionContext(this, defaultProfile, digestCondition(conditionElement));
        } else if (defaultConditionRef != null) {
            extentionContext = new ExtensionContext(this, defaultProfile, getConditionEvaluator(defaultConditionRef));
        } else {
            extentionContext = new ExtensionContext(this, defaultProfile, null);
        }
        executionContext.put(ExtensionContext.EXTENSION_CONTEXT_TYPED_KEY, extentionContext);

        // Filter the extension element through Smooks...
        configDigester.filterSource(executionContext, new DOMSource(configElement), null);

        // Copy the created resources from the ExtensionContext and onto the ResourceConfigList...
        List<ResourceConfig> resources = extentionContext.getResources();
        for (ResourceConfig resource : resources) {
            resourceConfigSeq.add(resource);
        }
    }

    private Smooks getExtendedConfigDigester(String configNamespace) {
        Smooks smooks = extendedConfigDigesters.get(configNamespace);

        if (smooks == null) {
            URI namespaceURI;

            try {
                namespaceURI = new URI(configNamespace);
            } catch (URISyntaxException e) {
                throw new SmooksConfigException("Unable to parse extended config namespace URI '" + configNamespace + "'.", e);
            }

            String resourcePath = "/META-INF" + namespaceURI.getPath() + "-smooks.xml";
            File resourceFile = new File(resourcePath);
            String baseURI = resourceFile.getParent().replace('\\', '/');

            // Validate the extended config...
            assertExtendedConfigOK(configNamespace, resourcePath);

            // Construct the Smooks instance for processing this config namespace...
            smooks = new Smooks(new DefaultApplicationContextBuilder().setClassLoader(classLoader).setRegisterSystemResources(false).build());
            setExtensionDigestOn();
            try {
                Registry registry = smooks.getApplicationContext().getRegistry();
                ResourceConfigSeq extConfigList = new DefaultResourceConfigSeq(baseURI);

                XMLConfigDigester configDigester = new XMLConfigDigester(extConfigList);

                configDigester.extendedConfigDigesters = extendedConfigDigesters;
                configDigester.digestConfigRecursively(new InputStreamReader(ClassUtils.getResourceAsStream(resourcePath, classLoader)), baseURI);
                registry.registerResourceConfigSeq(extConfigList);
            } catch (Exception e) {
                throw new SmooksConfigException("Failed to construct Smooks instance for processing extended configuration resource '" + resourcePath + "'.", e);
            } finally {
                setExtensionDigestOff();
            }

            // And add it to the Map of extension digesters...
            extendedConfigDigesters.put(configNamespace, smooks);
        }

        return smooks;
    }

    private void assertExtendedConfigOK(String configNamespace, String resourcePath) {
        InputStream resourceStream = ClassUtils.getResourceAsStream(resourcePath, classLoader);

        if (resourceStream == null) {
            throw new SmooksConfigException("Unable to locate Smooks digest configuration '" + resourcePath + "' for extended resource configuration namespace '" + configNamespace + "'.  This resource must be available on the classpath.");
        }

        Document configDoc;
        try {
            configDoc = XmlUtil.parseStream(resourceStream);
        } catch (Exception e) {
            throw new SmooksConfigException("Unable to parse namespace URI '" + configNamespace + "'.", e);
        }

        XsdDOMValidator validator;
        try {
            validator = new XsdDOMValidator(configDoc);
        } catch (SAXException e) {
            throw new SmooksConfigException("Unable to create XsdDOMValidator instance for extended resource config '" + resourcePath + "'.", e);
        }

        String defaultNS = validator.getDefaultNamespace().toString();
        if (!XSD_V20.equals(defaultNS)) {
            throw new SmooksConfigException("Extended resource configuration '" + resourcePath + "' default namespace must be a valid Smooks configuration namespace.");
        }
    }

    private static boolean isExtensionConfig() {
        return EXTENSION_DIGEST_ON.get();
    }

    private static void setExtensionDigestOn() {
        EXTENSION_DIGEST_ON.set(true);
    }

    private static void setExtensionDigestOff() {
        EXTENSION_DIGEST_ON.set(false);
    }

    private void digestConditions(Element conditionsElement) {
        NodeList conditions = conditionsElement.getElementsByTagName("condition");

        for (int i = 0; i < conditions.getLength(); i++) {
            Element conditionElement = (Element) conditions.item(i);
            String id = DomUtils.getAttributeValue(conditionElement, "id");

            if (id != null) {
                addConditionEvaluator(id, digestCondition(conditionElement));
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public ExpressionEvaluator digestCondition(Element conditionElement) throws SmooksConfigException {
        String idRef = DomUtils.getAttributeValue(conditionElement, "idRef");

        if (idRef != null) {
            return getConditionEvaluator(idRef);
        } else {
            String evaluatorClassName = DomUtils.getAttributeValue(conditionElement, "evaluator");

            String evaluatorConditionExpression = DomUtils.getAllText(conditionElement, true);
            if (evaluatorConditionExpression == null || evaluatorConditionExpression.trim().equals("")) {
                throw new SmooksConfigException("smooks-resource/condition must specify a condition expression as child text e.g. <condition evaluator=\"....\">A + B > C</condition>.");
            }

            // And construct it...
            return expressionEvaluatorFactory.create(evaluatorClassName, evaluatorConditionExpression);
        }
    }

    private void digestProfiles(Element profilesElement) {
        NodeList configNodes = profilesElement.getChildNodes();

        for (int i = 0; i < configNodes.getLength(); i++) {
            if (configNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element profileNode = (Element) configNodes.item(i);
                String baseProfile = DomUtils.getAttributeValue(profileNode, "base-profile");
                String subProfiles = DomUtils.getAttributeValue(profileNode, "sub-profiles");
                DefaultProfileSet profileSet = new DefaultProfileSet(baseProfile);

                if (subProfiles != null) {
                    profileSet.addProfiles(subProfiles.split(","));
                }

                resourceConfigSeq.add(profileSet);
            }
        }
    }

    private void digestParameters(Element resourceConfigElement, ResourceConfig resourceConfig) {
        NodeList configNodes = resourceConfigElement.getElementsByTagName("param");

        for (int i = 0; i < configNodes.getLength(); i++) {
            Element paramNode = (Element) configNodes.item(i);
            String paramName = DomUtils.getAttributeValue(paramNode, "name");
            String paramType = DomUtils.getAttributeValue(paramNode, "type");
            String paramValue = DomUtils.getAllText(paramNode, true);

            Parameter<?> paramInstance = resourceConfig.setParameter(paramName, paramType, paramValue);
            paramInstance.setXml(paramNode);
        }
    }

    public String getCurrentPath() {
        StringBuilder pathBuilder = new StringBuilder();

        for (int i = configStack.size() - 1; i >= 0; i--) {
            pathBuilder.insert(0, "]");
            pathBuilder.insert(0, configStack.get(i).configFile);
            pathBuilder.insert(0, "/[");
        }

        return pathBuilder.toString();
    }

    private void pushConfig(String file, URI fileURI) {
        for (SmooksConfig smooksConfig : configStack) {
            if (fileURI.equals(smooksConfig.fileURI)) {
                throw new SmooksConfigException("Invalid circular reference to config file '" + fileURI + "' from inside config file '" + getCurrentPath() + "'.");
            }
        }

        SmooksConfig config = new SmooksConfig(file);

        config.parent = configStack.peek();
        config.fileURI = fileURI;
        configStack.push(config);
    }

    private void popConfig() {
        configStack.pop();
    }

    public void addConditionEvaluator(String id, ExpressionEvaluator evaluator) {
        assertUniqueConditionId(id);
        configStack.peek().conditionEvaluators.put(id, evaluator);
    }

    public ExpressionEvaluator getConditionEvaluator(String idRef) {
        SmooksConfig smooksConfig = configStack.peek();

        while (smooksConfig != null) {
            ExpressionEvaluator evaluator = smooksConfig.conditionEvaluators.get(idRef);
            if (evaluator != null) {
                return evaluator;
            }
            smooksConfig = smooksConfig.parent;
        }

        throw new SmooksConfigException("Unknown condition idRef '" + idRef + "'.");
    }

    private void assertUniqueConditionId(String id) {
        if (configStack.peek().conditionEvaluators.containsKey(id)) {
            throw new SmooksConfigException("Duplicate condition ID '" + id + "'.");
        }
    }

    private static class SmooksConfig {

        private String defaultNS;
        private SmooksConfig parent;
        private final String configFile;
        private final Map<String, ExpressionEvaluator> conditionEvaluators = new HashMap<String, ExpressionEvaluator>();
        public URI fileURI;

        private SmooksConfig(String configFile) {
            this.configFile = configFile;
        }
    }
}
