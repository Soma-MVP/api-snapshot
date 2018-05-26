package pl.itcraft.soma.core.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.datastore.ReadPolicy.Consistency;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;

import pl.itcraft.soma.core.Constants;
import pl.itcraft.soma.core.QueueUtils;
import pl.itcraft.soma.core.QueueUtils.ItemAction;
import pl.itcraft.soma.core.QueueUtils.PromotingAction;
import pl.itcraft.soma.core.dto.BlockchainIICDto;
import pl.itcraft.soma.core.dto.BlockchainTransactionStatusDto;
import pl.itcraft.soma.core.error.ApiException;
import pl.itcraft.soma.core.error.EC;
import pl.itcraft.soma.core.error.ErrorStatus;
import pl.itcraft.soma.core.model.entities.Item;
import pl.itcraft.soma.core.model.entities.ItemContainer;
import pl.itcraft.soma.core.model.entities.ItemLikeStringKey;
import pl.itcraft.soma.core.model.entities.ItemPhoto;
import pl.itcraft.soma.core.model.entities.PhotoFile;
import pl.itcraft.soma.core.model.entities.Promotion;
import pl.itcraft.soma.core.model.entities.SearchKeyword;
import pl.itcraft.soma.core.model.entities.User;
import pl.itcraft.soma.core.model.entities.messages.BuyingConversation;
import pl.itcraft.soma.core.model.entities.messages.Message;
import pl.itcraft.soma.core.model.enums.Category;
import pl.itcraft.soma.core.model.enums.MessageType;
import pl.itcraft.soma.core.model.enums.PriceUnit;
import pl.itcraft.soma.core.objectify.OfyHelper;
import pl.itcraft.soma.core.objectify.OfyUtils;
import pl.itcraft.soma.core.search.SearchDocumentService;
import pl.itcraft.soma.core.search.SearchEntityDocument;
import pl.itcraft.soma.core.utils.BlockchainTransactionStatus;
import pl.itcraft.soma.core.utils.BlockchainUtils;
import pl.itcraft.soma.core.utils.ItemActions;
import pl.itcraft.soma.core.validators.ItemValidator;
import pl.itcraft.soma.core.validators.ValidationErrors;

public class ItemService {

	private final BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
	private final Logger logger = Logger.getLogger(ItemService.class.getName());
	private final SearchDocumentService searchDocumentService = new SearchDocumentService();
	private final UserService userService = new UserService();

	public ItemPhoto saveItemPhoto(User user, PhotoFile photoFile, Integer height, Integer width) {
		ItemPhoto itemPhoto = new ItemPhoto();
		itemPhoto.setUserId(user.getId());
		itemPhoto.setUrl(photoFile.getUrl());
		itemPhoto.setFileKey(photoFile.getFileKey());
		itemPhoto.setCreationDate(new Date());
		itemPhoto.setHeight(height);
		itemPhoto.setWidth(width);
		ObjectifyService.ofy().save().entity(itemPhoto).now();
		return itemPhoto;
	}

	public ItemContainer saveItem(Long id, final User user, String title, String description, Category category,
			final List<Long> photoIds, Boolean allowReselling, Long price, PriceUnit priceUnit, Double latitude,
			Double longitude, String locationName, Boolean isDraft, Boolean allowPromoting, Integer minimumFollowers, String signedTransaction, String blockchainId) throws ApiException {

		final Item item = id != null ? ObjectifyService.ofy().load().type(Item.class).id(id).now() : new Item();

		if (item == null || (item.getOwnerId() != null && !item.getOwnerId().equals(user.getId()))) {
			throw new ApiException(EC.NOT_FOUND, ErrorStatus.ITEM_NOT_FOUND);
		}
		
		if (blockchainId != null) {
			if (!user.getEthereumAddress().equals(BlockchainUtils.getOwnerOfIIC(user.getEthereumAddress(), blockchainId))) {
				throw new ApiException(EC.BAD_REQUEST, ErrorStatus.UNAUTHORIZED_ACTION);
			}
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
		item.setIsExpired(false);
		item.setAllowPromoting(allowPromoting);
		item.setMinimumFollowers(minimumFollowers);
		item.setPhotoIds(photoIds);

		if(item.getCreationDate() == null) {
			item.setCreationDate(new Date());
		}
		
		if(item.getExpirationDate() == null) {
			Date expirationDate = new DateTime(new Date()).plusDays(Constants.ITEM_EXPIRATION_DATE).toDate();
			item.setExpirationDate(expirationDate);
		}

		ValidationErrors errors = new ValidationErrors();
		new ItemValidator().validateItem(errors, item);
		ApiException.throwIfHasErrors(errors);

		final List<String> keysToDelete = new ArrayList<>();
		final List<ItemPhoto> oldItemPhotos = id != null
				? ObjectifyService.ofy().load().type(ItemPhoto.class).filter("itemId", item.getId()).list()
				: null;
		final List<ItemPhoto> currentItemPhotos = new ArrayList<>();

		if (blockchainId != null) {
			item.setBlockchainId(blockchainId);
		} else {
			String transactionHash = BlockchainUtils.sendTransaction(signedTransaction);
			item.setCreateTransactionHash(transactionHash);
		}

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
	
	public ItemPhoto getItemPhoto(Long itemId) {
		Item item = ObjectifyService.ofy().load().type(Item.class).id(itemId).now();
		if(item.getPhotoIds() == null) {
			return null;
		}
		return ObjectifyService.ofy().load().type(ItemPhoto.class).id(item.getPhotoIds().get(0)).now();
	}

	public Collection<ItemPhoto> getItemPhotos(List<Long> photoIds, Integer limit) {
		if(photoIds == null) {
			return new ArrayList<>();
		}
		return ObjectifyService.ofy().load().type(ItemPhoto.class).ids(photoIds.stream().limit(limit).collect(Collectors.toList())).values();
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
	
	public List<BlockchainIICDto> getIIC(User user) {
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		
		Function function = new Function(
				"balanceOf",
				Arrays.asList(new Address(user.getEthereumAddress())),
				Arrays.asList(new TypeReference<Uint256>(){}));
		String encodedFunction = FunctionEncoder.encode(function);
		try {
			EthCall response = web3.ethCall(Transaction.createEthCallTransaction(user.getEthereumAddress(), Constants.IIC_CONTRACT_ADDRESS, encodedFunction), DefaultBlockParameterName.LATEST).send();
			List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
			int tokensAmount = ((Uint256)results.get(0)).getValue().intValue();
			if (tokensAmount == 0) {
				return Collections.<BlockchainIICDto>emptyList();
			}
			List<BlockchainIICDto> result = new ArrayList<>(tokensAmount);
			for (int i = 0; i < tokensAmount; i++) {
				result.add(getIICDataOfOwnerByIndex(web3, user.getEthereumAddress(), i));
			}
			return result;
		} catch (IOException e) {
			logger.warning("IOException in sending transaction to blockchain");
			throw new RuntimeException(e);
		}
	}
	
	private BlockchainIICDto getIICDataOfOwnerByIndex(Web3j web3, String owner, int index) {
		Function function = new Function(
				"getIICDataOfOwnerByIndex",
				Arrays.asList(new Address(owner), new Uint256(index)),
				Arrays.asList(new TypeReference<Uint256>(){}, new TypeReference<Utf8String>(){}));
		String encodedFunction = FunctionEncoder.encode(function);
		try {
			EthCall response = web3.ethCall(Transaction.createEthCallTransaction(owner, Constants.IIC_CONTRACT_ADDRESS, encodedFunction), DefaultBlockParameterName.LATEST).send();
			List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
			if (results.isEmpty()) {
				return new BlockchainIICDto();
			}
			BigInteger tokenId = ((Uint256)results.get(0)).getValue();
			String name = ((Utf8String)results.get(1)).getValue();
			return new BlockchainIICDto(tokenId, name);
		} catch (IOException e) {
			logger.warning("IOException in sending transaction to blockchain");
			throw new RuntimeException(e);
		}
	}
	
	public void purchaseItemWithSCT(User user, Long acceptedOfferId, String approveSCTTransaction, String finalizeOfferTransaction) throws ApiException {
		Message acceptedOffer = getMessage(acceptedOfferId);
		if (acceptedOffer.getMessageType() != MessageType.OFFER_ACCEPT && acceptedOffer.getMessageType() != MessageType.RESELLING_ACCEPT) {
			throw new ApiException(EC.VALIDATION_ERROR, ErrorStatus.WRONG_OFFER_SELECTED);
		}
		BuyingConversation buyingConversation = ObjectifyService.ofy().load().type(BuyingConversation.class).id(acceptedOffer.getConversationId()).now();
		if (!user.getId().equals(buyingConversation.getBuyerId())) {
			throw new ApiException(EC.VALIDATION_ERROR, ErrorStatus.WRONG_OFFER_SELECTED);
		}
		BlockchainUtils.sendTransaction(approveSCTTransaction);
		String buyTransactionHash = BlockchainUtils.sendTransaction(finalizeOfferTransaction);
		Item item = getItem(buyingConversation.getItemId());
		item.setBuyTransactionHash(buyTransactionHash);
		ObjectifyService.ofy().save().entity(item).now();
	}
	
	public void changeIICOwner(Long itemId, Long newOwnerUserId) throws ApiException {
		Item item = getItem(itemId);
		User newOwner = userService.getOtherUserById(newOwnerUserId);
		changeIICOwner(item, newOwner);
	}

	public void changeIICOwner(Item item, User newOwner) {
		if (item.getBlockchainId() == null) {
			logger.warning("Item id: " + item.getId() + " has empty blockchainId");
			return;
		}
		if (newOwner.getEthereumAddress() == null) {
			logger.warning("User id: " + newOwner.getId() + " has empty ethereumAddress");
			return;
		}
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		Function function = new Function(
			"changeOwner",
			Arrays.asList(new Uint256(new BigInteger(item.getBlockchainId())), new Address(newOwner.getEthereumAddress())),
			Collections.<TypeReference<?>>emptyList()
			);
		try {
			Credentials credentials = WalletUtils.loadCredentials(Constants.ETHEREUM_WALLET_PASSWORD, OfyHelper.getServletContext().getRealPath("WEB-INF/ethereum_wallet.json"));
			BigInteger nonce = BlockchainUtils.getNextAvailableNonce(web3, credentials.getAddress());
			RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, Constants.GAS_PRICE, Constants.GAS_LIMIT, Constants.IIC_CONTRACT_ADDRESS, FunctionEncoder.encode(function));
			byte[] signedTransaction = TransactionEncoder.signMessage(rawTransaction, credentials);
			BlockchainUtils.sendTransaction(Numeric.toHexString(signedTransaction));
		} catch (IOException e) {
			logger.warning("IOException in reading ethereum wallet");
			throw new RuntimeException(e);
		} catch (CipherException e) {
			logger.warning("CipherException in reading ethereum wallet");
			throw new RuntimeException(e);
		}
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
				if(itemPhoto.getFileKey() != null) {
					blobstoreService.delete(new BlobKey(itemPhoto.getFileKey()));
					ObjectifyService.ofy().delete().entity(itemPhoto).now();
					counter++;
				}
			}
			itemPhotos = OfyUtils.listPagedEntities(queryParams, null, Constants.DELETE_UNUSED_PHOTOS_BATCH_SIZE, itemPhotos.getNextPageToken(), ItemPhoto.class);
		}
		logger.info("Successfully deleted unused item photos : " + counter);

	}

	public CollectionResponse<Item> searchItems(String keyword, Category category, Double latitude, Double longitude, Integer minRange, Integer maxRange, User user, Boolean followersItems, Boolean friendsItems, String nextPageToken, Integer limit){
		Date d1 = new Date();
		CollectionResponse<SearchEntityDocument> searchEntityDocuments = searchDocumentService.searchEntities(keyword, category, latitude, longitude, minRange, maxRange, user, followersItems, friendsItems, nextPageToken, limit);
		Date d2 = new Date();
		logger.info("searchEntities searchApi : " + (d2.getTime() - d1.getTime()));
		if(searchEntityDocuments == null || searchEntityDocuments.getItems() == null) {
			return CollectionResponse.<Item> builder().setItems(new ArrayList<Item>(0)).build();
		}

		Date d3 = new Date();
		List<Item> items = new ArrayList<>();
		for(SearchEntityDocument searchEntityDocument : searchEntityDocuments.getItems()) {
			items.add(ObjectifyService.ofy().load().type(Item.class).id(Long.valueOf(searchEntityDocument.getId())).now());
		}
		Date d4 = new Date();
		logger.info("load item entities from datastore and map : " + (d4.getTime() - d3.getTime()));
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

	public Item itemAction(User user, Long id, ItemActions itemAction) throws ApiException {
		final Item item = getItem(id);
		final ItemLikeStringKey itemLike;
		switch(itemAction) {
		case LIKE:
			final ItemLikeStringKey itemAlreadyLiked = findItemLike(id, user.getId());
			if(itemAlreadyLiked != null) {
				throw new ApiException(EC.NOT_FOUND, ErrorStatus.ITEM_ALREADY_LIKED);
			}
			itemLike = new ItemLikeStringKey();
			itemLike.setKey(generateItemLikeKey(id, user.getId()));
			itemLike.setItemId(item.getId());
			itemLike.setUserId(user.getId());
			itemLike.setCreationDate(new Date());
			item.incrementNumberOfLikes();
			 ObjectifyService.ofy().transact(() -> {
				ObjectifyService.ofy().save().entities(itemLike, item).now();
			});
			break;
		case UNLIKE:
			itemLike = findItemLike(user.getId(), id);
			if(itemLike == null) {
				throw new ApiException(EC.NOT_FOUND, ErrorStatus.ITEM_LIKE_NOT_FOUND);
			}
			item.decrementNumberOfLikes();
			ObjectifyService.ofy().transact(() -> {
				ObjectifyService.ofy().delete().entity(itemLike).now();
				ObjectifyService.ofy().save().entities(item).now();
			});

			break;
		default:
			break;
		}
		return item;
	}


	private ItemLikeStringKey findItemLike(Long userId, Long itemId) {
		return ObjectifyService.ofy().consistency(Consistency.STRONG).load().type(ItemLikeStringKey.class).id(generateItemLikeKey(itemId, userId)).now();
		
	}

	private String generateItemLikeKey(Long itemId, Long userId) {
		return itemId + "_" + userId;
	}

	public Boolean isLiked(Long itemId, Long userId) {
		return findItemLike(itemId, userId) != null;
	}

	public CollectionResponse<Item> getLikedItems(User user, String nextPageToken, Integer limit) {

		if(limit == null || limit > Constants.LIKED_ITEMS_MAX_LIMIT) {
			limit = Constants.LIKED_ITEMS_MAX_LIMIT;
		}

		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("userId", user.getId());
		CollectionResponse<ItemLikeStringKey> itemLikes = OfyUtils.listPagedEntities(queryParams, "-creationDate", limit, nextPageToken, ItemLikeStringKey.class);

		if(itemLikes == null || itemLikes.getItems() == null) {
			return CollectionResponse.<Item> builder().setItems(new ArrayList<Item>(0)).build();
		}

		List<Item> items = new ArrayList<>();
		for(ItemLikeStringKey itemLike : itemLikes.getItems()) {
			items.add(ObjectifyService.ofy().load().type(Item.class).id(itemLike.getItemId()).now());
		}
		return CollectionResponse.<Item> builder().setItems(items).setNextPageToken(itemLikes.getNextPageToken()).build();
	}

	public CollectionResponse<Item> itemsAutocomplete(String keyword, String nextPageToken, Integer limit){
		
		if (keyword == null || keyword.isEmpty()) {
			return CollectionResponse.<Item> builder().setItems(new ArrayList<Item>(0)).build();
		}
		
		CollectionResponse<SearchEntityDocument> searchEntityDocuments = searchDocumentService.searchEntitiesForAutocomplete(keyword, nextPageToken, limit);

		if(searchEntityDocuments == null || searchEntityDocuments.getItems() == null) {
			return CollectionResponse.<Item> builder().setItems(new ArrayList<Item>(0)).build();
		}

		List<Item> items = new ArrayList<>();
		for(SearchEntityDocument searchEntityDocument : searchEntityDocuments.getItems()) {
			items.add(ObjectifyService.ofy().load().type(Item.class).id(Long.valueOf(searchEntityDocument.getId())).now());
		}
		return CollectionResponse.<Item> builder().setItems(items).setNextPageToken(searchEntityDocuments.getNextPageToken()).build();
	}
	
	public void deleteExpiredItems() {
		Date now = new Date();
		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("expirationDate <", now);
		queryParams.put("isExpired", false);
		
		CollectionResponse<Item> expiredItems = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_ITEM_BATCH_SIZE, null, Item.class);
		
		while(expiredItems != null && expiredItems.getItems() != null && !expiredItems.getItems().isEmpty() && expiredItems.getNextPageToken() != null) {
			for(Item item : expiredItems.getItems()) {
				try {
					searchDocumentService.deleteSearchEntityDocumentGlobal(item);
					searchDocumentService.proceedDeleteItem(item);
					searchDocumentService.proceedStopPromotingForExpiredItem(item);
					item.setIsExpired(true);
					
					ObjectifyService.ofy().save().entity(item).now();
				} catch (Exception e) {
					logger.log(Level.WARNING, "An error occurred during deleting expired items", e);
				}
			}
			expiredItems = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_ITEM_BATCH_SIZE, expiredItems.getNextPageToken(), Item.class);
		}
	}
	
	public void saveSearchKeyword(String rawKeyword, User user) throws ApiException {
		
		String keyword = rawKeyword.trim().replaceAll("\\s+", " ").toLowerCase();
		// no update for raw keyword ?
		SearchKeyword keywordEntity = ObjectifyService.ofy().load().type(SearchKeyword.class)
				.filter("userId", user.getId())
				.filter("keyword", keyword)
				.filter("searchIndex", user.getSearchHistoryIndex())
				.first().now();
		
		if (keywordEntity == null) {
			keywordEntity = new SearchKeyword();
		
			keywordEntity.setUserId(user.getId());
			keywordEntity.setRawKeyword(rawKeyword);
			keywordEntity.setKeyword(keyword);
			keywordEntity.setSearchIndex(user.getSearchHistoryIndex());
		}

		keywordEntity.setSearchDate(new Date());

		ObjectifyService.ofy().save().entity(keywordEntity).now();
	}
	
	public CollectionResponse<SearchKeyword> getSearchHistory(User user){
		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("userId", user.getId());
		queryParams.put("searchIndex", user.getSearchHistoryIndex());
		
		return OfyUtils.listPagedEntities(queryParams, "-searchDate", Constants.SEARCH_ENGINE_SEARCH_HISTORY_SIZE, null, SearchKeyword.class);
	}
	
	public void startSearchHistoryDeleting(User user) {

		user.setSearchHistoryIndex(user.getSearchHistoryIndex() + 1);
		
		ObjectifyService.ofy().save().entity(user).now();

		Queue queue = QueueFactory.getQueue(QueueUtils.UTILS_QUEUE_NAME);
		queue.add(TaskOptions.Builder.withUrl(QueueUtils.UTILS_QUEUE_URL)
				.param(QueueUtils.ACTION_PARAM_NAME, QueueUtils.DELETE_SEARCH_HISTORY_ACTION_NAME)
				.param(QueueUtils.USER_ID_PARAMETER_NAME, user.getId().toString()));
		
	}
	
	public void deleteSearchHistory(User user) {
		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("userId", user.getId());
		queryParams.put("searchIndex <", user.getSearchHistoryIndex());
		
		CollectionResponse<SearchKeyword> searchKeywords = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_KEYWORDS_SIZE, null, SearchKeyword.class);
		
		while(searchKeywords != null && searchKeywords.getItems() != null && !searchKeywords.getItems().isEmpty() && searchKeywords.getNextPageToken() != null) {
			for(SearchKeyword searchKeyword : searchKeywords.getItems()) {
				ObjectifyService.ofy().delete().entity(searchKeyword).now();
			}
			searchKeywords = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_KEYWORDS_SIZE, searchKeywords.getNextPageToken(), SearchKeyword.class);
		}
	}

	public void promoteItem(User user, Long itemId) throws ApiException {
		Item item = getItem(itemId);
		
		if(!item.getAllowPromoting() || item.getMinimumFollowers() > user.getNumberOfFollowers()) {
			throw new ApiException(EC.VALIDATION_ERROR, ErrorStatus.PROMOTING_NOT_ALLOWED);
		}
		
		Promotion promotion = ObjectifyService.ofy().load().type(Promotion.class).filter("itemId", itemId).filter("promotingUserId", user.getId()).first().now();
		if(promotion != null) {
			throw new ApiException(EC.VALIDATION_ERROR, ErrorStatus.ALREADY_PROMOTING);
		}
		
		searchDocumentService.enqueuePromotingAction(item, PromotingAction.START, user.getId(), null);
	}

	public void stopPromotingItem(User user, Long itemId) throws ApiException {
		Item item = getItem(itemId);
		Promotion promotion = ObjectifyService.ofy().load().type(Promotion.class).filter("itemId", itemId).filter("promotingUserId", user.getId()).first().now();
		if(promotion == null) {
			throw new ApiException(EC.VALIDATION_ERROR, ErrorStatus.PROMOTION_NOT_FOUND);
		}
		searchDocumentService.enqueuePromotingAction(item, PromotingAction.STOP, user.getId(), null);
	}
	
	public void checkBlockchainTransactionAndSetItemBlockchainIds() {
		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("blockchainId", null);
		queryParams.put("createTransactionHash >", null);
		
		CollectionResponse<Item> pendingItems = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_ITEM_BATCH_SIZE, null, Item.class);
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		while(pendingItems != null && pendingItems.getItems() != null && !pendingItems.getItems().isEmpty() && pendingItems.getNextPageToken() != null) {
			for(Item item : pendingItems.getItems()) {
				try {
					String itemBlockchainId = BlockchainUtils.getItemBlockchainIdFromTransactionHash(web3, item.getCreateTransactionHash());
					if (itemBlockchainId != null) {
						item.setBlockchainId(itemBlockchainId);
						item.setCreateTransactionHash(null);
						ObjectifyService.ofy().save().entity(item).now();
					}
				} catch (Exception e) {
					logger.log(Level.WARNING, "An error occurred during setting blockchain ids for pending items", e);
				}
			}
			pendingItems = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_ITEM_BATCH_SIZE, pendingItems.getNextPageToken(), Item.class);
		}
	}
	
	public void checkBlockchainTransactionAndSetItemSold() {
		Map<String, Object> queryParams = new HashMap<>();
		queryParams.put("buyTransactionHash >", null);
		
		CollectionResponse<Item> pendingItems = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_ITEM_BATCH_SIZE, null, Item.class);
		Web3j web3 = Web3j.build(new HttpService(Constants.ETHEREUM_NODE_URL));
		while(pendingItems != null && pendingItems.getItems() != null && !pendingItems.getItems().isEmpty() && pendingItems.getNextPageToken() != null) {
			for(Item item : pendingItems.getItems()) {
				try {
					BlockchainTransactionStatusDto transactionStatus = BlockchainUtils.checkTransactionStatus(web3, item.getBuyTransactionHash());
					if (transactionStatus.getStatus() == BlockchainTransactionStatus.SUCCESS) {
						item.setBuyTransactionHash(null);
						//TODO change to some more appropriate status
						item.setIsExpired(true);
						ObjectifyService.ofy().save().entity(item).now();
					} else if (transactionStatus.getStatus() == BlockchainTransactionStatus.FAIL) {
						logger.warning("Buy transaction failed, transactionHash: " + item.getBuyTransactionHash());
						item.setBuyTransactionHash(null);
						ObjectifyService.ofy().save().entity(item).now();
					}
				} catch (Exception e) {
					logger.log(Level.WARNING, "An error occurred during setting sold flag for items", e);
				}
			}
			pendingItems = OfyUtils.listPagedEntities(queryParams, null, Constants.SEARCH_ENGINE_ITEM_BATCH_SIZE, pendingItems.getNextPageToken(), Item.class);
		}
	}
	
	private Message getMessage(Long id) throws ApiException {
		Message message = ObjectifyService.ofy().load().type(Message.class).id(id).now();
		if(message == null) {
			throw new ApiException(EC.NOT_FOUND, ErrorStatus.MESSAGE_NOT_FOUND);
		}
		return message;
	}

}
