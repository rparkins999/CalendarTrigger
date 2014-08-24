package com.PKH.calendarmute.models;

public class Calendar {
	
	private long id;
	private String displayName;
	private boolean synced;
	private boolean checked;

	public Calendar(long id, String displayName, boolean synced, boolean checked) {
		this.id = id;
		this.displayName = displayName;
		this.synced = synced;
		this.checked = checked;
	}
	
	public long getId() {
		return id;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public boolean isSynced() {
		return synced;
	}
	
	public boolean isChecked() {
		return checked;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
