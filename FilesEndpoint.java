package pl.itcraft.soma.api.endpoints;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.google.api.server.spi.ServiceException;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.InternalServerErrorException;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.UploadOptions;

import pl.itcraft.soma.api.dto.FileUploadUrlDto;
import pl.itcraft.soma.core.Constants;
import pl.itcraft.soma.core.error.ErrorStatus;

@Api(
	    name = EndpointUtils.ENDPOINT_API_NAME,
	    version = "v1",
	    namespace =
	      @ApiNamespace(
	        ownerDomain = EndpointUtils.ENDPOINT_OWNER_DOMAIN,
	        ownerName = EndpointUtils.ENDPOINT_OWNER_NAME
	      )
	    )
public class FilesEndpoint {

	private final Logger logger = Logger.getLogger(FilesEndpoint.class.getName());
	private final EndpointUtils endpointUtils = new EndpointUtils();
	private final BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();

	@ApiMethod(name = "getPhotoUploadUrl", path="get-photo-upload-url", httpMethod = HttpMethod.POST, description="Returns a photo upload URL where you can POST image file")
	public FileUploadUrlDto getPhotoUploadUrl(HttpServletRequest request) throws ServiceException {

		try {
			endpointUtils.authenticateUser(request);

			String uploadUrl = blobstoreService.createUploadUrl(Constants.PHOTO_UPLOAD_URL,
					UploadOptions.Builder.withGoogleStorageBucketName(Constants.CLOUD_STORAGE_DEFAULT_BUCKET));

			FileUploadUrlDto dto = new FileUploadUrlDto();
			dto.setUrl(uploadUrl);

			return dto;
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
