package pl.itcraft.soma.api.endpoints;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;

import com.google.api.server.spi.ServiceException;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Nullable;
import com.google.api.server.spi.response.BadRequestException;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.InternalServerErrorException;

import pl.itcraft.soma.api.dto.FriendDto;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.service.FriendshipService;
import pl.itcraft.soma.core.utils.FriendshipActions;

@Api(
	    name = EndpointUtils.ENDPOINT_API_NAME,
	    version = "v1",
	    namespace =
	      @ApiNamespace(
	        ownerDomain = EndpointUtils.ENDPOINT_OWNER_DOMAIN,
	        ownerName = EndpointUtils.ENDPOINT_OWNER_NAME
	      )
	    )
public class FriendshipEndpoint {
	
	private final Logger logger = Logger.getLogger(FriendshipEndpoint.class.getName());
	private final FriendshipService friendshipService = new FriendshipService();
	private final EndpointUtils endpointUtils = new EndpointUtils();
	
	@ApiMethod(name = "friendshipAction", path = "friendship-action", httpMethod = HttpMethod.POST)
	public void friendshipAction(HttpServletRequest request
										, @Named("targetId") @NotNull Long targetId
										, @Named("action") @NotNull FriendshipActions action) throws IOException, ServiceException {
		try {
			User user = endpointUtils.authenticateUser(request);
			friendshipService.friendshipAction(user.getId(), targetId, action);
		} catch (BadRequestException e) {
			throw new BadRequestException(e.getMessage());
		} catch (Exception e) {
			logger.log(Level.SEVERE, ErrorStatus.INTERNAL_SERVER_ERROR, e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@ApiMethod(name="getMyFriendsList", path = "get-my-friends-list", httpMethod = HttpMethod.GET)
	public CollectionResponse<FriendDto> getMyFriendsList(HttpServletRequest request
									, @Named("limit") @Nullable Integer limit
									, @Named("nextPageToken") @Nullable String nextPageToken) throws ServiceException{
		try {
			User user = endpointUtils.authenticateUser(request);
			CollectionResponse<User> friendsList = friendshipService.getFriendsList(user.getId(), limit, nextPageToken);
			return createFriendsListResponse(friendsList);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, ErrorStatus.INTERNAL_SERVER_ERROR, e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
		
	}

	@ApiMethod(name="getAnothersFriendsList", path = "get-anothers-friends-list", httpMethod = HttpMethod.GET)
	public CollectionResponse<FriendDto> getAnothersFriendsList(HttpServletRequest request
			, @Named("userId") Long userId
			, @Named("limit") @Nullable Integer limit
			, @Named("nextPageToken") @Nullable String nextPageToken) throws ServiceException{
		try {
			endpointUtils.authenticateUser(request);
			CollectionResponse<User> friendsList = friendshipService.getFriendsList(userId, limit, nextPageToken);
			return createFriendsListResponse(friendsList);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, ErrorStatus.INTERNAL_SERVER_ERROR, e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
		
	}

	private CollectionResponse<FriendDto> createFriendsListResponse(CollectionResponse<User> friendsList) {
		if(friendsList == null || friendsList.getItems() == null){
			return null;
		}
		
		List<FriendDto> items = new ArrayList<>();
		for(User friend : friendsList.getItems()){
			items.add(new FriendDto(friend));
		}
		logger.info("Raw items "+items);
		CollectionResponse<FriendDto> response = CollectionResponse.<FriendDto> builder()
				.setItems(items)
				.setNextPageToken(friendsList.getNextPageToken())
				.build();
		logger.info("Response items "+response.getItems()+(response.getItems() == null ? " are null" : " are not null"));
		return response;
	}

}
