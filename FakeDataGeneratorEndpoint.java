package pl.itcraft.soma.api.endpoints;

import java.io.IOException;
import java.util.logging.Logger;

import javax.inject.Named;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.BadRequestException;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

import pl.itcraft.soma.core.QueueUtils;
import pl.itcraft.soma.core.error.ApiException;

@Api(
	    name = EndpointUtils.ADMIN_API_NAME,
	    version = "v1",
	    namespace =
	      @ApiNamespace(
	        ownerDomain = EndpointUtils.ENDPOINT_OWNER_DOMAIN,
	        ownerName = EndpointUtils.ENDPOINT_OWNER_NAME
	      )
	    )
public class FakeDataGeneratorEndpoint {
	
	private final String PWD_CODE = "7%jkZB4sRTa";
	private Logger logger = Logger.getLogger(FakeDataGeneratorEndpoint.class.getName());
	
	@ApiMethod(name="generateFakeData", path = "generate-fake-data", httpMethod = HttpMethod.POST)
	public void generateFakeData(@Named("pwdCode") String pwdCode,
			@Named("numberOfUsers") Integer numberOfUsers, @Named("numberOfItems") Integer numberOfItems, @Named("numberOfMinFollowersForFollowedALot") Integer numberOfMinFollowersForFollowedALot,
			@Named("numberOfMinFollowersForFollowingALot") Integer numberOfMinFollowersForFollowingALot, @Named("numberOfFollowersTotal") Integer numberOfFollowersTotal,
			@Named("numberOfMinFriendsForALotOfFriends") Integer numberOfMinFriendsForALotOfFriends, @Named("numberOfFriends") Integer numberOfFriends,
			@Named("maxRadiusForRandomLocation") Double maxRadiusForRandomLocationInMeters, @Named("downloadImages") Boolean downloadImages) throws ApiException, BadRequestException, IOException {
	
		if (pwdCode.equals(PWD_CODE)) {
			Queue queue = QueueFactory.getQueue(QueueUtils.FAKE_DATA_GENERATOR_QUEUE_NAME);
			queue.add(TaskOptions.Builder.withUrl(QueueUtils.FAKE_DATA_GENERATOR_QUEUE_URL)
					.param(QueueUtils.PARAM_NUM_OF_USERS, numberOfUsers.toString())
					.param(QueueUtils.PARAM_NUM_OF_ITEMS, numberOfItems.toString())
					.param(QueueUtils.PARAM_NUM_OF_FRIENDS, numberOfFriends.toString())
					.param(QueueUtils.PARAM_NUM_OF_MIN_FOLLOWERS_FOR_FOLLOWED_A_LOT, numberOfMinFollowersForFollowedALot.toString())
					.param(QueueUtils.PARAM_NUM_OF_MIN_FOLLOWERS_FOR_FOLLOWING_A_LOT, numberOfMinFollowersForFollowingALot.toString())
					.param(QueueUtils.PARAM_NUM_OF_FOLLOWERS_TOTAL, numberOfFollowersTotal.toString())
					.param(QueueUtils.PARAM_NUM_OF_MIN_FRIENDS_FOR_A_LOT_OF_FRIENDS, numberOfMinFriendsForALotOfFriends.toString())
					.param(QueueUtils.PARAM_MAX_RADIUS_FOR_RANDOM_LOCATION_IN_METERS, maxRadiusForRandomLocationInMeters.toString())
					.param(QueueUtils.PARAM_DOWNLOAD_IMAGES, downloadImages.toString()));
		} else {
			logger.warning("Unauthorized admin API request");
		}
		
	}

}
