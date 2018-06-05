package pl.itcraft.soma.api.servlets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.FileInfo;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;

import com.google.gson.Gson;

import pl.itcraft.soma.api.dto.FileUploadResponseDto;
import pl.itcraft.soma.api.endpoints.EndpointUtils;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.ItemPhoto;
import pl.itcraft.soma.core.model.entities.MessagePhoto;
import pl.itcraft.soma.core.model.entities.PhotoFile;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.model.enums.PhotoUploadType;
import pl.itcraft.soma.core.service.ItemService;
import pl.itcraft.soma.core.service.MessagesService;
import pl.itcraft.soma.core.service.UserService;

public class PhotoUploadServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private final BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
	private final BlobInfoFactory blobInfoFactory = new BlobInfoFactory();
	private final ImagesService imagesService = ImagesServiceFactory.getImagesService();
	private final ItemService itemService = new ItemService();
	private final MessagesService messagesService = new MessagesService();

	private final static Logger logger = Logger.getLogger(PhotoUploadServlet.class.getName());

	private final UserService userService = new UserService();
	private final EndpointUtils endpointUtils = new EndpointUtils();

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

		FileUploadResponseDto dto = new FileUploadResponseDto();

		List<BlobKey> blobKeys = new ArrayList<>();

		Long fileSize = 0L;

		for (FileInfo fileInfo : blobstoreService.getFileInfos(request).get("photo")) {
			blobKeys.add(blobstoreService.createGsBlobKey(fileInfo.getGsObjectName()));
			fileSize = fileInfo.getSize();
		}

		try {
			List<PhotoFile> photoFiles = new ArrayList<>();
			for (BlobKey blobKey : blobKeys) {
				PhotoFile file = new PhotoFile();
				file.setFileKey(blobKey.getKeyString());
				file.setUrl(imagesService.getServingUrl(ServingUrlOptions.Builder.withBlobKey(blobKey)));

				photoFiles.add(file);
			}

			User user = endpointUtils.authenticateUser(request);

			PhotoUploadType type = PhotoUploadType.valueOf(request.getParameter("type"));

			switch(type) {
			case ITEM_PHOTO:
				Integer height = UploadUtils.getIntParameter(request, "height");
				Integer width = UploadUtils.getIntParameter(request, "width");
				ItemPhoto itemPhoto = itemService.saveItemPhoto(user, photoFiles.get(0), height, width);
				dto.setObjectId(itemPhoto.getId());
				break;
			case USER_PROFILE:
				userService.updateProfilePhoto(user, photoFiles.get(0));
				break;
			case MESSAGE_PHOTO:
				MessagePhoto messagePhoto = messagesService.saveMessagePhoto(user, photoFiles.get(0));
				dto.setObjectId(messagePhoto.getId());
				break;
			default:
				break;
			}

			dto.setMessage("success");
			dto.setUrl(photoFiles.get(0).getUrl());

			response.getWriter().write(new Gson().toJson(dto));

		} catch (UnauthorizedException e) {
			logger.log(Level.WARNING, "unauthorized", e);
			dto.setMessage(ErrorStatus.ACCESS_DENIED);
			response.getWriter().write(new Gson().toJson(dto));
			for (BlobKey blobKey : blobKeys) {
				blobstoreService.delete(blobKey);
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "unknown error", e);
			dto.setMessage(e.getMessage());
			response.getWriter().write(new Gson().toJson(dto));
			for (BlobKey blobKey : blobKeys) {
				blobstoreService.delete(blobKey);
			}
		}
	}
}