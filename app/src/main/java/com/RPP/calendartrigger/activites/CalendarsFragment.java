package com.RPP.calendartrigger.activites;

import com.RPP.calendartrigger.PrefsManager;
import com.RPP.calendartrigger.R;
import com.RPP.calendartrigger.calendar.CalendarProvider;
import com.RPP.calendartrigger.models.Calendar;
import com.RPP.calendartrigger.service.MuteService;
import com.RPP.calendartrigger.views.CalendarAdapter;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

public class CalendarsFragment extends Fragment {
	
	private ListView lstAgendas;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.layout_lst_agendas, container, false);
		
		lstAgendas = (ListView) res.findViewById(R.id.lst_calendars);
		
		// Fill calendars
		refreshCalendars(false);
		
		return res;
	}
	
	public void refreshCalendars(boolean forceRefresh) {
		Calendar[] savedCalendars;
		if(!forceRefresh && (savedCalendars = CalendarProvider.getCachedCalendars()) != null)
			fillCalendars(savedCalendars);
		else
			new CalendarGetter().execute(true);
	}
	
	private class CalendarGetter extends AsyncTask<Boolean, Void, Calendar[]> {
		@Override
		protected Calendar[] doInBackground(Boolean... params) {
			// Fetch calendars
			Activity a = getActivity();
			if(a == null) // Fragment already detached
				return null;
			
			CalendarProvider provider = new CalendarProvider(a);

			return provider.listCalendars(params[0]);
		}
		
		@Override
		protected void onPostExecute(Calendar[] result) {
			fillCalendars(result);
		}
	};
	
	private void fillCalendars(Calendar[] calendars) {
		
		Activity a = getActivity();
		if(a == null) // Fragment already detached
			return;
		
		if(calendars == null) {
			Toast.makeText(a, R.string.erreur_listing_agendas, Toast.LENGTH_LONG).show();
			return;
		}
		
		CalendarAdapter adapter = new CalendarAdapter(getActivity(), calendars);
		lstAgendas.setAdapter(adapter);
		
		// Restore checked items in the list
		for(int i=0, max = lstAgendas.getCount(); i<max; i++) {
			lstAgendas.setItemChecked(i, adapter.getItem(i).isChecked());
		}
		
		adapter.setItemCheckedChangedListener(new CalendarAdapter.ItemCheckedChangedListener() {
			@Override
			public void onItemCheckedChanged() {
				
				Activity a = getActivity();
				PrefsManager.saveCalendars(a, lstAgendas.getCheckedItemIds());
				
				// Remove cached calendars (now invalid)
				CalendarProvider.invalidateCalendars();
				
				// Launch service to check if there are events now
				MuteService.startIfNecessary(a, "fillCalendars");
			}
		});
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_refresh_calendars:
			refreshCalendars(true);
			return true;
		default:
			return false;
		}
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
	}
}
