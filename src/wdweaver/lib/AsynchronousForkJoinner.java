package wdweaver.lib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.RequestMethod;
import com.google.appengine.repackaged.com.google.protobuf.ByteString;
import com.google.appengine.repackaged.com.google.protobuf.InvalidProtocolBufferException;
import com.google.apphosting.api.ApiProxy;

/**
 * Execute asynchronous operation on Google App Engine
 * if this request does as spinning up request, it must be failed. 
 * 
 * @author kenta.wataru@gmail.com
 *
 * @param <R> response value type
 * @param <Q> request value type
 */
public class AsynchronousForkJoinner<T extends Serializable> {

	private static final Double DEFAULT_DEADLINE = 5.0;
	private static final int MAX_DUMMY_REQUEST = 3;
	private static final Logger logger = Logger.getLogger(AsynchronousForkJoinner.class.getName());
	
	private class Join {
		Join(Callable<T> request, Future<byte[]> future) {
			this.request = request;
			this.future = future;
		}
		public Callable<T> request;
		public Future<byte[]> future;
		
	}
	
	/** target proxy url */
	private String url;
	/** timeout deadline */
	private Double deadline;
	/** requstack */
	private Stack<Join> joinStack;

	public AsynchronousForkJoinner() {
		initialize(DEFAULT_DEADLINE);
	}
	
	public AsynchronousForkJoinner(final Double deadline) {
		initialize(deadline);
	}
	
	void initialize(final Double deadline) {

		ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
		if (null != environment.getAttributes().get("debug")) {
			this.url = "http://localhost:8888/forkface";
		} else {
			String version = environment.getVersionId();
			version = version.substring(0,version.indexOf("."));
			this.url = "http://"+version+".latest."+environment.getAppId()+".appspot.com/forkface";
		}
		this.deadline = deadline;
		this.joinStack = new Stack<Join>();
	}
	
	byte[] serialize(Serializable s) throws IOException {
		ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
		ObjectOutputStream objectstream = new ObjectOutputStream(bytestream);
		objectstream.writeObject(s);
		objectstream.flush();
		objectstream.close();
		byte[] serializedData = bytestream.toByteArray().clone();
		bytestream.close();
		return serializedData;
	}
		
	Serializable deserialize(byte[] response) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bytestream = new ByteArrayInputStream(response);
		ObjectInputStream objectstream = new ObjectInputStream(bytestream);
		@SuppressWarnings("unchecked")
		T deserializedObject = (T)objectstream.readObject();
		objectstream.close();
		bytestream.close();
		return deserializedObject;
	}
	
	public <Q extends Callable<T> & Serializable> AsynchronousForkJoinner<T> fork(Q request) throws IOException {
		joinStack.push(new Join(request,internalFork(request)));
		return this;
	}
	
	Future<byte[]> internalFork(Serializable request) throws IOException {
		URLFetchServicePb.URLFetchRequest.Builder fetchRequestBuilder = URLFetchServicePb.URLFetchRequest.newBuilder();
		fetchRequestBuilder.setMethod(RequestMethod.POST);
		fetchRequestBuilder.setDeadline(deadline);
		fetchRequestBuilder.setFollowRedirects(true);
		if(request!=null) {
			fetchRequestBuilder.setPayload(ByteString.copyFrom(serialize(request)));
		}
		fetchRequestBuilder.setUrl(url);
		URLFetchServicePb.URLFetchRequest fetch = fetchRequestBuilder.build();
		ApiProxy.ApiConfig config = new ApiProxy.ApiConfig();
		config.setDeadlineInSeconds(deadline);
		@SuppressWarnings("unchecked")
		ApiProxy.Delegate<ApiProxy.Environment> delegate = ApiProxy.getDelegate();

		Future<byte[]> future = delegate.makeAsyncCall(
				ApiProxy.getCurrentEnvironment(),
				"urlfetch",	"Fetch", fetch.toByteArray(), config);
		return future;
	}
	
	public Map<Callable<T>,T> join() {
		HashMap<Callable<T>,T> map = new HashMap<Callable<T>,T>();
		 
		try {
			for(int i=0; i<MAX_DUMMY_REQUEST - joinStack.size();i++)
				internalFork(null);
		} catch (IOException e) {
			logger.log(Level.WARNING,"error at dummy internalFork",e);
		}
		while(joinStack.size()>0) {
			Join joinObject = joinStack.pop();
			try {
				byte[] rawResponse = joinObject.future.get();
				URLFetchServicePb.URLFetchResponse response = URLFetchServicePb.URLFetchResponse.parseFrom(rawResponse);
				byte[] serializedData = response.getContent().toByteArray();
				@SuppressWarnings("unchecked")
				T responseObject = (T)deserialize(serializedData);
				map.put(joinObject.request,responseObject);
			} catch (InterruptedException e) {
				logger.log(Level.WARNING,String.format("error at %s",joinObject.request.toString()),e);
				continue;
			} catch (ExecutionException e) {
				logger.log(Level.WARNING,String.format("error at %s",joinObject.request.toString()),e);
				continue;
			} catch (InvalidProtocolBufferException e) {
				logger.log(Level.WARNING,String.format("error at %s",joinObject.request.toString()),e);
				continue;
			} catch (IOException e) {
				logger.log(Level.WARNING,String.format("error at %s",joinObject.request.toString()),e);
				continue;
			} catch (ClassNotFoundException e) {
				logger.log(Level.WARNING,String.format("error at %s",joinObject.request.toString()),e);
				continue;
			}
		}
		return map;
	}
}
