/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.geronimo.blueprint.compendium.cm;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.geronimo.blueprint.ComponentDefinitionRegistry;
import org.apache.geronimo.blueprint.NamespaceHandler;
import org.apache.geronimo.blueprint.ParserContext;
import org.apache.geronimo.blueprint.container.Parser;
import org.apache.geronimo.blueprint.container.ParserContextImpl;
import org.apache.geronimo.blueprint.mutable.MutableBeanMetadata;
import org.apache.geronimo.blueprint.mutable.MutableCollectionMetadata;
import org.apache.geronimo.blueprint.mutable.MutableComponentMetadata;
import org.apache.geronimo.blueprint.mutable.MutableIdRefMetadata;
import org.apache.geronimo.blueprint.mutable.MutableMapMetadata;
import org.apache.geronimo.blueprint.mutable.MutableRefMetadata;
import org.apache.geronimo.blueprint.mutable.MutableReferenceMetadata;
import org.apache.geronimo.blueprint.mutable.MutableValueMetadata;
import org.apache.geronimo.blueprint.reflect.MetadataUtil;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.BeanProperty;
import org.osgi.service.blueprint.reflect.CollectionMetadata;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.IdRefMetadata;
import org.osgi.service.blueprint.reflect.MapMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.osgi.service.blueprint.reflect.RefMetadata;
import org.osgi.service.blueprint.reflect.ReferenceMetadata;
import org.osgi.service.blueprint.reflect.ServiceMetadata;
import org.osgi.service.blueprint.reflect.ValueMetadata;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO
 *
 * @author <a href="mailto:dev@geronimo.apache.org">Apache Geronimo Project</a>
 * @version $Rev: 766508 $, $Date: 2009-04-19 22:09:27 +0200 (Sun, 19 Apr 2009) $
 */
public class CmNamespaceHandler implements NamespaceHandler {

    public static final String BLUEPRINT_NAMESPACE = "http://www.osgi.org/xmlns/blueprint/v1.0.0";
    public static final String BLUEPRINT_CM_NAMESPACE = "http://www.osgi.org/xmlns/blueprint-cm/v1.0.0";

    public static final String PROPERTY_PLACEHOLDER_ELEMENT = "property-placeholder";
    public static final String MANAGED_PROPERTIES_ELEMENT = "managed-properties";
    public static final String MANAGED_SERVICE_FACTORY_ELEMENT = "managed-service-factory";
    public static final String CM_PROPERTIES_ELEMENT = "cm-properties";
    public static final String DEFAULT_PROPERTIES_ELEMENT = "default-properties";
    public static final String PROPERTY_ELEMENT = "property";
    public static final String INTERFACES_ELEMENT = "interfaces";
    public static final String VALUE_ELEMENT = "value";
    public static final String MANAGED_COMPONENT_ELEMENT = "managed-component";

    public static final String ID_ATTRIBUTE = "id";
    public static final String PERSISTENT_ID_ATTRIBUTE = "persistent-id";
    public static final String PLACEHOLDER_PREFIX_ATTRIBUTE = "placeholder-prefix";
    public static final String PLACEHOLDER_SUFFIX_ATTRIBUTE = "placeholder-suffix";
    public static final String DEFAULTS_REF_ATTRIBUTE = "defaults-ref";
    public static final String UPDATE_STRATEGY_ATTRIBUTE = "update-strategy";
    public static final String UPDATE_METHOD_ATTRIBUTE = "update-method";
    public static final String FACTORY_PID_ATTRIBUTE = "factory-pid";
    public static final String AUTO_EXPORT_ATTRIBUTE = "auto-export";
    public static final String RANKING_ATTRIBUTE = "ranking";
    public static final String INTERFACE_ATTRIBUTE = "interface";
    public static final String UPDATE_ATTRIBUTE = "update";

    public static final String CONFIG_ADMIN_REFERENCE_NAME = "blueprint.configadmin";

    public static final String AUTO_EXPORT_DISABLED = "disabled";
    public static final String AUTO_EXPORT_INTERFACES = "interfaces";
    public static final String AUTO_EXPORT_CLASS_HIERARCHY = "class-hierarchy";
    public static final String AUTO_EXPORT_ALL = "all-classes";
    public static final String AUTO_EXPORT_DEFAULT = AUTO_EXPORT_DISABLED;
    public static final String RANKING_DEFAULT = "0";

    private static final Logger LOGGER = LoggerFactory.getLogger(CmNamespaceHandler.class);

    private int idCounter;
    
    public URL getSchemaLocation(String namespace) {
        return getClass().getResource("blueprint-cm.xsd");
    }

    public ComponentMetadata parse(Element element, ParserContext context) {
        LOGGER.debug("Parsing element {" + element.getNamespaceURI() + "}" + element.getLocalName());
        ComponentDefinitionRegistry registry = context.getComponentDefinitionRegistry();
        createConfigAdminProxy(context, registry);
        if (nodeNameEquals(element, PROPERTY_PLACEHOLDER_ELEMENT)) {
            return parsePropertyPlaceholder(context, element);
        } else if (nodeNameEquals(element, MANAGED_SERVICE_FACTORY_ELEMENT)) {
            return parseManagedServiceFactory(context, element);
        } else {
            throw new ComponentDefinitionException("Unsupported element: " + element.getNodeName());
        }
    }

    public ComponentMetadata decorate(Node node, ComponentMetadata component, ParserContext context) {
        LOGGER.debug("Decorating node {" + node.getNamespaceURI() + "}" + node.getLocalName());
        ComponentDefinitionRegistry registry = context.getComponentDefinitionRegistry();
        createConfigAdminProxy(context, registry);
        if (node instanceof Element) {
            if (nodeNameEquals(node, MANAGED_PROPERTIES_ELEMENT)) {
                return decorateManagedProperties(context, (Element) node, component);
            } else if (nodeNameEquals(node, CM_PROPERTIES_ELEMENT)) {
                return decorateCmProperties(context, (Element) node, component);
            } else {
                throw new ComponentDefinitionException("Unsupported element: " + node.getNodeName());
            }
        } else {
            throw new ComponentDefinitionException("Illegal use of blueprint cm namespace");
        }
    }

    private ComponentMetadata parsePropertyPlaceholder(ParserContext context, Element element) {
        MutableBeanMetadata metadata = context.createMetadata(MutableBeanMetadata.class);
        metadata.setProcessor(true);
        metadata.setId(getId(context, element));
        metadata.setScope(BeanMetadata.SCOPE_SINGLETON);
        metadata.setRuntimeClass(CmPropertyPlaceholder.class);
        metadata.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
        metadata.addProperty("configAdmin", createRef(context, CONFIG_ADMIN_REFERENCE_NAME));
        metadata.addProperty("persistentId", createValue(context, element.getAttribute(PERSISTENT_ID_ATTRIBUTE)));
        String prefix = element.hasAttribute(PLACEHOLDER_PREFIX_ATTRIBUTE)
                                    ? element.getAttribute(PLACEHOLDER_PREFIX_ATTRIBUTE)
                                    : "${";
        metadata.addProperty("placeholderPrefix", createValue(context, prefix));
        String suffix = element.hasAttribute(PLACEHOLDER_SUFFIX_ATTRIBUTE)
                                    ? element.getAttribute(PLACEHOLDER_SUFFIX_ATTRIBUTE)
                                    : "}";
        metadata.addProperty("placeholderSuffix", createValue(context, suffix));
        String defaultsRef = element.hasAttribute(DEFAULTS_REF_ATTRIBUTE) ? element.getAttribute(DEFAULTS_REF_ATTRIBUTE) : null;
        if (defaultsRef != null) {
            metadata.addProperty("defaultProperties", createRef(context, defaultsRef));
        }
        // Parse elements
        NodeList nl = element.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node instanceof Element) {
                Element e = (Element) node;
                if (BLUEPRINT_CM_NAMESPACE.equals(e.getNamespaceURI())) {
                    if (nodeNameEquals(e, DEFAULT_PROPERTIES_ELEMENT)) {
                        if (defaultsRef != null) {
                            throw new ComponentDefinitionException("Only one of " + DEFAULTS_REF_ATTRIBUTE + " attribute or " + DEFAULT_PROPERTIES_ELEMENT + " element is allowed");
                        }
                        Metadata props = parseDefaultProperties(context, metadata, e);
                        metadata.addProperty("defaultProperties", props);
                    }
                }
            }
        }
        
        validatePlaceholder(metadata, context.getComponentDefinitionRegistry());
        
        return metadata;
    }

    private void validatePlaceholder(MutableBeanMetadata metadata, ComponentDefinitionRegistry registry) {
        String prefix = getPlaceholderProperty(metadata, "placeholderPrefix");
        String suffix = getPlaceholderProperty(metadata, "placeholderSuffix");
        for (String id : registry.getComponentDefinitionNames()) {
            ComponentMetadata component = registry.getComponentDefinition(id);
            if (component instanceof BeanMetadata) {
                BeanMetadata bean = (BeanMetadata) component;
                if (bean.getRuntimeClass() == CmPropertyPlaceholder.class) {
                    String otherPrefix = getPlaceholderProperty(bean, "placeholderPrefix");
                    String otherSuffix = getPlaceholderProperty(bean, "placeholderSuffix");
                    if (prefix.equals(otherPrefix) && suffix.equals(otherSuffix)) {
                        throw new ComponentDefinitionException("Multiple placeholders with the same prefix and suffix are not allowed");
                    }
                }
            }
        }
    }
    
    private String getPlaceholderProperty(BeanMetadata bean, String name) {
        for (BeanProperty property : bean.getProperties()) {
            if (name.equals(property.getName())) {
                ValueMetadata value = (ValueMetadata) property.getValue();
                return value.getStringValue();
            }
        }
        return null;
    }
    
    private Metadata parseDefaultProperties(ParserContext context, MutableBeanMetadata enclosingComponent, Element element) {
        MutableMapMetadata props = context.createMetadata(MutableMapMetadata.class);
        NodeList nl = element.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node instanceof Element) {
                Element e = (Element) node;
                if (BLUEPRINT_CM_NAMESPACE.equals(e.getNamespaceURI())) {
                    if (nodeNameEquals(e, PROPERTY_ELEMENT)) {
                        BeanProperty prop = context.parseElement(BeanProperty.class, enclosingComponent, e);
                        props.addEntry(createValue(context, prop.getName(), String.class.getName()), prop.getValue());
                    }
                }
            }
        }
        return props;
    }

    private ComponentMetadata parseManagedServiceFactory(ParserContext context, Element element) {
        String id = getId(context, element);

        MutableBeanMetadata factoryMetadata = context.createMetadata(MutableBeanMetadata.class);
        generateIdIfNeeded(context, factoryMetadata);
        factoryMetadata.addProperty("id", createValue(context, factoryMetadata.getId()));
        factoryMetadata.setScope(BeanMetadata.SCOPE_SINGLETON);
        factoryMetadata.setRuntimeClass(CmManagedServiceFactory.class);
        factoryMetadata.setInitMethodName("init");
        factoryMetadata.setDestroyMethodName("destroy");
        factoryMetadata.addProperty("configAdmin", createRef(context, CONFIG_ADMIN_REFERENCE_NAME));
        factoryMetadata.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
        factoryMetadata.addProperty("factoryPid", createValue(context, element.getAttribute(FACTORY_PID_ATTRIBUTE)));
        String autoExport = element.hasAttribute(AUTO_EXPORT_ATTRIBUTE) ? element.getAttribute(AUTO_EXPORT_ATTRIBUTE) : AUTO_EXPORT_DEFAULT;
        if (AUTO_EXPORT_DISABLED.equals(autoExport)) {
            autoExport = Integer.toString(ServiceMetadata.AUTO_EXPORT_DISABLED);
        } else if (AUTO_EXPORT_INTERFACES.equals(autoExport)) {
            autoExport = Integer.toString(ServiceMetadata.AUTO_EXPORT_INTERFACES);
        } else if (AUTO_EXPORT_CLASS_HIERARCHY.equals(autoExport)) {
            autoExport = Integer.toString(ServiceMetadata.AUTO_EXPORT_CLASS_HIERARCHY);
        } else if (AUTO_EXPORT_ALL.equals(autoExport)) {
            autoExport = Integer.toString(ServiceMetadata.AUTO_EXPORT_ALL_CLASSES);
        } else {
            throw new ComponentDefinitionException("Illegal value (" + autoExport + ") for " + AUTO_EXPORT_ATTRIBUTE + " attribute");
        }
        factoryMetadata.addProperty("autoExport", createValue(context, autoExport));
        String ranking = element.hasAttribute(RANKING_ATTRIBUTE) ? element.getAttribute(RANKING_ATTRIBUTE) : RANKING_DEFAULT;
        factoryMetadata.addProperty("ranking", createValue(context, ranking));

        List<String> interfaces = null;
        if (element.hasAttribute(INTERFACE_ATTRIBUTE)) {
            interfaces = Collections.singletonList(element.getAttribute(INTERFACE_ATTRIBUTE));
            factoryMetadata.addProperty("interfaces", createList(context, interfaces));
        }

        Parser parser = getParser(context);
        
        // Parse elements
        NodeList nl = element.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node instanceof Element) {
                Element e = (Element) node;
                if (isBlueprintNamespace(e.getNamespaceURI())) {
                    if (nodeNameEquals(e, Parser.INTERFACES_ELEMENT)) {
                        if (interfaces != null) {
                            throw new ComponentDefinitionException("Only one of " + Parser.INTERFACE_ATTRIBUTE + " attribute or " + INTERFACES_ELEMENT + " element must be used");
                        }
                        interfaces = parser.parseInterfaceNames(e);
                        factoryMetadata.addProperty("interfaces", createList(context, interfaces));                    
                    } else if (nodeNameEquals(e, Parser.SERVICE_PROPERTIES_ELEMENT)) { 
                        MapMetadata map = parser.parseServiceProperties(e, factoryMetadata);
                        factoryMetadata.addProperty("serviceProperties", map);
                    } else {
                        // TODO: parse listeners
                    }
                } else if (BLUEPRINT_CM_NAMESPACE.equals(e.getNamespaceURI())) {
                    if (nodeNameEquals(e, MANAGED_COMPONENT_ELEMENT)) {
                        MutableBeanMetadata managedComponent = context.parseElement(MutableBeanMetadata.class, null, e);
                        generateIdIfNeeded(context, managedComponent);
                        managedComponent.setScope(BeanMetadata.SCOPE_PROTOTYPE);
                        // destroy-method on managed-component has different signature than on regular beans
                        // so we'll handle it differently
                        String destroyMethod = managedComponent.getDestroyMethodName();
                        if (destroyMethod != null) {
                            factoryMetadata.addProperty("componentDestroyMethod", createValue(context, destroyMethod));
                            managedComponent.setDestroyMethodName(null);
                        }
                        context.getComponentDefinitionRegistry().registerComponentDefinition(managedComponent);
                        factoryMetadata.addProperty("managedComponentName", createIdRef(context, managedComponent.getId()));
                    }
                }
            }
        }

        context.getComponentDefinitionRegistry().registerComponentDefinition(factoryMetadata);
        
        MutableBeanMetadata mapMetadata = context.createMetadata(MutableBeanMetadata.class);
        mapMetadata.setScope(BeanMetadata.SCOPE_SINGLETON);
        mapMetadata.setId(id);
        mapMetadata.setFactoryComponent(createRef(context, factoryMetadata.getId()));
        mapMetadata.setFactoryMethodName("getServiceMap");
        return mapMetadata;
    }

    private ComponentMetadata decorateCmProperties(ParserContext context, Element element, ComponentMetadata component) {
        generateIdIfNeeded(context, ((MutableComponentMetadata) component));
        MutableBeanMetadata metadata = context.createMetadata(MutableBeanMetadata.class);
        metadata.setProcessor(true);
        metadata.setId(getId(context, element));
        metadata.setRuntimeClass(CmProperties.class);
        String persistentId = element.getAttribute(PERSISTENT_ID_ATTRIBUTE);
        // if persistentId is "" the cm-properties element in nested in managed-service-factory
        // and the configuration object will come from the factory. So we only really need to register
        // ManagedService if the persistentId is not an empty string.
        if (persistentId.length() > 0) {
            metadata.setInitMethodName("init");
            metadata.setDestroyMethodName("destroy");
        }
        metadata.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
        metadata.addProperty("configAdmin", createRef(context, CONFIG_ADMIN_REFERENCE_NAME));
        metadata.addProperty("persistentId", createValue(context, persistentId));
        if (element.hasAttribute(UPDATE_ATTRIBUTE)) {
            metadata.addProperty("update", createValue(context, element.getAttribute(UPDATE_ATTRIBUTE)));
        }
        metadata.addProperty("serviceId", createIdRef(context, component.getId()));
        context.getComponentDefinitionRegistry().registerComponentDefinition(metadata);
        return component;
    }

    private ComponentMetadata decorateManagedProperties(ParserContext context, Element element, ComponentMetadata component) {
        if (!(component instanceof MutableBeanMetadata)) {
            throw new ComponentDefinitionException("Element " + MANAGED_PROPERTIES_ELEMENT + " must be used inside a <bp:bean> element");
        }
        generateIdIfNeeded(context, ((MutableBeanMetadata) component));
        MutableBeanMetadata metadata = context.createMetadata(MutableBeanMetadata.class);
        metadata.setProcessor(true);
        metadata.setId(getId(context, element));
        metadata.setRuntimeClass(CmManagedProperties.class);
        String persistentId = element.getAttribute(PERSISTENT_ID_ATTRIBUTE);
        // if persistentId is "" the managed properties element in nested in managed-service-factory
        // and the configuration object will come from the factory. So we only really need to register
        // ManagedService if the persistentId is not an empty string.
        if (persistentId.length() > 0) {
            metadata.setInitMethodName("init");
            metadata.setDestroyMethodName("destroy");
        }
        metadata.addProperty("blueprintContainer", createRef(context, "blueprintContainer"));
        metadata.addProperty("configAdmin", createRef(context, CONFIG_ADMIN_REFERENCE_NAME));
        metadata.addProperty("persistentId", createValue(context, persistentId));
        if (element.hasAttribute(UPDATE_STRATEGY_ATTRIBUTE)) {
            metadata.addProperty("updateStrategy", createValue(context, element.getAttribute(UPDATE_STRATEGY_ATTRIBUTE)));
        }
        if (element.hasAttribute(UPDATE_METHOD_ATTRIBUTE)) {
            metadata.addProperty("updateMethod", createValue(context, element.getAttribute(UPDATE_METHOD_ATTRIBUTE)));
        }
        metadata.addProperty("beanName", createIdRef(context, component.getId()));
        context.getComponentDefinitionRegistry().registerComponentDefinition(metadata);
        return component;
    }

    /**
     * Create a reference to the ConfigurationAdmin service if not already done
     * and add it to the registry.
     *
     * @param registry the registry to add the config admin reference to
     */
    private void createConfigAdminProxy(ParserContext context, ComponentDefinitionRegistry registry) {
        if (registry.getComponentDefinition(CONFIG_ADMIN_REFERENCE_NAME) == null) {
            MutableReferenceMetadata reference = context.createMetadata(MutableReferenceMetadata.class);
            reference.setId(CONFIG_ADMIN_REFERENCE_NAME);
            reference.addInterfaceName(ConfigurationAdmin.class.getName());
            reference.setAvailability(ReferenceMetadata.AVAILABILITY_MANDATORY);
            reference.setTimeout(300000);
            registry.registerComponentDefinition(reference);
        }
    }

    private static ValueMetadata createValue(ParserContext context, String value) {
        return createValue(context, value, null);
    }

    private static ValueMetadata createValue(ParserContext context, String value, String type) {
        MutableValueMetadata m = context.createMetadata(MutableValueMetadata.class);
        m.setStringValue(value);
        m.setTypeName(type);
        return m;
    }

    private static RefMetadata createRef(ParserContext context, String value) {
        MutableRefMetadata m = context.createMetadata(MutableRefMetadata.class);
        m.setComponentId(value);
        return m;
    }

    private static IdRefMetadata createIdRef(ParserContext context, String value) {
        MutableIdRefMetadata m = context.createMetadata(MutableIdRefMetadata.class);
        m.setComponentId(value);
        return m;
    }

    private static CollectionMetadata createList(ParserContext context, List<String> list) {
        MutableCollectionMetadata m = context.createMetadata(MutableCollectionMetadata.class);
        m.setCollectionClass(List.class);
        m.setValueTypeName(String.class.getName());
        for (String v : list) {
            m.addValue(createValue(context, v, String.class.getName()));
        }
        return m;
    }

    private static boolean nodeNameEquals(Node node, String name) {
        return (name.equals(node.getNodeName()) || name.equals(node.getLocalName()));
    }

    public static boolean isBlueprintNamespace(String ns) {
        return BLUEPRINT_NAMESPACE.equals(ns);
    }

    public String getId(ParserContext context, Element element) {
        if (element.hasAttribute(ID_ATTRIBUTE)) {
            return element.getAttribute(ID_ATTRIBUTE);
        } else {
            return generateId(context);
        }
    }

    public void generateIdIfNeeded(ParserContext context, MutableComponentMetadata metadata) {
        if (metadata.getId() == null) {
            metadata.setId(generateId(context));
        }
    }

    private String generateId(ParserContext context) {
        String id;
        do {
            id = "#cm-" + ++idCounter;
        } while (context.getComponentDefinitionRegistry().containsComponentDefinition(id));
        return id;
    }
    
    private Parser getParser(ParserContext ctx) {
        if (ctx instanceof ParserContextImpl) {
            return ((ParserContextImpl) ctx).getParser();
        }
        throw new RuntimeException("Unable to get parser");
    }

}
