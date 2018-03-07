package pl.itcraft.soma.api.endpoints;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.api.server.spi.ServiceException;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.response.InternalServerErrorException;

import pl.itcraft.soma.core.error.ErrorStatus;

@Api(
	    name = "echo",
	    version = "v1",
	    namespace =
	      @ApiNamespace(
	        ownerDomain = "echo.example.com",
	        ownerName = "echo.example.com",
	        packagePath = ""
	      ),
	    // [START_EXCLUDE]
	    issuers = {
//	      @ApiIssuer(
//	        name = "firebase",
//	        issuer = "https://securetoken.google.com/soma-develop",
//	        jwksUri = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com")
	    }
	    // [END_EXCLUDE]
	    )
public class TestEndpoint {

	private final Logger logger = Logger.getLogger(TestEndpoint.class.getName());

	@ApiMethod(name = "testFix", path="testFix", httpMethod = HttpMethod.POST)
	public void testFix(@Named("userId") Long userId) throws ServiceException {
		try {

//			BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
//		    BlobKey blobKey = blobstoreService.createGsBlobKey(
//		        "/gs/" + );
//			List<User> users = ObjectifyService.ofy().load().type(User.class).list();
//			for (User user : users) {
//				user.setEmail(user.getEmail());
//				user.setUsername(user.getUsername());
//
//				logger.info("User email : " + user.getEmail());
//				logger.info("User username : " + user.getUsername());
//
//				ObjectifyService.ofy().save().entity(user).now();
//			}


		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "coconutTest", path="coconutTest", httpMethod = HttpMethod.POST)
	public void coconutTest(@Named("userId") Long userId) throws ServiceException {
		try {
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
