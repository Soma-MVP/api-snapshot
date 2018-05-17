package pl.itcraft.soma.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.api.server.spi.response.BadRequestException;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.cmd.Query;

import pl.itcraft.soma.core.Constants;
import pl.itcraft.soma.core.QueueUtils.PromotingAction;
import pl.itcraft.soma.core.QueueUtils.UserAction;
import pl.itcraft.soma.core.dto.FollowWithDateDto;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.Following;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.push.FcmPushType;
import pl.itcraft.soma.core.search.SearchDocumentService;

public class FollowingService {

	private final static Logger logger = Logger.getLogger(FollowingService.class.getName());
	private final SearchDocumentService searchDocumentService = new SearchDocumentService();
	private final NotificationService notificationService = new NotificationService();
	
	public void createFollowing(final Long followerId, final Long followedId) throws BadRequestException {
		if (followerId.equals(followedId)) {
			throw new BadRequestException(ErrorStatus.UNABLE_FOLLOW_YOURSELF);
		}
		if (findFollowing(followerId, followedId) != null) {
			throw new BadRequestException(ErrorStatus.ALREADY_FOLLOWING);
		}
		if (!userWithIdExists(followedId)) {
			throw new BadRequestException(ErrorStatus.USER_NOT_FOUND);
		}
		
		ObjectifyService.ofy().transact(new VoidWork() {

			@Override
			public void vrun() {
				incrementFollowingCounters(followerId, followedId);
				ObjectifyService.ofy().save().entity(new Following(followerId, followedId)).now();
			}
		});
		notificationService.createAndEnqueueNotification(null, followerId, followedId, FcmPushType.USER_FOLLOWING, null);
		searchDocumentService.enqueueUserAction(followerId, followedId, UserAction.FOLLOW);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.FOLLOW, followerId, followedId);
	}

	private void incrementFollowingCounters(Long followerId, Long followedId) {
		User follower = ObjectifyService.ofy().load().type(User.class).id(followerId).now();
		follower.incrementNumberOfFollowings();
		User followed = ObjectifyService.ofy().load().type(User.class).id(followedId).now();
		followed.incrementNumberOfFollowers();
		ObjectifyService.ofy().save().entities(follower, followed);
	}

	private void decrementFollowingCounters(Long followerId, Long followedId) {
		User follower = ObjectifyService.ofy().load().type(User.class).id(followerId).now();
		follower.decrementNumberOfFollowings();
		User followed = ObjectifyService.ofy().load().type(User.class).id(followedId).now();
		followed.decrementNumberOfFollowers();
		ObjectifyService.ofy().save().entities(follower, followed);
	}

	public void deleteFollowing(final Long followerId, final Long followedId) throws BadRequestException {
		final Following following = findFollowing(followerId, followedId);
		if (following == null) {
			throw new BadRequestException(ErrorStatus.NO_FOLLOWING);
		}
		ObjectifyService.ofy().transact(new VoidWork() {

			@Override
			public void vrun() {
				decrementFollowingCounters(followerId, followedId);
				ObjectifyService.ofy().delete().entity(following).now();
			}
		});
		searchDocumentService.enqueueUserAction(followerId, followedId, UserAction.UNFOLLOW);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.UNFOLLOW, followerId, followedId);

	}

	public CollectionResponse<FollowWithDateDto> getFollowingsList(Long userId, Integer limit, String nextPageToken) {
		return getFollowsList(userId, limit, nextPageToken, true);
	}

	public CollectionResponse<FollowWithDateDto> getFollowersList(Long userId, Integer limit, String nextPageToken) {
		return getFollowsList(userId, limit, nextPageToken, false);
	}

	private CollectionResponse<FollowWithDateDto> getFollowsList(Long userId, Integer limit, String nextPageToken, boolean findFollowings) {
		Query<Following> followsQuery = ObjectifyService.ofy().load().type(Following.class)
				.filter(findFollowings ? "followerId" : "followedId", userId).limit(limit != null && !limit.equals(0) ? limit : Constants.FRIENDS_LIMIT)
				.order("creationDate");

		if (nextPageToken != null) {
			followsQuery = followsQuery.startAt(Cursor.fromWebSafeString(nextPageToken));
		}

		QueryResultIterator<Following> followsQueryIterator = followsQuery.iterator();

		logger.info((findFollowings ? "Followed " : "Followers ") + followsQuery.toString());

		List<Key<User>> ids = new ArrayList<Key<User>>();
		while (followsQueryIterator.hasNext()) {
			Following following = followsQueryIterator.next();
			long followingUserId = findFollowings ? following.getFollowedId() : following.getFollowerId();
			ids.add(Key.create(User.class, followingUserId));
		}

		// Filtering on empty ids throws error
		if (ids.isEmpty()) {
			logger.info("Is empty");
			return CollectionResponse.<FollowWithDateDto>builder().setItems(new ArrayList<FollowWithDateDto>(0)).build();
		}
		Query<User> query = ObjectifyService.ofy().load().type(User.class).filterKey("in", ids);

		QueryResultIterator<User> queryResultIterator = query.iterator();

		List<FollowWithDateDto> items = new ArrayList<>();

		followsQueryIterator = followsQuery.iterator();
		while (queryResultIterator.hasNext()) {
			items.add(new FollowWithDateDto(queryResultIterator.next(), followsQueryIterator.next().getCreationDate()));
		}
		CollectionResponse.Builder<FollowWithDateDto> builder = CollectionResponse.<FollowWithDateDto>builder().setItems(items);

		Cursor nextCursor = followsQueryIterator.getCursor();

		logger.info("Cursor " + queryResultIterator.toString() + " " + nextCursor);

		if (nextCursor != null) {
			builder.setNextPageToken(nextCursor.toWebSafeString());
		}
		return builder.build();
	}

	private Following findFollowing(Long followerId, Long followedId) {
		List<Following> followings = ObjectifyService.ofy().load().type(Following.class)
				.filter("followerId", followerId).filter("followedId", followedId).list();
		if (followings.isEmpty()) {
			return null;
		}
		return followings.get(0);
	}

	public Boolean isFollower(Long followerId, Long followedId) {
		return findFollowing(followerId, followedId) != null;
	}

	private boolean userWithIdExists(Long userId) {
		return ObjectifyService.ofy().load().type(User.class).filterKey(Key.create(User.class, userId)).count() == 1;
	}

	public void createFollowingForFriendship(final Long oneSideUserId, final Long reversedSideUserId) {
		final Following oneSideFollowing = findFollowing(oneSideUserId, reversedSideUserId);
		final Following reversedSideFollowing = findFollowing(reversedSideUserId, oneSideUserId);

		ObjectifyService.ofy().transact(new VoidWork() {

			@Override
			public void vrun() {
				User oneSideUser = ObjectifyService.ofy().consistency(Consistency.STRONG).load().type(User.class).id(oneSideUserId).now();
				User reversedSideUser = ObjectifyService.ofy().consistency(Consistency.STRONG).load().type(User.class).id(reversedSideUserId).now();


				Following oneSideFollowingToSave;
				if (oneSideFollowing != null) {
					oneSideFollowingToSave = oneSideFollowing;
				} else {
					oneSideFollowingToSave = new Following(oneSideUserId, reversedSideUserId);
					oneSideUser.incrementNumberOfFollowings();
					reversedSideUser.incrementNumberOfFollowers();
				}

				oneSideFollowingToSave.setIsFriend(true);

				Following reversedSideFollowingToSave;
				if (reversedSideFollowing != null) {

					reversedSideFollowingToSave = reversedSideFollowing;
				} else {
					reversedSideFollowingToSave = new Following(reversedSideUserId, oneSideUserId);
					reversedSideUser.incrementNumberOfFollowings();
					oneSideUser.incrementNumberOfFollowers();
				}

				reversedSideFollowingToSave.setIsFriend(true);

				ObjectifyService.ofy().save().entities(oneSideFollowingToSave, reversedSideFollowingToSave, oneSideUser, reversedSideUser).now();
				
			}
		});
		notificationService.createAndEnqueueNotification(null, oneSideUserId, reversedSideUserId, FcmPushType.USER_FOLLOWING, null);
		notificationService.createAndEnqueueNotification(null, reversedSideUserId, oneSideUserId, FcmPushType.USER_FOLLOWING, null);

	}

	public void removeIsFriendFlagFromFriendship(Long oneSideUserId, Long reversedSideUserId) {
		Following oneSideFollowing = findFollowing(oneSideUserId, reversedSideUserId);
		Following reversedSideFollowing = findFollowing(reversedSideUserId, oneSideUserId);

		oneSideFollowing.setIsFriend(false);
		reversedSideFollowing.setIsFriend(false);

		ObjectifyService.ofy().save().entities(oneSideFollowing, reversedSideFollowing).now();

	}

}
