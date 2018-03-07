package pl.itcraft.soma.core.model.entities;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Index;

import pl.itcraft.soma.core.model.enums.Category;

@Entity
public class Item extends BaseEntity {

	@Index
	private Long ownerId;
	private String title;
	private String description;
	private Category category;
	private Boolean allowReselling;
	private Long price;
	private String priceUnit;
	private Double latitude;
	private Double longitude;
	private String locationName;
	@Index
	private Date creationDate;
	@Index
	private Boolean isDraft;

	private Integer numberOfViews = 0;

	public Long getOwnerId() {
		return ownerId;
	}
	public void setOwnerId(Long ownerId) {
		this.ownerId = ownerId;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public Category getCategory() {
		return category;
	}
	public void setCategory(Category category) {
		this.category = category;
	}
	public Boolean getAllowReselling() {
		return allowReselling;
	}
	public void setAllowReselling(Boolean allowReselling) {
		this.allowReselling = allowReselling;
	}
	public Long getPrice() {
		return price;
	}
	public void setPrice(Long price) {
		this.price = price;
	}
	public String getPriceUnit() {
		return priceUnit;
	}
	public void setPriceUnit(String priceUnit) {
		this.priceUnit = priceUnit;
	}
	public Double getLatitude() {
		return latitude;
	}
	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}
	public Double getLongitude() {
		return longitude;
	}
	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}
	public String getLocationName() {
		return locationName;
	}
	public void setLocationName(String locationName) {
		this.locationName = locationName;
	}
	public Date getCreationDate() {
		return creationDate;
	}
	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}
	public Boolean getIsDraft() {
		return isDraft;
	}
	public void setIsDraft(Boolean isDraft) {
		this.isDraft = isDraft;
	}
	public Integer getNumberOfViews() {
		return numberOfViews;
	}
	public void setNumberOfViews(Integer numberOfViews) {
		this.numberOfViews = numberOfViews;
	}

	public void addViews(Integer numberOfViews) {
		if(this.numberOfViews == null) {
			this.numberOfViews = 0;
		}
		this.numberOfViews += (numberOfViews != null && numberOfViews >= 0) ? numberOfViews : 0;
	}
}
