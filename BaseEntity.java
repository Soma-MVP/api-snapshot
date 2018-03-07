package pl.itcraft.soma.core.model.entities;

import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Ignore;

public abstract class BaseEntity {

	@Id
	private Long id;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
