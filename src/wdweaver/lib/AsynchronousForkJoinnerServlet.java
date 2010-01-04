package wdweaver.lib;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Execute asynchronous operation on Google App Engine
 * if request does as spinning up request, it must be re-entry. 
 * 
 * @author kenta.wataru@gmail.com
 *
 * @param <R> response value type
 * @param <Q> request value type
 */
public class AsynchronousForkJoinnerServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(AsynchronousForkJoinnerServlet.class.getName());
	private ThreadLocal<Boolean> spinup = new ThreadLocal<Boolean>();

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		String uuid = (String) config.getServletContext().getAttribute("uuid");
		if(uuid==null) {
			config.getServletContext().setAttribute("uuid",UUID.randomUUID().toString());
			spinup.set(true);
		} else {
			spinup.set(false);
		}
	}


	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			logger.entering(AsynchronousForkJoinnerServlet.class.getName(), "service");
			String uuid = (String) getServletContext().getAttribute("uuid");
			logger.warning(uuid);
			//for test environment
			if(spinup.get()!=null&&spinup.get()) {
				spinup.set(false);
				String red = request.getRequestURI()+((request.getQueryString()==null)?"":"?"+request.getQueryString());
				response.sendRedirect(red);
				return;
			}
			//check if request buffer is empty.
			if (request.getInputStream()==null||request.getInputStream().available() == 0) {
				response.setStatus(HttpServletResponse.SC_OK);
				return;
			}
			ObjectInputStream inboundStream = new ObjectInputStream(request.getInputStream());
			Callable<?> callableRequest = (Callable<?>) inboundStream.readObject();
			Object callableResponse = callableRequest.call();
			ObjectOutputStream outboundStream = new ObjectOutputStream(response.getOutputStream());
			response.setStatus(HttpServletResponse.SC_OK);
			outboundStream.writeObject(callableResponse);
			response.flushBuffer();
		} catch (ClassNotFoundException e) {
			logger.log(Level.WARNING,"error",e);
			throw new ServletException(e);
		} catch (Exception e) {
			logger.log(Level.WARNING,"error",e);
			throw new ServletException(e);
		} finally {
			logger.exiting(AsynchronousForkJoinnerServlet.class.getName(), "service");
		}
	
	}

}
