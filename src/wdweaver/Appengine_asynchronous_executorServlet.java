package wdweaver;

import java.io.IOException;
import java.io.Serializable;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import wdweaver.lib.AsynchronousForkJoinner;

@SuppressWarnings("serial")
public class Appengine_asynchronous_executorServlet extends HttpServlet {
	
	private Logger logger = Logger.getLogger(Appengine_asynchronous_executorServlet.class.getName());
	private ThreadLocal<Boolean> spinup = new ThreadLocal<Boolean>();
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

	
	@Override
	public void init(ServletConfig config) throws ServletException {
		String uuid = (String) config.getServletContext().getAttribute("uuid");
		if(uuid==null) {
			config.getServletContext().setAttribute("uuid",UUID.randomUUID().toString());
		}
		super.init(config);
		spinup.set(true);
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
	
	@Override
	public void service(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		String uuid = (String) getServletContext().getAttribute("uuid");
		logger.warning(uuid);
		if(spinup.get()) {
			logger.warning("spin up request");
			spinup.set(false);
			String red = req.getRequestURI()+((req.getQueryString()==null)?"":"?"+req.getQueryString());
			logger.warning(red);
			resp.sendRedirect(red);
			return;
		}
		String uri = req.getRequestURI();
		if(uri.endsWith("index")) {
			resp.setContentType("text/plain");
			resp.getWriter().println("Hello, world");
			return;
		}
		if(uri.endsWith("wait1")) {
			String d = req.getParameter("d");
			String w = req.getParameter("w");
			Double dd = Double.valueOf(d);
			Integer ww = Integer.valueOf(w);
			AsynchronousForkJoinner<TestValue> t = new AsynchronousForkJoinner<TestValue>(dd);
			//for(int i=0; i<5; i++) {
				t.fork(new WaitRequest(ww));
			//}
			t.join();
			return;
		}
		if(uri.endsWith("wait2")) {
			String n = req.getParameter("n");
			String d = req.getParameter("d");
			String w = req.getParameter("w");
			Integer nn = Integer.valueOf(n);
			Double dd = Double.valueOf(d);
			Integer ww = Integer.valueOf(w);
			AsynchronousForkJoinner<TestValue> t = new AsynchronousForkJoinner<TestValue>(dd);
			for(int i=0; i<nn; i++) {
				t.fork(new WaitRequest(ww));
			}
			t.join();
			return;
		}

		
	}
}
