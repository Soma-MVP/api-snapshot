package pl.itcraft.soma.api.endpoints;

import java.util.ArrayList;
import java.util.Collection;
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

import pl.itcraft.soma.api.dto.FollowingDto;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.service.FollowingService;


@Api(
	    name = EndpointUtils.ENDPOINT_API_NAME,
	    version = "v1",
	    namespace =
	      @ApiNamespace(
	        ownerDomain = EndpointUtils.ENDPOINT_OWNER_DOMAIN,
	        ownerName = EndpointUtils.ENDPOINT_OWNER_NAME
	      )
	    )
public class FollowingEndpoint {

	private final Logger logger = Logger.getLogger(FollowingEndpoint.class.getName());
	private final FollowingService followingService = new FollowingService();
	private final EndpointUtils endpointUtils = new EndpointUtils();
	
	@ApiMethod(name = "createFollowing", path = "create-following", httpMethod = HttpMethod.POST)
	public void createFollowing(HttpServletRequest request
			, @Named("followedId") @NotNull Long followedId) throws BadRequestException, InternalServerErrorException {
		
		try {
			User user = endpointUtils.authenticateUser(request);
			followingService.createFollowing(user.getId(), followedId);
		} catch (BadRequestException e){
			throw new BadRequestException(e.getMessage());
		} catch (Exception e) {
			logger.log(Level.SEVERE, ErrorStatus.INTERNAL_SERVER_ERROR, e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@ApiMethod(name = "deleteFollowing", path = "delete-following", httpMethod = HttpMethod.POST)
	public void deleteFollowing(HttpServletRequest request
			, @Named("followedId") @NotNull Long followedId) throws BadRequestException, InternalServerErrorException {
		
		try {
			User user = endpointUtils.authenticateUser(request);
			followingService.deleteFollowing(user.getId(), followedId);
		} catch (BadRequestException e){
			throw new BadRequestException(e.getMessage());
		} catch (Exception e) {
			logger.log(Level.SEVERE, ErrorStatus.INTERNAL_SERVER_ERROR, e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@ApiMethod(name="getFollowingsList", path = "get-followings-list", httpMethod = HttpMethod.GET)
	public CollectionResponse<FollowingDto> getFollowingsList(HttpServletRequest request
									, @Named("limit") @Nullable Integer limit
									, @Named("nextPageToken") @Nullable String nextPageToken) throws ServiceException{
		try {
			User user = endpointUtils.authenticateUser(request);
			CollectionResponse<User> followingsList = followingService.getFollowingsList(user.getId(), limit, nextPageToken);
			return createFollowingsListResponse(followingsList);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, ErrorStatus.INTERNAL_SERVER_ERROR, e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
		
	}
	
	@ApiMethod(name="getFollowersList", path = "get-followers-list", httpMethod = HttpMethod.GET)
	public CollectionResponse<FollowingDto> getFollowersList(HttpServletRequest request
			, @Named("limit") @Nullable Integer limit
			, @Named("nextPageToken") @Nullable String nextPageToken) throws ServiceException{
		try {
			User user = endpointUtils.authenticateUser(request);
			CollectionResponse<User> followersList = followingService.getFollowersList(user.getId(), limit, nextPageToken);
			return createFollowingsListResponse(followersList);
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			logger.log(Level.SEVERE, ErrorStatus.INTERNAL_SERVER_ERROR, e);
			throw new InternalServerErrorException(ErrorStatus.INTERNAL_SERVER_ERROR);
		}
		
	}
	
	private CollectionResponse<FollowingDto> createFollowingsListResponse(CollectionResponse<User> followingsList) {
		if(followingsList == null || followingsList.getItems() == null){
			return null;
		}
		
		Collection<FollowingDto> items = new ArrayList<>();
		for(User friend : followingsList.getItems()){
			items.add(new FollowingDto(friend));
		}
		logger.info("Raw items "+items);
		CollectionResponse<FollowingDto> response = CollectionResponse.<FollowingDto> builder()
				.setItems(items)
				.setNextPageToken(followingsList.getNextPageToken())
				.build();
		logger.info("Response items "+response.getItems()+(response.getItems() == null ? " are null" : " are not null"));
		return response;
	}
	
}
