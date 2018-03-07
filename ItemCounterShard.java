package pl.itcraft.soma.core.model.entities;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class ItemCounterShard {
	@Id
	private	String key;
	@Index
	private Long itemId;
	@Index
	private Boolean modified = false;
	private Integer counter = 0;

	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public Long getItemId() {
		return itemId;
	}
	public void setItemId(Long itemId) {
		this.itemId = itemId;
	}
	public Boolean getModified() {
		return modified;
	}
	public void setModified(Boolean modified) {
		this.modified = modified;
	}
	public Integer getCounter() {
		return counter;
	}
	public void setCounter(Integer counter) {
		this.counter = counter;
	}
	public void incrementCounter() {
		counter++;
	}

}
