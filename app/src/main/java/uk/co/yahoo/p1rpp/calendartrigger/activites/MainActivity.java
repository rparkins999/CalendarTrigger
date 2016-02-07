package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.RPP.calendartrigger.PrefsManager;
import android.os.Bundle;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;
public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onPause() {
		// Don't start service until user finishes setup
		super.onPause();
		MuteService.startIfNecessary(this, "MainActivity");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.clear();
		super.onCreateOptionsMenu(menu);
		MenuItem mi;
		mi = menu.add(R.string.new_event_class);
		mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		super.onCreateOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		MenuItem mi;
		mi = menu.add(R.string.new_event_class);
		mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		super.onPrepareOptionsMenu(menu);
		int nc = PrefsManager.getNumClasses(this);
		for (int i = 0; i < nc; ++i)
		{
			if (PrefsManager.isClassUsed(this, i))
			{
				menu.add(getResources()
							 .getString(R.string.edit_event_class,
										PrefsManager.getClassName(this, i)));
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		String name = item.getTitle().toString();
		if (name.equals(getResources().getString(R.string.new_event_class)))
		{
		    DialogFragment newFragment = new CreateClassDialog();
		    newFragment.show(getFragmentManager(), "CreateClassDialog");
		}
		else
		{
			// edit (or delete) an existing event class
			name = name.substring(getResources()
				.getString(R.string.edit_event_class).indexOf('%'));
			Intent i = new Intent(this, EditActivity.class);
			i.putExtra("classname", name);
			startActivity(i);
		}
		return true;
	}

	public void doPositiveClick(text) {
		int n = PrefsManager.getNewClass(this);
		PrefsManager.setClassName(this, n, text);
		 invalidateOptionsMenu();
	}

	public void doNegativeClick() {
		// nothing
	}
}
