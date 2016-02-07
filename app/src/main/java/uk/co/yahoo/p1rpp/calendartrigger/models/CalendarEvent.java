package uk.co.yahoo.p1rpp.calendartrigger.models;

import java.util.GregorianCalendar;

public class CalendarEvent {

	private String nom;
	private GregorianCalendar startTime;
	private GregorianCalendar endTime;
	
	public CalendarEvent(String nom, GregorianCalendar startTime, GregorianCalendar endTime) {
		this.nom = nom;
		this.startTime = startTime;
		this.endTime = endTime;
	}
	
	public CalendarEvent(String nom, long startTime, long endTime) {
		this(nom, new GregorianCalendar(), new GregorianCalendar());
		this.startTime.setTimeInMillis(startTime);
		this.endTime.setTimeInMillis(endTime);
	}
	
	public GregorianCalendar getStartTime() {
		return startTime;
	}
	
	public GregorianCalendar getEndTime() {
		return endTime;
	}
	
	public String getNom() {
		return nom;
	}
}
