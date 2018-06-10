package pl.itcraft.soma.core.search;
import java.util.logging.Logger;

import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.search.Cursor;
import com.google.appengine.api.search.Index;
import com.google.appengine.api.search.Query;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.Results;
import com.google.appengine.api.search.ScoredDocument;

import pl.itcraft.soma.core.Constants;
import pl.itcraft.soma.core.model.entities.User;

public class SearchUserService {

	private static final Logger logger = Logger.getLogger(SearchUserService.class.getName());

	public CollectionResponse<SearchUserDocument> searchUsers(String keyword, String nextPageToken, Integer limit) {

		if(limit == null || limit > Constants.USERS_SEARCH_MAX_LIMIT) {
			limit = Constants.USERS_SEARCH_MAX_LIMIT;
		}

		//Search by keyword
		StringBuilder queryBuilder = new StringBuilder();
		if (keyword != null && !keyword.isEmpty()) {
			queryBuilder = queryBuilder.append("searchableTextValue = ").append(keyword.toLowerCase());
		}

		QueryOptions.Builder qob = QueryOptions.newBuilder()
				.setCursor(nextPageToken != null && !nextPageToken.isEmpty() ?
						Cursor.newBuilder().build(nextPageToken) : Cursor.newBuilder().build())
				.setLimit(limit);

		logger.info("Executing query: " + queryBuilder.toString());

		Query query = Query.newBuilder().setOptions(qob.build()).build(queryBuilder.toString());

		Index index = DocIndexFactory.getIndex(DocIndexFactory.USERS_INDEX);
		Results<ScoredDocument> results = index.search(query);

		logger.info("Found number of " + results.getNumberReturned() + " returned results.");

		CollectionResponse.Builder<SearchUserDocument> builder = CollectionResponse.<SearchUserDocument>builder()
				.setItems(SearchUserDocument.fromResults(results))
				.setNextPageToken(results != null && results.getCursor() != null ? results.getCursor().toWebSafeString() : null);

		return builder.build();
	}

	public void updateSearchUserDocument(User user) {
		Index index = DocIndexFactory.getUsersIndex();
 		if (index.get(user.getId().toString()) != null) {
 			index.delete(user.getId().toString());
 		}
		
		SearchUserDocument document = new SearchUserDocument(user);
		index.put(document.toDocument());
	}
}
