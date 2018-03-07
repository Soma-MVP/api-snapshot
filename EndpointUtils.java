package pl.itcraft.soma.api.endpoints;

import javax.servlet.http.HttpServletRequest;

import com.google.api.server.spi.response.UnauthorizedException;

import pl.itcraft.soma.core.Constants;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.security.AccessStatus;
import pl.itcraft.soma.core.security.AppSecurityManager;
import pl.itcraft.soma.core.security.AuthenticationResponse;

public class EndpointUtils {

	public static final String ENDPOINT_API_NAME = "soma";
	public static final String ADMIN_API_NAME = "somaAdmin";

	public static final String ENDPOINT_OWNER_DOMAIN = "soma.itcraft.pl";
	public static final String ENDPOINT_OWNER_NAME = "soma.itcraft.pl";

	private final AppSecurityManager securityManager = new AppSecurityManager();

	public User authenticateUser(HttpServletRequest request) throws UnauthorizedException {
		String accessToken = request.getHeader(Constants.AUTH_TOKEN_HEADER);

		AuthenticationResponse response = securityManager.checkAccessTokenValidity(accessToken);

		if (response.getStatus().equals(AccessStatus.ACCESS_GRANTED)) {
			return response.getUser();
		} else {
			throw new UnauthorizedException(ErrorStatus.ACCESS_DENIED);
		}
	}

}
