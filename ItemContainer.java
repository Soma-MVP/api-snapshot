package pl.itcraft.soma.core.model.entities;

import java.util.List;

public class ItemContainer extends Item {

	private Item item;
	private List<ItemPhoto> itemPhotos;

	public ItemContainer(Item item, List<ItemPhoto> itemPhotos) {
		this.item = item;
		this.itemPhotos = itemPhotos;
	}

	public Item getItem() {
		return item;
	}

	public void setItem(Item item) {
		this.item = item;
	}

	public List<ItemPhoto> getItemPhotos() {
		return itemPhotos;
	}

	public void setItemPhotos(List<ItemPhoto> itemPhotos) {
		this.itemPhotos = itemPhotos;
	}

}
