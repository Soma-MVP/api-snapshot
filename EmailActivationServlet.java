package pl.itcraft.soma.api.servlets;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.server.spi.response.BadRequestException;

import pl.itcraft.soma.api.endpoints.EndpointUtils;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.service.UserService;

public class EmailActivationServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private final static Logger logger = Logger.getLogger(EmailActivationServlet.class.getName());

	private final UserService userService = new UserService();
	private final EndpointUtils endpointUtils = new EndpointUtils();

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String token = request.getParameter("token");
		try {
			userService.activateEmailByToken(token);
			response.getWriter().println("Email activated");
		} catch (BadRequestException e) {
			switch(e.getMessage()) {
			case ErrorStatus.EMAIL_ALREADY_ACTIVATED:
				response.getWriter().println("Email already activated");
				break;
			case ErrorStatus.INVALID_ACTIVATION_TOKEN:
				response.getWriter().println("Invalid activation token");
				break;
			default:
				response.getWriter().println("Unknown error");
				logger.log(Level.WARNING, "unknown error", e);
				break;
			}
		}
	}
}