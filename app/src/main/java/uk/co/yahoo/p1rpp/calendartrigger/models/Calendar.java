/*
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.models;

public class Calendar {
	
	private long id;
	private String displayName;
	private boolean synced;

	public Calendar(long id, String displayName, boolean synced) {
		this.id = id;
		this.displayName = displayName;
		this.synced = synced;
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

	@Override
	public String toString() {
		return displayName;
	}
}
