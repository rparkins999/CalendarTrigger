package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onStop() {
		// Don't start service until user finishes setup
		super.onStop();
		MuteService.startIfNecessary(this, "MainActivity");
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		super.onPrepareOptionsMenu(menu);
		MenuItem mi;
		mi = menu.add(R.string.new_event_class);
		mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		int nc = PrefsManager.getNumClasses(this);
		for (int i = 0; i < nc; ++i)
		{
			if (PrefsManager.isClassUsed(this, i))
			{
				menu.add(
					getResources().getString(R.string.edit_event_class,
											 PrefsManager.getClassName(this, i)));
			}
		}
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		invalidateOptionsMenu();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		String name = item.getTitle().toString();
		if (name.equals(getResources()
							.getString(R.string.new_event_class)))
		{
			CreateClassDialog newFragment = new CreateClassDialog();
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
}
