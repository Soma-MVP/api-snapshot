package pl.itcraft.soma.api.endpoints;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.RequestWrapper;

import com.google.api.server.spi.ServiceException;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.response.InternalServerErrorException;

import pl.itcraft.soma.api.dto.OwnProfileDto;
import pl.itcraft.soma.api.dto.UserProfileDto;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.PhotoFile;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.service.UserService;

@Api(
	    name = EndpointUtils.ENDPOINT_API_NAME,
	    version = "v1",
	    namespace =
	      @ApiNamespace(
	        ownerDomain = EndpointUtils.ENDPOINT_OWNER_DOMAIN,
	        ownerName = EndpointUtils.ENDPOINT_OWNER_NAME
	      )
	    )
public class UserEndpoint {

	private final Logger logger = Logger.getLogger(UserEndpoint.class.getName());
	private final UserService userService = new UserService();
	private final EndpointUtils endpointUtils = new EndpointUtils();

	@ApiMethod(name = "register", path="register", httpMethod = HttpMethod.POST)
	public OwnProfileDto register(@Named("email") String email,
				@Named("password") String password,
				@Named("username") String username,
				@Named("fcmRegistrationToken") @Nullable String fcmRegistrationToken,
				@Named("latitude") Double latitude,
				@Named("longitude") Double longitude,
				@Named("locationName") String locationName) throws ServiceException {
		try {
			return new OwnProfileDto(userService.registerUser(email, password, fcmRegistrationToken, username, latitude, longitude, locationName));
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}


	@ApiMethod(name = "registerViaFacebook", path="register-via-facebook", httpMethod = HttpMethod.POST)
	public OwnProfileDto registerViaFacebook(
				@Named("facebookAccessToken") String facebookAccessToken,
				@Named("username") String username,
				@Named("fcmRegistrationToken") @Nullable String fcmRegistrationToken,
				@Named("latitude") Double latitude,
				@Named("longitude") Double longitude,
				@Named("locationName") String locationName) throws ServiceException {
		try {
			return new OwnProfileDto(userService.registerViaFacebook(username, latitude, longitude, locationName, facebookAccessToken, fcmRegistrationToken));
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "login", path="login", httpMethod = HttpMethod.POST)
	public OwnProfileDto login(@Named("email") String email,
			@Named("password") String password,
			@Named("fcmRegistrationToken") @Nullable String fcmRegistrationToken) throws ServiceException {
		try {
			return new OwnProfileDto(userService.loginUser(email, password, fcmRegistrationToken));
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "loginViaFacebook", path = "login-via-facebook", httpMethod = HttpMethod.POST)
	public OwnProfileDto loginUserViaFacebook(@Named("facebookAccessToken") String facebookAccessToken,
								@Named("fcmRegistrationToken") @Nullable String fcmRegistrationToken) throws ServiceException{
		try{
			return new OwnProfileDto(userService.initLoginViaFacebook(facebookAccessToken, fcmRegistrationToken));
		}catch(Exception e){
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}

	}

	@ApiMethod(name = "updateFcmRegistrationId", path="update-fcm-registration-id", httpMethod = HttpMethod.POST)
	public void updateFcmRegistrationId(HttpServletRequest request,
			@Named("fcmRegistrationToken") String fcmRegistrationToken) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);

			userService.updateFcmRegistrationId(user, fcmRegistrationToken);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "logout", path="logout", httpMethod = HttpMethod.POST)
	public void logout(HttpServletRequest request,
			@Named("fcmRegistrationId") @Nullable String fcmRegistrationId) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			userService.logoutUser(user, fcmRegistrationId);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "editProfile", path="edit-profile", httpMethod = HttpMethod.POST)
	public OwnProfileDto editProfile(HttpServletRequest request,
			@Named("username") String username,
			@Named("latitude") Double latitude,
			@Named("longitude") Double longitude,
			@Named("locationName") String locationName,
			@Named("about") String about) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			return new OwnProfileDto(userService.editProfile(user, username, latitude, longitude, locationName, about));
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "resendActivationEmail", path="resend-activation-email", httpMethod = HttpMethod.POST)
	public void resendActivationEmail(@Named("email") String email) throws ServiceException {
		try {
			userService.resendActivationEmail(email);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "activateEmailByToken", path="activate-email-by-token", httpMethod = HttpMethod.POST)
	public void activateEmailByToken(@Named("emailActivationToken") String emailActivationToken) throws ServiceException {
		try {
			userService.activateEmailByToken(emailActivationToken);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "deleteOwnProfilePhoto", path="delete-own-profile-photo", httpMethod = HttpMethod.POST)
	public OwnProfileDto deleteOwnProfilePhoto(HttpServletRequest request) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			return new OwnProfileDto(userService.updateProfilePhoto(user, new PhotoFile()));
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "changePassword", path="change-password", httpMethod = HttpMethod.POST)
	public void changePassword(HttpServletRequest request,
			@Named("oldPassword") String oldPassword, @Named("newPassword")  String newPassword) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			userService.changePassword(user, oldPassword, newPassword);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "getMyProfile", path="get-my-profile", httpMethod = HttpMethod.POST)
	public OwnProfileDto getMyProfile(HttpServletRequest request) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			return new OwnProfileDto(user);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "getUserProfile", path="get-user-profile", httpMethod = HttpMethod.POST)
	public UserProfileDto getUserProfile(HttpServletRequest request, @Named("id") Long id) throws ServiceException {
		try {
			endpointUtils.authenticateUser(request);
			return new UserProfileDto(userService.getOtherUserById(id));
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "sendPasswordResetEmail", path="send-password-reset-email", httpMethod = HttpMethod.POST)
	public void sendPasswordResetEmail(@Named("email") String email) throws ServiceException {
		try {
			userService.sendPasswordResetEmail(email);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "resetPasswordByToken", path="reset-password-by-token", httpMethod = HttpMethod.POST)
	public void resetPasswordByToken(@Named("passwordResetToken") String passwordResetToken, @Named("newPassword") String newPassword) throws ServiceException {
		try {
			userService.resetPasswordByToken(passwordResetToken, newPassword);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}
}