package com.RPP.calendartrigger.activites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.RPP.calendartrigger.PrefsManager;
import com.RPP.calendartrigger.R;
import com.RPP.calendartrigger.service.MuteService;

public class MainActivity extends Activity {

	static final int CREATE_CLASS_REQUEST = 1;
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
		/* we use a dynamic menu, so we don't inflate it here */
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		MenuItem mi;
		mi = menu.add(R.string.NewEventClass);
		mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		int nc = PrefsManager.getNumClasses(this);
		for (int i = 0; i < nc; ++i)
		{
			if (PrefsManager.isClassUsed(this, i))
			{
				menu.add(getResources()
							 .getString(R.string.EditEventClass,
										PrefsManager.getClassName(this, i)));
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		String name = item.getTitle().toString();
		if (name.equals(getResources().getString(R.string.NewEventClass)))
		{
			// create a new event class
			startActivityForResult(new Intent(this, CreateActivity.class),
								   CREATE_CLASS_REQUEST);
		}
		else
		{
			// edit (or delete) an existing event class
			name = name.substring(
				getResources().getString(R.string.EditEventClass).indexOf('%'));
			Intent i = new Intent(this, EditActivity.class);
			i.putExtra("classname", name);
			startActivity(new Intent(this, EditActivity.class));
		}
		return true;
	}

	@Override
	protected void onActivityResult(
		int requestCode, int resultCode, Intent data) {
		if ((requestCode == CREATE_CLASS_REQUEST)
			&& (resultCode == RESULT_OK))
		{
			Intent i = new Intent(this, EditActivity.class);
			i.putExtras(data.getExtras());
			startActivity(new Intent(this, EditActivity.class));
		}
	}
}
