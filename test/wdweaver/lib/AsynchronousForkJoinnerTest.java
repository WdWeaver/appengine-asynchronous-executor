package wdweaver.lib;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.appengine.api.datastore.dev.LocalDatastoreService;
import com.google.appengine.tools.development.ApiProxyLocalImpl;
import com.google.appengine.tools.development.DevAppServer;
import com.google.appengine.tools.development.DevAppServerFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;

public class AsynchronousForkJoinnerTest {
	
	public class MyTestEnvironment implements Environment {
		public String getAppId() { return "asynchronousforkjoinner"; }
		public String getVersionId() { return "1"; }
		public Map<String, Object> getAttributes() { 
			HashMap<String,Object> map = new HashMap<String,Object>(); 
			map.put("debug", true);
			return map;
		}
		public String getRequestNamespace() { return ""; }
		public String getAuthDomain() { throw new UnsupportedOperationException(); }
		public String getEmail() {	throw new UnsupportedOperationException();	}
		public boolean isAdmin() { throw new UnsupportedOperationException(); }
		public boolean isLoggedIn() { throw new UnsupportedOperationException(); }
	}

	static DevAppServerFactory factory;
	static DevAppServer server;
	
	@BeforeClass
	public static void startTest() throws Exception {
		factory = new DevAppServerFactory();
		server = factory.createDevAppServer(new File("war"), "0.0.0.0", 8888);
		server.start();
	}
	
	@AfterClass
	public static void finishTest() throws Exception {
		if(server !=null)
			server.shutdown();
	}
	
	@Before
	public void setUp() throws Exception {
		ApiProxy.setEnvironmentForCurrentThread(new MyTestEnvironment());  
		ApiProxy.setDelegate(new ApiProxyLocalImpl(new File("war")){});
		ApiProxyLocalImpl proxy = (ApiProxyLocalImpl) ApiProxy.getDelegate();
		proxy.setProperty(LocalDatastoreService.NO_STORAGE_PROPERTY, Boolean.TRUE.toString());	
	}
	
	@After
	public void tearDown() throws Exception {
		ApiProxyLocalImpl proxy = (ApiProxyLocalImpl) ApiProxy.getDelegate();  
		LocalDatastoreService datastoreService = (LocalDatastoreService)proxy.getService("datastore_v3");  
		datastoreService.clearProfiles();
		ApiProxy.setDelegate(null);  
		ApiProxy.setEnvironmentForCurrentThread(null);  
	}
	
	@Test
	public void testDefault() {
		
	}
	
	public static class TestValue implements Serializable {
		private static final long serialVersionUID = 1L;
		private String val = null;
		public TestValue() {
			
		}
		public TestValue(String val) {
			this.val = val;
		}
		public void setVal(String val) {
			this.val = val;
		}
		public String getVal() {
			return val;
		}
	}
	
	public static class TestRequest implements Callable<TestValue>, Serializable {
		private static final long serialVersionUID = 1L;
		@Override
		public TestValue call() throws Exception {
			return new TestValue(Long.toBinaryString(new Random().nextLong()));
		}
	}

	public static class WaitRequest implements Callable<TestValue>, Serializable {
		private static final long serialVersionUID = 1L;
		private final long wait;
		public WaitRequest(long wait) {
			this.wait = wait;
		}
		@Override
		public TestValue call() throws Exception {
			Thread.sleep(wait);
			return new TestValue(Long.toBinaryString(new Random().nextLong()));
		}
	}
	
	@Test
	public void testSerializations() throws IOException, ClassNotFoundException {
		AsynchronousForkJoinner<TestValue> t = new AsynchronousForkJoinner<TestValue>();
		TestRequest r1 = new TestRequest();
		byte[] d1 = t.serialize(r1);
		TestRequest r2 = (TestRequest)t.deserialize(d1);
		byte[] d2 = t.serialize(r2);
		assertArrayEquals(d1,d2);
	}

	@Test
	public void testSingleFork() throws IOException {
		AsynchronousForkJoinner<TestValue> t = new AsynchronousForkJoinner<TestValue>();
		t.fork(new TestRequest());
		Map<Callable<TestValue>,TestValue> r = t.join();
		assertNotNull(r);
		Assert.assertNotSame(0, r.size());
		for(TestValue o: r.values()) {
			assertNotNull(o);
			assertNotNull(o.getVal());
			System.out.println(o.getVal());
		}
	}

	@Test
	public void testCollectionFork() throws IOException {
		AsynchronousForkJoinner<TestValue> t = new AsynchronousForkJoinner<TestValue>();
		for(int i=0; i<10; i++) {
			t.fork(new TestRequest());
		}
		Map<Callable<TestValue>,TestValue> r = t.join();
		assertNotNull(r);
		Assert.assertNotSame(0, r.size());
		Assert.assertEquals(10, r.size());
		for(TestValue o: r.values()) {
			assertNotNull(o);
			assertNotNull(o.getVal());
			System.out.println(o.getVal());
		}
	}

	@Test
	public void testWaitFork1() throws IOException {
		AsynchronousForkJoinner<TestValue> t = new AsynchronousForkJoinner<TestValue>(10.0);
		for(int i=0; i<10; i++) {
			t.fork(new WaitRequest(1000));
		}
		Map<Callable<TestValue>,TestValue> r = t.join();
		assertNotNull(r);
		Assert.assertNotSame(0, r.size());
		Assert.assertEquals(10, r.size());
		for(TestValue o: r.values()) {
			assertNotNull(o);
			assertNotNull(o.getVal());
			System.out.println(o.getVal());
		}
	}

	@Test
	public void testMaxWaitSingleFork() throws IOException {
		AsynchronousForkJoinner<TestValue> t = new AsynchronousForkJoinner<TestValue>(10.0);
		t.fork(new WaitRequest(4000));
		Map<Callable<TestValue>,TestValue> r = t.join();
		assertNotNull(r);
		Assert.assertNotSame(0, r.size());
		for(TestValue o: r.values()) {
			assertNotNull(o);
			assertNotNull(o.getVal());
			System.out.println(o.getVal());
		}
	}
	
}
