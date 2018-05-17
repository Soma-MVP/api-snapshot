package pl.itcraft.soma.core.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.api.server.spi.response.BadRequestException;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.response.ConflictException;
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
import pl.itcraft.soma.core.dto.FriendUserDto;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.Friendship;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.model.enums.FriendshipStatus;
import pl.itcraft.soma.core.push.FcmPushType;
import pl.itcraft.soma.core.search.SearchDocumentService;
import pl.itcraft.soma.core.utils.FriendshipActions;

public class FriendshipService {

	private final static Logger logger = Logger.getLogger(FriendshipService.class.getName());
	private final SearchDocumentService searchDocumentService = new SearchDocumentService();
	private final FollowingService followingService = new FollowingService();
	private final NotificationService notificationService = new NotificationService();
	
	public void friendshipAction(Long senderId, Long targetId, FriendshipActions action) throws BadRequestException {
		List<Friendship> existingInvitationResult = ObjectifyService.ofy().consistency(Consistency.STRONG).load().type(Friendship.class)
				.filter("senderId", senderId)
				.filter("targetId", targetId)
				.list();
		List<Friendship> reversedInvitationResult = ObjectifyService.ofy().consistency(Consistency.STRONG).load().type(Friendship.class)
				.filter("senderId", targetId)
				.filter("targetId", senderId)
				.list();
		Friendship existingInvitation = checkInvitationsResult(existingInvitationResult);
		Friendship reversedInvitation = checkInvitationsResult(reversedInvitationResult);

		switch(action) {
		case ADD:
			if(senderId.equals(targetId)) {
				throw new BadRequestException(ErrorStatus.UNABLE_FRIEND_YOURSELF);
			} else if(existingInvitation != null && reversedInvitation != null) {
				throw new BadRequestException(ErrorStatus.ALREADY_FRIENDS);
			} else if (existingInvitation != null) {
				throw new BadRequestException(ErrorStatus.INVITATION_EXISTS);
			} else if (reversedInvitation != null) {
				confirmInvitation(senderId, targetId,reversedInvitation);
			} else if(!userWithIdExists(targetId)) {
				throw new BadRequestException(ErrorStatus.USER_NOT_FOUND);
			} else {
				sendInvitation(senderId, targetId);
			}
			break;
		case REJECT:
			if (existingInvitation != null && reversedInvitation != null) {
				deleteFriendship(existingInvitation, reversedInvitation, senderId, targetId);
			} else if (existingInvitation != null) {
				deleteInvitation(existingInvitation);
			} else if (reversedInvitation != null) {
				deleteInvitation(reversedInvitation);
			} else {
				throw new BadRequestException(ErrorStatus.INVITATION_NOT_FOUND);
			}
		}
	}

	public CollectionResponse<FriendUserDto> getFriendsList(Long userId, Integer limit, String nextPageToken) {

		Query<Friendship> friendsQuery = ObjectifyService.ofy().consistency(Consistency.STRONG).load()
				.type(Friendship.class)
				.filter("targetId", userId)
				.filter("showOnList", true)
				.limit(limit != null && !limit.equals(0) ? limit : Constants.FRIENDS_LIMIT)
				.order("-status")
				.order("creationDate");

		return getFriendsListFromQuery(nextPageToken, friendsQuery);
	}
	
	public CollectionResponse<FriendUserDto> getAnothersFriendsList(Long userId, Integer limit, String nextPageToken) {
		
		Query<Friendship> friendsQuery = ObjectifyService.ofy().consistency(Consistency.STRONG).load()
				.type(Friendship.class)
				.filter("targetId", userId)
				.filter("status", FriendshipStatus.FRIENDS)
				.limit(limit != null && !limit.equals(0) ? limit : Constants.FRIENDS_LIMIT)
				.order("creationDate");
		
		return getFriendsListFromQuery(nextPageToken, friendsQuery);
	}

	private CollectionResponse<FriendUserDto> getFriendsListFromQuery(String nextPageToken, Query<Friendship> friendsQuery) {
		if(nextPageToken != null){
			friendsQuery = friendsQuery.startAt(Cursor.fromWebSafeString(nextPageToken));
		}

		QueryResultIterator<Friendship> friendsQueryIterator = friendsQuery.iterator();

		logger.info("Friends  "+friendsQuery.toString());

		List<Key<User>> ids = new ArrayList<Key<User>>();
		while(friendsQueryIterator.hasNext()){
			Friendship friendship = friendsQueryIterator.next();
			ids.add(Key.create(User.class, friendship.getSenderId()));
		}

		//Filtering on empty ids throws error
		if (ids.isEmpty()) {
			logger.info("Is empty");
			return CollectionResponse.<FriendUserDto> builder().setItems(new ArrayList<FriendUserDto>(0)).build();
		}
		Query<User> query = ObjectifyService.ofy().consistency(Consistency.STRONG).load()
				.type(User.class)
				.filterKey("in", ids);

		QueryResultIterator<User> queryResultIterator = query.iterator();

		List<FriendUserDto> items = new ArrayList<>();

		friendsQueryIterator = friendsQuery.iterator();
		while (queryResultIterator.hasNext()) {
			Friendship friendship = friendsQueryIterator.next();
			items.add(new FriendUserDto(queryResultIterator.next(), friendship.getStatus(), friendship.getCreationDate()));
	    }
	    CollectionResponse.Builder<FriendUserDto> builder = CollectionResponse.<FriendUserDto> builder().setItems(items);

	    Cursor nextCursor = friendsQueryIterator.getCursor();

		logger.info("Cursor "+queryResultIterator.toString()+" "+nextCursor);

	    if (nextCursor != null) {
			builder.setNextPageToken(nextCursor.toWebSafeString());
		}
		return builder.build();
	}
	
	private void sendInvitation(Long senderId, Long targetId) {
		Friendship newFriendship = new Friendship();
		newFriendship.setSenderId(senderId);
		newFriendship.setTargetId(targetId);
		newFriendship.setStatus(FriendshipStatus.SENT);
		newFriendship.setCreationDate(new Date());
		ObjectifyService.ofy().save().entity(newFriendship).now();
	
		notificationService.createAndEnqueueNotification(null, senderId, targetId,  FcmPushType.CONNECTION_REQUEST, null);
	}

	private void confirmInvitation(final Long senderId, final Long targetId, final Friendship existing) {
		final Friendship newFriendship = new Friendship();
		newFriendship.setSenderId(senderId);
		newFriendship.setTargetId(targetId);
		newFriendship.setStatus(FriendshipStatus.FRIENDS);
		newFriendship.setCreationDate(new Date());
		existing.setStatus(FriendshipStatus.FRIENDS);

		ObjectifyService.ofy().consistency(Consistency.STRONG).transact(new VoidWork() {
			@Override
			public void vrun() {
				logger.info("Confirming invitation, senderId: " + senderId + ", targetId: " + targetId);
				ObjectifyService.ofy().consistency(Consistency.STRONG).save().entities(newFriendship, existing).now();
			}
		});
		followingService.createFollowingForFriendship(targetId, senderId);
		searchDocumentService.enqueueUserAction(targetId, senderId, UserAction.FRIEND);
		searchDocumentService.enqueueUserAction(senderId, targetId, UserAction.FRIEND);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.FRIEND, senderId, targetId);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.FRIEND, targetId, senderId);
	}
	
	/**
	 * Only to be used by data generator
	 * @throws ConflictException 
	 */
	public void createFakeFriendship(final Long user1Id, final Long user2Id) throws ConflictException {
		
		Friendship f = ObjectifyService.ofy().load().type(Friendship.class).filter("senderId =", user1Id).filter("targetId =", user2Id).first().now();
		if (f != null) {
			throw new ConflictException("already exists");
		}
		
		final Friendship newFriendship = new Friendship();
		newFriendship.setSenderId(user1Id);
		newFriendship.setTargetId(user2Id);
		newFriendship.setStatus(FriendshipStatus.FRIENDS);
		newFriendship.setCreationDate(new Date());

		final Friendship newFriendship2 = new Friendship();
		newFriendship2.setSenderId(user2Id);
		newFriendship2.setTargetId(user1Id);
		newFriendship2.setStatus(FriendshipStatus.FRIENDS);
		newFriendship2.setCreationDate(new Date());
		
		ObjectifyService.ofy().save().entities(newFriendship, newFriendship2).now();
		
		followingService.createFollowingForFriendship(user1Id, user2Id);	
		searchDocumentService.enqueueUserAction(user1Id, user2Id, UserAction.FRIEND);
		searchDocumentService.enqueueUserAction(user2Id, user2Id, UserAction.FRIEND);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.FRIEND, user1Id, user2Id);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.FRIEND, user2Id, user1Id);
	}

	private void deleteInvitation(Friendship toDelete) {
		ObjectifyService.ofy().consistency(Consistency.STRONG).delete().entity(toDelete).now();
	}

	private void deleteFriendship(final Friendship oneSide, final Friendship reversedSide, final Long oneSideUserId, final Long reversedSideUserId) {
		ObjectifyService.ofy().consistency(Consistency.STRONG).transact(new VoidWork() {
			@Override
			public void vrun() {
				logger.info("Deleting friendship, users: " + oneSideUserId + ", " + reversedSideUserId);
				ObjectifyService.ofy().consistency(Consistency.STRONG).delete().entities(oneSide, reversedSide).now();
			}
		});
		followingService.removeIsFriendFlagFromFriendship(oneSideUserId, reversedSideUserId);
		searchDocumentService.enqueueUserAction(oneSideUserId, reversedSideUserId, UserAction.UNFRIEND);
		searchDocumentService.enqueueUserAction(reversedSideUserId, oneSideUserId, UserAction.UNFRIEND);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.UNFRIEND, oneSideUserId, reversedSideUserId);
		searchDocumentService.enqueuePromotingAction(null, PromotingAction.UNFRIEND, reversedSideUserId, oneSideUserId);
	}

	private Friendship checkInvitationsResult(List<Friendship> result) throws BadRequestException {
		logger.log(Level.INFO, "Checking "+result.toString());
		if (result.isEmpty() || result == null) {
			logger.log(Level.INFO,"Empty or null");
			return null;
		}else if (result.size() > 1) {
			logger.log(Level.WARNING, result.size()+" result size error");
			throw new BadRequestException("Bad server state");
		}else{
			logger.log(Level.INFO, "Returning");
			return result.get(0);
		}
	}

	private boolean userWithIdExists(Long userId) {
		return ObjectifyService.ofy().consistency(Consistency.STRONG).load().type(User.class).filterKey(Key.create(User.class, userId)).count() == 1;
	}

}
