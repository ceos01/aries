package org.apache.aries.spifly;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.apache.aries.spifly.api.SpiFlyConstants;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.framework.wiring.BundleWiring;

public class ClientWeavingHookTest {
    @Before
    public void setUp() {
        Activator.activator = new Activator();
    }
        
    @Test
    public void testClientWeavingHookBasicServiveLoaderUsage() throws Exception {
        Bundle spiFlyBundle = mockSpiFlyBundle("spifly", Version.parseVersion("1.9.4"));               
       
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.SPI_CONSUMER_HEADER, "true");
        Bundle consumerBundle = mockConsumerBundle(headers, spiFlyBundle);

        WeavingHook wh = new ClientWeavingHook(spiFlyBundle.getBundleContext());
        
        // Weave the TestClient class.
        URL clsUrl = getClass().getResource("TestClient.class");
        Assert.assertNotNull("precondition", clsUrl);
        WovenClass wc = new MyWovenClass(clsUrl, "org.apache.aries.spifly.TestClient", consumerBundle);
        Assert.assertEquals("Precondition", 0, wc.getDynamicImports().size());
        wh.weave(wc);
        Assert.assertEquals(1, wc.getDynamicImports().size());
        String di1 = "org.apache.aries.spifly;bundle-symbolic-name=spifly;bundle-version=1.9.4";
        String di2 = "org.apache.aries.spifly;bundle-version=1.9.4;bundle-symbolic-name=spifly";
        String di = wc.getDynamicImports().get(0);
        Assert.assertTrue("Weaving should have added a dynamic import", di1.equals(di) || di2.equals(di));        
                
        // ok the weaving is done, now prepare the registry for the call
        Bundle providerBundle = mockProviderBundle("impl1", 1, "META-INF/services/org.apache.aries.mytest.MySPI");        
        Activator.activator.registerProviderBundle("org.apache.aries.mytest.MySPI", providerBundle);
        
        // Invoke the woven class and check that it propertly sets the TCCL so that the 
        // META-INF/services/org.apache.aries.mytest.MySPI file from impl1 is visible.
        Class<?> cls = wc.getDefinedClass();
        Method method = cls.getMethod("test", new Class [] {String.class});
        Object result = method.invoke(cls.newInstance(), "hello");
        Assert.assertEquals("olleh", result);
    }

    @Test
    public void testClientWeavingHookMultipleProviders() throws Exception {
        Bundle spiFlyBundle = mockSpiFlyBundle();

        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.SPI_CONSUMER_HEADER, "true");
        Bundle consumerBundle = mockConsumerBundle(headers, spiFlyBundle);

        WeavingHook wh = new ClientWeavingHook(spiFlyBundle.getBundleContext());

        // Weave the TestClient class.
        URL clsUrl = getClass().getResource("TestClient.class");
        WovenClass wc = new MyWovenClass(clsUrl, "org.apache.aries.spifly.TestClient", consumerBundle);
        wh.weave(wc);

        Bundle providerBundle1 = mockProviderBundle("impl1", 1, "META-INF/services/org.apache.aries.mytest.MySPI");
        Bundle providerBundle2 = mockProviderBundle("impl2", 2, "META-INF/services/org.apache.aries.mytest.MySPI");
        
        // Register in reverse order to make sure the order in which bundles are sorted is correct
        Activator.activator.registerProviderBundle("org.apache.aries.mytest.MySPI", providerBundle2);
        Activator.activator.registerProviderBundle("org.apache.aries.mytest.MySPI", providerBundle1);

        // Invoke the woven class and check that it propertly sets the TCCL so that the 
        // META-INF/services/org.apache.aries.mytest.MySPI files from impl1 and impl2 are visible.
        Class<?> cls = wc.getDefinedClass();
        Method method = cls.getMethod("test", new Class [] {String.class});
        Object result = method.invoke(cls.newInstance(), "hello");
        Assert.assertEquals("All three services should be invoked in the correct order", "ollehHELLO5", result);        
    }
    
    @Test
    public void testClientSpecifyingProvider() throws Exception {
        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.SPI_CONSUMER_HEADER, "java.util.ServiceLoader#load(java.lang.Class);bundle=impl2");

        Bundle providerBundle1 = mockProviderBundle("impl1", 1, "META-INF/services/org.apache.aries.mytest.MySPI");
        Bundle providerBundle2 = mockProviderBundle("impl2", 2, "META-INF/services/org.apache.aries.mytest.MySPI");
        Activator.activator.registerProviderBundle("org.apache.aries.mytest.MySPI", providerBundle1);
        Activator.activator.registerProviderBundle("org.apache.aries.mytest.MySPI", providerBundle2);

        Bundle consumerBundle = mockConsumerBundle(headers, providerBundle1, providerBundle2);
        Bundle spiFlyBundle = mockSpiFlyBundle(consumerBundle, providerBundle1, providerBundle2);        
        WeavingHook wh = new ClientWeavingHook(spiFlyBundle.getBundleContext());

        // Weave the TestClient class.
        URL clsUrl = getClass().getResource("TestClient.class");
        WovenClass wc = new MyWovenClass(clsUrl, "org.apache.aries.spifly.TestClient", consumerBundle);
        wh.weave(wc);

        // Invoke the woven class and check that it propertly sets the TCCL so that the 
        // META-INF/services/org.apache.aries.mytest.MySPI file from impl2 is visible.
        Class<?> cls = wc.getDefinedClass();
        Method method = cls.getMethod("test", new Class [] {String.class});
        Object result = method.invoke(cls.newInstance(), "hello");
        Assert.assertEquals("Only the services from bundle impl2 should be selected", "HELLO5", result);        
    }
    
    @Test
    public void testServiceProviderOverloadUnprocessed() {
        Assert.fail("Test that ServiceLoader.load(2) doesn't get affected");
    }
    
    @Test
    public void testJAXPClientWantsJREImplementation1() throws Exception {
        Bundle systembundle = mockSystemBundle();

        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.SPI_CONSUMER_HEADER, "javax.xml.parsers.DocumentBuilderFactory#newInstance()");
        Bundle consumerBundle = mockConsumerBundle(headers, systembundle);

        WeavingHook wh = new ClientWeavingHook(mockSpiFlyBundle(consumerBundle, systembundle).getBundleContext());

        URL clsUrl = getClass().getResource("JaxpClient.class");
        WovenClass wc = new MyWovenClass(clsUrl, "org.apache.aries.spifly.JaxpClient", consumerBundle);
        wh.weave(wc);
        
        Class<?> cls = wc.getDefinedClass();
        Method method = cls.getMethod("test", new Class [] {});
        Class<?> result = (Class<?>) method.invoke(cls.newInstance());
        Assert.assertEquals("JAXP implementation from JRE", "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", result.getName());                
    }
    
    // If there is an alternate implementation it should always be favoured over the JRE one
    @Test
    public void testJAXPClientWantsAltImplementation1() throws Exception {
        Bundle systembundle = mockSystemBundle();

        Bundle providerBundle = mockProviderBundle("impl3", 1, "META-INF/services/javax.xml.parsers.DocumentBuilderFactory");
        Activator.activator.registerProviderBundle("javax.xml.parsers.DocumentBuilderFactory", providerBundle);

        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.SPI_CONSUMER_HEADER, "javax.xml.parsers.DocumentBuilderFactory#newInstance()");
        Bundle consumerBundle = mockConsumerBundle(headers, providerBundle, systembundle);

        WeavingHook wh = new ClientWeavingHook(mockSpiFlyBundle(consumerBundle, providerBundle, systembundle).getBundleContext());

        URL clsUrl = getClass().getResource("JaxpClient.class");
        WovenClass wc = new MyWovenClass(clsUrl, "org.apache.aries.spifly.JaxpClient", consumerBundle);
        wh.weave(wc);
        
        Class<?> cls = wc.getDefinedClass();
        Method method = cls.getMethod("test", new Class [] {});
        Class<?> result = (Class<?>) method.invoke(cls.newInstance());
        Assert.assertEquals("JAXP implementation from JRE", "org.apache.aries.spifly.impl3.MyAltDocumentBuilderFactory", result.getName());                
    }

    @Test
    public void testJAXPClientWantsJREImplementation2() throws Exception {
        Bundle systembundle = mockSystemBundle();
        
        Bundle providerBundle = mockProviderBundle("impl3", 1, "META-INF/services/javax.xml.parsers.DocumentBuilderFactory");
        Activator.activator.registerProviderBundle("javax.xml.parsers.DocumentBuilderFactory", providerBundle);

        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.SPI_CONSUMER_HEADER, "javax.xml.parsers.DocumentBuilderFactory#newInstance();bundleId=0");
        Bundle consumerBundle = mockConsumerBundle(headers, providerBundle, systembundle);

        WeavingHook wh = new ClientWeavingHook(mockSpiFlyBundle(consumerBundle, providerBundle, systembundle).getBundleContext());

        URL clsUrl = getClass().getResource("JaxpClient.class");
        WovenClass wc = new MyWovenClass(clsUrl, "org.apache.aries.spifly.JaxpClient", consumerBundle);
        wh.weave(wc);
        
        Class<?> cls = wc.getDefinedClass();
        Method method = cls.getMethod("test", new Class [] {});
        Class<?> result = (Class<?>) method.invoke(cls.newInstance());
        Assert.assertEquals("JAXP implementation from JRE", "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", result.getName());                
    }

    @Test
    public void testJAXPClientWantsAltImplementation2() throws Exception {
        Bundle systembundle = mockSystemBundle();

        Bundle providerBundle = mockProviderBundle("impl3", 1, "META-INF/services/javax.xml.parsers.DocumentBuilderFactory");
        Activator.activator.registerProviderBundle("javax.xml.parsers.DocumentBuilderFactory", providerBundle);

        Dictionary<String, String> headers = new Hashtable<String, String>();
        headers.put(SpiFlyConstants.SPI_CONSUMER_HEADER, "javax.xml.parsers.DocumentBuilderFactory#newInstance();bundle=impl3");
        Bundle consumerBundle = mockConsumerBundle(headers, providerBundle, systembundle);
        
        WeavingHook wh = new ClientWeavingHook(mockSpiFlyBundle(consumerBundle, providerBundle, systembundle).getBundleContext());

        URL clsUrl = getClass().getResource("JaxpClient.class");
        WovenClass wc = new MyWovenClass(clsUrl, "org.apache.aries.spifly.JaxpClient", consumerBundle);
        wh.weave(wc);
        
        Class<?> cls = wc.getDefinedClass();
        Method method = cls.getMethod("test", new Class [] {});
        Class<?> result = (Class<?>) method.invoke(cls.newInstance());
        Assert.assertEquals("JAXP implementation from alternative bundle", "org.apache.aries.spifly.impl3.MyAltDocumentBuilderFactory", result.getName());                        
    }
    
    private Bundle mockSpiFlyBundle(Bundle ... bundles) {
        return mockSpiFlyBundle("spifly", new Version(1, 0, 0), bundles);
    }
    
    private Bundle mockSpiFlyBundle(String bsn, Version version, Bundle ... bundles) {
        Bundle spiFlyBundle = EasyMock.createMock(Bundle.class);

        BundleContext spiFlyBundleContext = EasyMock.createMock(BundleContext.class);
        EasyMock.expect(spiFlyBundleContext.getBundle()).andReturn(spiFlyBundle);
        List<Bundle> allBundles = new ArrayList<Bundle>(Arrays.asList(bundles));
        allBundles.add(spiFlyBundle);
        EasyMock.expect(spiFlyBundleContext.getBundles()).andReturn(allBundles.toArray(new Bundle [] {}));
        EasyMock.replay(spiFlyBundleContext);

        EasyMock.expect(spiFlyBundle.getSymbolicName()).andReturn(bsn).anyTimes();
        EasyMock.expect(spiFlyBundle.getVersion()).andReturn(version);
        EasyMock.expect(spiFlyBundle.getBundleContext()).andReturn(spiFlyBundleContext).anyTimes();
        EasyMock.replay(spiFlyBundle);

        return spiFlyBundle;
    }

    private Bundle mockProviderBundle(String subdir, long id, String ... resources) {
        // Set up the classloader that will be used by the ASM-generated code as the TCCL. 
        // It can load a META-INF/services file
        ClassLoader cl = new TestImplClassLoader(subdir, resources);
        
        // The BundleWiring API is used on the bundle by the generated code to obtain its classloader
        BundleWiring bw = EasyMock.createMock(BundleWiring.class);
        EasyMock.expect(bw.getClassLoader()).andReturn(cl);
        EasyMock.replay(bw);
        
        Bundle providerBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(providerBundle.adapt(BundleWiring.class)).andReturn(bw);
        EasyMock.expect(providerBundle.getSymbolicName()).andReturn(subdir).anyTimes();
        EasyMock.expect(providerBundle.getBundleId()).andReturn(id).anyTimes();
        EasyMock.replay(providerBundle);
        return providerBundle;
    }

    private Bundle mockConsumerBundle(Dictionary<String, String> headers, Bundle ... otherBundles) {
        // Create a mock object for the client bundle which holds the code that uses ServiceLoader.load() 
        // or another SPI invocation.
        BundleContext bc = EasyMock.createMock(BundleContext.class);
        
        Bundle consumerBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(consumerBundle.getSymbolicName()).andReturn("testConsumer").anyTimes();
        EasyMock.expect(consumerBundle.getHeaders()).andReturn(headers);
        EasyMock.expect(consumerBundle.getBundleContext()).andReturn(bc);
        EasyMock.expect(consumerBundle.getBundleId()).andReturn(Long.MAX_VALUE).anyTimes();
        EasyMock.replay(consumerBundle);        

        List<Bundle> allBundles = new ArrayList<Bundle>(Arrays.asList(otherBundles));
        allBundles.add(consumerBundle);
        EasyMock.expect(bc.getBundles()).andReturn(allBundles.toArray(new Bundle [] {}));
        EasyMock.replay(bc);

        return consumerBundle;
    }
    
    private Bundle mockSystemBundle() {
        Bundle systemBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(systemBundle.getBundleId()).andReturn(0L).anyTimes();
        EasyMock.replay(systemBundle);
        
        return systemBundle;
    }
            
    private class TestImplClassLoader extends URLClassLoader {
        private final List<String> resources;
        private final String prefix;
        
        public TestImplClassLoader(String subdir, String ... resources) {
            super(new URL [] {}, TestImplClassLoader.class.getClassLoader());
            
            this.prefix = TestImplClassLoader.class.getPackage().getName().replace('.', '/') + "/" + subdir + "/";
            this.resources = Arrays.asList(resources);
        }

        @Override
        public URL findResource(String name) {
            if (resources.contains(name)) {
                return getClass().getClassLoader().getResource(prefix + name);
            } else {
                return super.findResource(name);
            }
        }

        @Override
        public Enumeration<URL> findResources(String name) throws IOException {
            if (resources.contains(name)) {
                return getClass().getClassLoader().getResources(prefix + name);
            } else {
                return super.findResources(name);
            }
        }
    }    

    private static class MyWovenClass implements WovenClass {
        byte [] bytes;
        final String className;
        final Bundle bundleContainingOriginalClass;
        List<String> dynamicImports = new ArrayList<String>();
        boolean weavingComplete = false;
        
        private MyWovenClass(URL clazz, String name, Bundle bundle) throws Exception {
            bytes = Streams.suck(clazz.openStream());
            className = name;
            bundleContainingOriginalClass = bundle;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public void setBytes(byte[] newBytes) {
            bytes = newBytes;
        }

        @Override
        public List<String> getDynamicImports() {
            return dynamicImports;
        }

        @Override
        public boolean isWeavingComplete() {
            return weavingComplete;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public ProtectionDomain getProtectionDomain() {
            return null;
        }

        @Override
        public Class<?> getDefinedClass() {
            try {
                weavingComplete = true;
                return new MyWovenClassClassLoader(className, getBytes(), getClass().getClassLoader(), bundleContainingOriginalClass).loadClass(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public BundleWiring getBundleWiring() {
            BundleWiring bw = EasyMock.createMock(BundleWiring.class);
            EasyMock.expect(bw.getBundle()).andReturn(bundleContainingOriginalClass);
            EasyMock.replay(bw);
            return bw;
        }
    }
    
    private static class MyWovenClassClassLoader extends ClassLoader implements BundleReference {
        private final String className;
        private final Bundle bundle;
        private final byte [] bytes;
        
        public MyWovenClassClassLoader(String className, byte[] bytes, ClassLoader parent, Bundle bundle) {
            super(parent);
            
            this.className = className;
            this.bundle = bundle;
            this.bytes = bytes;            
        }
        
        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(className, bytes, 0, bytes.length);
            } else {
                return super.loadClass(name, resolve);
            }
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return loadClass(name, false);
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }
    }    
}
