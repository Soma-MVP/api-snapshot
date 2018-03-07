package pl.itcraft.soma.core.model.entities;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Following {

	@Id
	private Long id;

	@Index
	private Long followerId;

	@Index
	private Long followedId;

	@Index
	private Date creationDate;

	private Boolean isFriend;

	public Following() {}

	public Following(Long followerId, Long followedId) {
		this.followerId = followerId;
		this.followedId = followedId;
		this.creationDate = new Date();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public Long getFollowerId() {
		return followerId;
	}

	public void setFollowerId(Long followerId) {
		this.followerId = followerId;
	}

	public Long getFollowedId() {
		return followedId;
	}

	public void setFollowedId(Long followedId) {
		this.followedId = followedId;
	}

	public Boolean getIsFriend() {
		return isFriend;
	}

	public void setIsFriend(Boolean isFriend) {
		this.isFriend = isFriend;
	}

}
