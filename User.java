package pl.itcraft.soma.core.model.entities;

import java.util.Date;
import java.util.Set;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Index;

@Entity
public class User extends BaseEntity {

	@Index
	private String accessToken;

	private String username;

	@Index
	private String usernameIdx;

	@Index
	private Long facebookId;

	@Index
	private String email;

	@Index
	private String emailIdx;

	@Index
	private String emailActivationToken;

	private Boolean emailActivated;

	@Index
	private String passwordResetToken;
	private Date passwordResetTokenExpirationDate;
	private String password;

	private PhotoFile photoFile;

	private Set<String> fcmIds;

	@Index
	private Date registrationDate;

	private String locationName;
	private Double latitude;
	private Double longitude;

	private String about;

	private Integer numberOfFollowers;
	private Integer numberOfFollowings;
	private Integer numberOfItems;

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public Long getFacebookId() {
		return facebookId;
	}

	public void setFacebookId(Long facebookId) {
		this.facebookId = facebookId;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
		if (this.email != null) {
			this.emailIdx = this.email.toLowerCase().trim();
		}
	}

	public String getEmailActivationToken() {
		return emailActivationToken;
	}

	public void setEmailActivationToken(String emailActivationToken) {
		this.emailActivationToken = emailActivationToken;
	}

	public String getPasswordResetToken() {
		return passwordResetToken;
	}

	public void setPasswordResetToken(String passwordResetToken) {
		this.passwordResetToken = passwordResetToken;
	}

	public Date getPasswordResetTokenExpirationDate() {
		return passwordResetTokenExpirationDate;
	}

	public void setPasswordResetTokenExpirationDate(Date passwordResetTokenExpirationDate) {
		this.passwordResetTokenExpirationDate = passwordResetTokenExpirationDate;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public PhotoFile getPhotoFile() {
		return photoFile;
	}

	public void setPhotoFile(PhotoFile photoFile) {
		this.photoFile = photoFile;
	}

	public Boolean getEmailActivated() {
		return emailActivated != null ? emailActivated : false;
	}

	public void setEmailActivated(Boolean emailActivated) {
		this.emailActivated = emailActivated;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
		if (this.username != null) {
			this.usernameIdx = this.username.toLowerCase().trim();
		}
	}

	public Set<String> getFcmIds() {
		return fcmIds;
	}

	public void setFcmIds(Set<String> fcmIds) {
		this.fcmIds = fcmIds;
	}

	public Date getRegistrationDate() {
		return registrationDate;
	}

	public void setRegistrationDate(Date registrationDate) {
		this.registrationDate = registrationDate;
	}

	public String getUsernameIdx() {
		return usernameIdx;
	}

	public void setUsernameIdx(String usernameIdx) {
		this.usernameIdx = usernameIdx;
	}

	public String getEmailIdx() {
		return emailIdx;
	}

	public void setEmailIdx(String emailIdx) {
		this.emailIdx = emailIdx;
	}

	public String getLocationName() {
		return locationName;
	}

	public void setLocationName(String locationName) {
		this.locationName = locationName;
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

	public String getAbout() {
		return about;
	}

	public void setAbout(String about) {
		this.about = about;
	}

	public Integer getNumberOfFollowers() {
		return numberOfFollowers != null ? numberOfFollowers : 0;
	}

	public void setNumberOfFollowers(Integer numberOfFollowers) {
		if(numberOfFollowers < 0)
			numberOfFollowers = 0;
		this.numberOfFollowers = numberOfFollowers;
	}

	public Integer getNumberOfFollowings() {
		return numberOfFollowings != null ? numberOfFollowings : 0;
	}

	public void setNumberOfFollowings(Integer numberOfFollowings) {
		if(numberOfFollowings < 0)
			numberOfFollowings = 0;
		this.numberOfFollowings = numberOfFollowings;
	}

	public Integer getNumberOfItems() {
		return numberOfItems != null ? numberOfItems : 0;
	}

	public void setNumberOfItems(Integer numberOfItems) {
		if(numberOfItems < 0)
			numberOfItems = 0;
		this.numberOfItems = numberOfItems;
	}

	public void incrementNumberOfFollowers() {
		if(numberOfFollowers == null) {
			numberOfFollowers = 0;
		}
		numberOfFollowers++;
	}

	public void decrementNumberOfFollowers() {
		if(numberOfFollowers != null && numberOfFollowers > 0) {
			numberOfFollowers--;
		}
	}

	public void incrementNumberOfFollowings() {
		if(numberOfFollowings == null) {
			numberOfFollowings = 0;
		}
		numberOfFollowings++;
	}

	public void decrementNumberOfFollowings() {
		if(numberOfFollowings != null && numberOfFollowings > 0) {
			numberOfFollowings--;
		}
	}

	public void incrementNumberOfItems() {
		if(numberOfItems == null) {
			numberOfItems = 0;
		}
		numberOfItems++;
	}

	public void decrementNumberOfItems() {
		if(numberOfItems != null && numberOfItems > 0) {
			numberOfItems--;
		}
	}


}
