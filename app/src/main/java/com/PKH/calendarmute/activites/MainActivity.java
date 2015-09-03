package com.PKH.calendarmute.activites;

import com.PKH.calendarmute.R;
import com.PKH.calendarmute.service.MuteService;

import android.os.Bundle;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;

public class MainActivity extends Activity {
	
	public static final String TAG_TAB_CALENDARS = "calendars";
	public static final String TAG_TAB_ACTIONS = "actions";
	
	private static final String KEY_SAVED_CURRENT_TAB = "currentTab";
	
	public static final String ACTION_SHOW_ACTIONS = "showActions";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		ActionBar ab = getActionBar();
		ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		
		// Add tabs
		Tab tab = ab.newTab()
				.setText(R.string.agendas)
				.setTabListener(new CalendarMuteTabListener<CalendarsFragment>(this, TAG_TAB_CALENDARS, CalendarsFragment.class));
		ab.addTab(tab);
		
		tab = ab.newTab()
				.setText(R.string.actions)
				.setTabListener(new CalendarMuteTabListener<ActionsFragment>(this, TAG_TAB_ACTIONS, ActionsFragment.class));
		ab.addTab(tab);
		
		if(ACTION_SHOW_ACTIONS.equals(getIntent().getAction()))
			ab.setSelectedNavigationItem(1); // Show actions tab
		if(savedInstanceState != null && savedInstanceState.containsKey(KEY_SAVED_CURRENT_TAB))
			ab.setSelectedNavigationItem(savedInstanceState.getInt(KEY_SAVED_CURRENT_TAB));
		
		// Start service
		MuteService.startIfNecessary(this);
	}
	
	
	@Override
	protected void onSaveInstanceState(Bundle b) {
		super.onSaveInstanceState(b);
		
		b.putInt(KEY_SAVED_CURRENT_TAB, getActionBar().getSelectedNavigationIndex());
	}

}
