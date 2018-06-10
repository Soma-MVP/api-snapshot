package pl.itcraft.soma.core.search;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.Results;
import com.google.appengine.api.search.ScoredDocument;
import com.google.appengine.api.search.Document.Builder;

import pl.itcraft.soma.core.model.entities.User;

public class SearchUserDocument {
	
	private String id;
	private String searchableTextValue;
	private Date creationDate;
	
	public SearchUserDocument(User user) {
		this.id = user.getId().toString();
		setSearchableTextValue(user.getUsernameIdx());
		this.creationDate = user.getRegistrationDate();
	}
	
	public SearchUserDocument(Document document) {
		this.id = document.getId();
		if (document.getFieldNames().contains("searchableTextValue")) {
			this.searchableTextValue = document.getOnlyField("searchableTextValue").getText() ;
		}
		this.creationDate = document.getOnlyField("creationDate").getDate();
	}
	
	public static List<SearchUserDocument> fromResults(Results<ScoredDocument> results) {
		List<SearchUserDocument> items = new ArrayList<>(results.getNumberReturned());
		for (ScoredDocument doc : results.getResults()) {
			items.add(new SearchUserDocument(doc));
		}
		return items;
	}
	
	public Document toDocument() {
		Builder builder = Document.newBuilder()
				.setId(id)
				.setRank((int) (creationDate.getTime()/1000));

		if (searchableTextValue != null) {
			builder = builder
					.addField(Field.newBuilder().setName("searchableTextValue").setText(searchableTextValue));
		}
		if (creationDate != null) {
			builder = builder
					.addField(Field.newBuilder().setName("creationDate").setDate(creationDate));
		}

		return builder.build();
	}
	
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getSearchableTextValue() {
		return searchableTextValue;
	}
	
	public void setSearchableTextValue(String searchableTextValue) {
		StringBuilder builder = new StringBuilder(searchableTextValue);
		for (String prefix : generatePrefixes(searchableTextValue, 2)) {
			builder.append(" ").append(prefix);
		}
		this.searchableTextValue = builder.toString();
	}
	
	private Set<String> generatePrefixes(String keyword, int minPrefixLength) {
		Set<String> prefixes = new HashSet<>();
		if (keyword.length() <= minPrefixLength) {
			prefixes.add(keyword);
		} else {
			for (int j = minPrefixLength; j <= keyword.length(); j++) {
				prefixes.add(keyword.substring(0, j));
			}
		}
		return prefixes;
	}
	
}
