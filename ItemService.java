package pl.itcraft.soma.core.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;

import pl.itcraft.soma.core.Constants;
import pl.itcraft.soma.core.QueueUtils;
import pl.itcraft.soma.core.QueueUtils.ItemAction;
import pl.itcraft.soma.core.error.ApiException;
import pl.itcraft.soma.core.error.EC;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.Item;
import pl.itcraft.soma.core.model.entities.ItemContainer;
import pl.itcraft.soma.core.model.entities.ItemLike;
import pl.itcraft.soma.core.model.entities.ItemPhoto;
import pl.itcraft.soma.core.model.entities.PhotoFile;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.model.enums.Category;
import pl.itcraft.soma.core.objectify.OfyUtils;
import pl.itcraft.soma.core.search.SearchDocumentService;
import pl.itcraft.soma.core.search.SearchEntityDocument;
import pl.itcraft.soma.core.utils.ItemActions;
import pl.itcraft.soma.core.validators.ItemValidator;
import pl.itcraft.soma.core.validators.ValidationErrors;

public class ItemService {

	private final BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
	private final Logger logger = Logger.getLogger(ItemService.class.getName());
	private final SearchDocumentService searchDocumentService = new SearchDocumentService();

	public ItemPhoto saveItemPhoto(User user, PhotoFile photoFile) {
		ItemPhoto itemPhoto = new ItemPhoto();
		itemPhoto.setUserId(user.getId());
		itemPhoto.setUrl(photoFile.getUrl());
		itemPhoto.setFileKey(photoFile.getFileKey());
		itemPhoto.setCreationDate(new Date());
		ObjectifyService.ofy().save().entity(itemPhoto).now();
		return itemPhoto;
	}

	public ItemContainer saveItem(Long id, final User user, String title, String description, Category category,
			final List<Long> photoIds, Boolean allowReselling, Long price, String priceUnit, Double latitude,
			Double longitude, String locationName, Boolean isDraft) throws ApiException {

		final Item item = id != null ? ObjectifyService.ofy().load().type(Item.class).id(id).now() : new Item();

		if (item == null || (item.getOwnerId() != null && !item.getOwnerId().equals(user.getId()))) {
			throw new ApiException(EC.NOT_FOUND, ErrorStatus.ITEM_NOT_FOUND);
		}

		// Check if there are too much photos
		if (photoIds != null && photoIds.size() > Constants.ITEM_PHOTOS_LIMIT) {
			throw new ApiException(EC.BAD_REQUEST, ErrorStatus.TOO_MUCH_PHOTOS);
		}
		if (photoIds != null) {
			// Check if user provided valid photoIds
			for (Long photoId : photoIds) {
				ItemPhoto photo = ObjectifyService.ofy().load().type(ItemPhoto.class).id(photoId).now();
				// In case when photo does not exists or its not Users photo throw ApiException
				if (photo == null || !photo.getUserId().equals(user.getId())) {
					throw new ApiException(EC.NOT_FOUND, ErrorStatus.PHOTO_NOT_FOUND);
				}
				// In case when photo is assigned to some other item throw ApiException
				if ((id == null && photo.getItemId() != null)
						|| id != null && photo.getItemId() != null && !photo.getItemId().equals(id)) {
					throw new ApiException(EC.BAD_REQUEST, ErrorStatus.PHOTO_ALREADY_ASSIGNED);
				}
			}
		}

		//Firstly setDraft to check if we even should bother about updating SearchAPI indexes
		item.setIsDraft(isDraft != null && isDraft);
		boolean shouldUpdate = !item.getIsDraft()
								&& (!Objects.equals(item.getTitle(), title)
									|| !Objects.equals(item.getCategory(), category)
									|| !Objects.equals(item.getLatitude(), latitude)
									|| !Objects.equals(item.getLongitude(), longitude));


		item.setOwnerId(user.getId());
		item.setTitle(title);
		item.setDescription(description);
		item.setCategory(category);
		item.setAllowReselling(allowReselling);
		item.setPrice(price);
		item.setPriceUnit(priceUnit);
		item.setLatitude(latitude);
		item.setLongitude(longitude);
		item.setLocationName(locationName);

		if(item.getCreationDate() == null) {
			item.setCreationDate(new Date());
		}

		ValidationErrors errors = new ValidationErrors();
		new ItemValidator().validateItem(errors, item);
		ApiException.throwIfHasErrors(errors);

		final List<String> keysToDelete = new ArrayList<>();
		final List<ItemPhoto> oldItemPhotos = id != null
				? ObjectifyService.ofy().load().type(ItemPhoto.class).filter("itemId", item.getId()).list()
				: null;
		final List<ItemPhoto> currentItemPhotos = new ArrayList<>();


		Item result = ObjectifyService.ofy().transact(new Work<Item>() {

			@Override
			public Item run() {
				if(id == null) {
					user.incrementNumberOfItemsWithDraft();
					if(!item.getIsDraft()) {
						user.incrementNumberOfItems();
					}
				}
				// Save item and get id
				ObjectifyService.ofy().save().entities(item, user).now();


				// Delete entity of old photos if its not used anymore
				if (oldItemPhotos != null && !oldItemPhotos.isEmpty()) {
					for (ItemPhoto itemPhoto : oldItemPhotos) {
						if (photoIds == null || !photoIds.contains(itemPhoto.getId())) {
							keysToDelete.add(itemPhoto.getFileKey());
							ObjectifyService.ofy().delete().entity(itemPhoto).now();
						}
					}
				}
				if (photoIds != null) {
					// Connect ItemPhoto with Item
					for (Long photoId : photoIds) {
						ItemPhoto photo = ObjectifyService.ofy().load().type(ItemPhoto.class).id(photoId).now();
						photo.setItemId(item.getId());
						ObjectifyService.ofy().save().entity(photo).now();
						currentItemPhotos.add(photo);
					}
				}
				return item;
			}
		});

		// Delete photos from Blobstore
		for (String key : keysToDelete) {
			blobstoreService.delete(new BlobKey(key));
		}

		if(shouldUpdate) {
			searchDocumentService.updateSearchEntityDocumentGlobal(item);
			searchDocumentService.enqueueItemAction(result, ItemAction.CREATE);
		}

		return new ItemContainer(result, currentItemPhotos);
	}

	public Item getItem(Long id) throws ApiException {
		Item item = ObjectifyService.ofy().load().type(Item.class).id(id).now();
		if(item == null) {
			throw new ApiException(EC.NOT_FOUND, ErrorStatus.ITEM_NOT_FOUND);
		}
		return item;

	}

	public List<ItemPhoto> getItemPhotos(Long id) {
		return ObjectifyService.ofy().load().type(ItemPhoto.class).filter("itemId", id).order("-creationDate").limit(Constants.ITEM_PHOTOS_LIMIT).list();
	}
	public ItemPhoto getItemPhoto(Long id) {
		return ObjectifyService.ofy().load().type(ItemPhoto.class).filter("itemId", id).order("-creationDate").first().now();
	}

	public CollectionResponse<Item> getUserItems(User user, Integer limit, String nextPageToken, boolean withoutDrafts) {
		if(limit == null || limit > Constants.USER_ITEMS_MAX_LIMIT) {
			limit = Constants.USER_ITEMS_MAX_LIMIT;
		}

		Map<String, Object> queryParams = new HashMap<>();
		if(withoutDrafts) {
			queryParams.put("isDraft", false);
		}
		queryParams.put("ownerId", user.getId());
		return OfyUtils.listPagedEntities(queryParams, "-creationDate", limit, nextPageToken, Item.class);
	}

	public void proceedDeleteUnusedPhotos() {

		Date date = DateTime.now().minusHours(Constants.UNUSED_PHOTO_EXPIRATION_TIME).toDate();

		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("creationDate < ", date);
		queryParams.put("itemId", null);

		int counter = 0;
		CollectionResponse<ItemPhoto> itemPhotos = OfyUtils.listPagedEntities(queryParams, null, Constants.DELETE_UNUSED_PHOTOS_BATCH_SIZE, null, ItemPhoto.class);
		while(itemPhotos != null && itemPhotos.getItems() != null && !itemPhotos.getItems().isEmpty()){
			for(ItemPhoto itemPhoto : itemPhotos.getItems()) {
				blobstoreService.delete(new BlobKey(itemPhoto.getFileKey()));
				ObjectifyService.ofy().delete().entity(itemPhoto).now();
				counter++;
			}
			itemPhotos = OfyUtils.listPagedEntities(queryParams, null, Constants.DELETE_UNUSED_PHOTOS_BATCH_SIZE, itemPhotos.getNextPageToken(), ItemPhoto.class);
		}
		logger.info("Successfully deleted unused item photos : " + counter);

	}

	public CollectionResponse<Item> searchItems(String keyword, Category category, Double latitude, Double longitude, Integer minRange, Integer maxRange, User user, Boolean followersItems, Boolean friendsItems, String nextPageToken, Integer limit){

		CollectionResponse<SearchEntityDocument> searchEntityDocuments = searchDocumentService.searchEntities(keyword, category, latitude, longitude, minRange, maxRange, user, followersItems, friendsItems, nextPageToken, limit);

		if(searchEntityDocuments == null || searchEntityDocuments.getItems() == null) {
			return CollectionResponse.<Item> builder().setItems(new ArrayList<Item>(0)).build();
		}

		List<Item> items = new ArrayList<>();
		for(SearchEntityDocument searchEntityDocument : searchEntityDocuments.getItems()) {
			items.add(ObjectifyService.ofy().load().type(Item.class).id(Long.valueOf(searchEntityDocument.getId())).now());
		}
		return CollectionResponse.<Item> builder().setItems(items).setNextPageToken(searchEntityDocuments.getNextPageToken()).build();
	}

	public void publishDraftItem(Long id, User user) throws ApiException {
		Item item = ObjectifyService.ofy().load().type(Item.class).id(id).now();

		if(item == null || !Objects.equals(item.getOwnerId(), user.getId())) {
			throw new ApiException(EC.NOT_FOUND, ErrorStatus.ITEM_NOT_FOUND);
		}

		item.setIsDraft(false);
		user.incrementNumberOfItems();
		ObjectifyService.ofy().transact(() -> {
			ObjectifyService.ofy().save().entities(item, user).now();
		});
		searchDocumentService.updateSearchEntityDocumentGlobal(item);
		searchDocumentService.enqueueItemAction(item, ItemAction.CREATE);


	}

	public void enqueueItemView(Long itemId) {
		Queue queue = QueueFactory.getQueue(QueueUtils.ITEM_SHARD_QUEUE_NAME);
		queue.add(TaskOptions.Builder.withUrl(QueueUtils.ITEM_SHARD_QUEUE_URL)
				.param(QueueUtils.ITEM_ID_PARAMETER_NAME, itemId.toString())
				);
	}

	public void itemAction(User user, Long id, ItemActions itemAction) throws ApiException {
		final Item item = getItem(id);
		final ItemLike itemLike;
		switch(itemAction) {
		case LIKE:
			itemLike = new ItemLike();
			itemLike.setItemId(item.getId());
			itemLike.setUserId(user.getId());
			itemLike.setCreationDate(new Date());
			item.incrementNumberOfLikes();
			ObjectifyService.ofy().transact(new VoidWork() {

				@Override
				public void vrun() {
					ObjectifyService.ofy().save().entities(itemLike, item).now();
				}
			});
			break;
		case UNLIKE:
			itemLike = findItemLike(user.getId(), id);
			if(itemLike == null) {
				throw new ApiException(EC.NOT_FOUND, ErrorStatus.ITEM_LIKE_NOT_FOUND);
			}
			item.decrementNumberOfLikes();
			ObjectifyService.ofy().transact(new VoidWork() {

				@Override
				public void vrun() {
					ObjectifyService.ofy().delete().entity(itemLike).now();
					ObjectifyService.ofy().save().entities(item).now();
				}
			});

			break;
		default:
			break;

		}
	}


	private ItemLike findItemLike(Long userId, Long itemId) {
		List<ItemLike> ItemLikes = ObjectifyService.ofy().load().type(ItemLike.class)
				.filter("userId", userId).filter("itemId", itemId).list();
		if (ItemLikes.isEmpty()) {
			return null;
		}
		return ItemLikes.get(0);
	}

	public Boolean isLiked(Long userId, Long itemId) {
		return findItemLike(userId, itemId) != null;
	}

	public CollectionResponse<Item> getLikedItems(User user, String nextPageToken, Integer limit) {

		if(limit == null || limit > Constants.LIKED_ITEMS_MAX_LIMIT) {
			limit = Constants.LIKED_ITEMS_MAX_LIMIT;
		}

		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("userId", user.getId());
		CollectionResponse<ItemLike> itemLikes = OfyUtils.listPagedEntities(queryParams, "-creationDate", limit, nextPageToken, ItemLike.class);

		if(itemLikes == null || itemLikes.getItems() == null) {
			return CollectionResponse.<Item> builder().setItems(new ArrayList<Item>(0)).build();
		}

		List<Item> items = new ArrayList<>();
		for(ItemLike itemLike : itemLikes.getItems()) {
			items.add(ObjectifyService.ofy().load().type(Item.class).id(itemLike.getItemId()).now());
		}
		return CollectionResponse.<Item> builder().setItems(items).setNextPageToken(itemLikes.getNextPageToken()).build();
	}

	public CollectionResponse<String> itemsAutocomplete(String keyword, String nextPageToken, Integer limit){
		CollectionResponse<SearchEntityDocument> searchEntityDocuments = searchDocumentService.searchEntitiesForAutocomplete(keyword, nextPageToken, limit);

		if(searchEntityDocuments == null || searchEntityDocuments.getItems() == null) {
			return CollectionResponse.<String> builder().setItems(new ArrayList<String>(0)).build();
		}

		List<String> items = new ArrayList<>();
		for(SearchEntityDocument searchEntityDocument : searchEntityDocuments.getItems()) {
			items.add(ObjectifyService.ofy().load().type(Item.class).id(Long.valueOf(searchEntityDocument.getId())).now().getTitle());
		}
		return CollectionResponse.<String> builder().setItems(items).setNextPageToken(searchEntityDocuments.getNextPageToken()).build();
	}

}
