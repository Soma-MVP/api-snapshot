package pl.itcraft.soma.api.endpoints;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.google.api.server.spi.ServiceException;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Description;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.InternalServerErrorException;

import pl.itcraft.soma.api.dto.ItemDto;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.Item;
import pl.itcraft.soma.core.model.entities.ItemContainer;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.model.enums.Category;
import pl.itcraft.soma.core.service.ItemService;

@Api(
	    name = EndpointUtils.ENDPOINT_API_NAME,
	    version = "v1",
	    namespace =
	      @ApiNamespace(
	        ownerDomain = EndpointUtils.ENDPOINT_OWNER_DOMAIN,
	        ownerName = EndpointUtils.ENDPOINT_OWNER_NAME
	      )
	    )
public class ItemEndpoint {

	private final Logger logger = Logger.getLogger(ItemEndpoint.class.getName());
	private final ItemService itemService = new ItemService();
	private final EndpointUtils endpointUtils = new EndpointUtils();

	@ApiMethod(name = "addItem", path = "add-item", httpMethod = HttpMethod.POST)
	public ItemDto addItem(HttpServletRequest request,
										@Named("id") @Nullable Long id,
										@Named("title") String title,
										@Named("description") String description,
										@Named("category") Category category,
										@Named("photoIds") @Description("List of photo IDs, which is returned in response of POST to URL of getPhotoUploadUrl() method")
										@Nullable List<Long> photoIds,
										@Named("allowReselling") Boolean allowReselling,
										@Named("price") Long price,
										@Named("priceUnit") String priceUnit,
										@Named("latitude") Double latitude,
										@Named("longitude") Double longitude,
										@Named("locationName") String locationName,
										@Named("isDraft") @Nullable Boolean isDraft) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			ItemContainer itemContainer = itemService.saveItem(id, user, title, description, category, photoIds, allowReselling, price, priceUnit, latitude, longitude, locationName, isDraft);
			return new ItemDto(itemContainer.getItem(), itemContainer.getItemPhotos());
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "getItem", path = "get-item", httpMethod = HttpMethod.GET)
	public ItemDto getItem(HttpServletRequest request, @Named("id") Long id) throws ServiceException {
		try {
			endpointUtils.authenticateUser(request);
			Item item = itemService.getItem(id);
			itemService.enqueueItemView(id);
			return new ItemDto(item, itemService.getItemPhotos(item.getId()));
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "getUserItems", path = "get-user-items", httpMethod = HttpMethod.GET)
	public List<ItemDto> getUserItems(HttpServletRequest request, @Named("userId") Long userId) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			List<Item> items = itemService.getUserItems(user);
			List<ItemDto> result = new ArrayList<>();
			for(Item item : items) {
				result.add(new ItemDto(item, itemService.getItemPhotos(item.getId())));
			}
			return result;
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "searchItems", path = "search-items", httpMethod = HttpMethod.POST)
	public CollectionResponse<ItemDto> searchItems(HttpServletRequest request,
			@Named("keyword") @Nullable String keyword,
			@Named("category") @Nullable Category category,
			@Named("latitude") @Nullable Double latitude,
			@Named("longitude") @Nullable Double longitude,
			@Named("minRange") @Description("Minimum range in meters") @Nullable Integer minRange,
			@Named("maxRange") @Description("Maximum range in meters") @Nullable Integer maxRange,
			@Named("followersItems") @Description("Send true if you want only items from people that you follow") @Nullable Boolean followersItems,
			@Named("friendsItems") @Description("Send true if you want only items from your friends") @Nullable Boolean friendsItems,
			@Named("limit") @Nullable Integer limit,
			@Named("nextPageToken") @Nullable String nextPageToken) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			CollectionResponse<Item> items = itemService.searchItems(keyword, category, latitude, longitude, minRange, maxRange, user, followersItems, friendsItems, nextPageToken, limit);
			List<ItemDto> itemDtos = new ArrayList<>();
			for(Item item : items.getItems()) {
				itemDtos.add(new ItemDto(item, itemService.getItemPhotos(item.getId())));
			}
			return CollectionResponse.<ItemDto> builder().setItems(itemDtos).setNextPageToken(items.getNextPageToken()).build();
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiMethod(name = "publishDraftItem", path = "publish-draft-item", httpMethod = HttpMethod.POST)
	public void publishDraftItem(HttpServletRequest request,
			@Named("id") Long id) throws ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			itemService.publishDraftItem(id, user);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "error on executing endpoint", e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}


}
