/*
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onBackPressed() {
		// Don't start service until user finishes setup
		MuteService.startIfNecessary(this, "MainActivity");
		super.onBackPressed();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		super.onPrepareOptionsMenu(menu);
		MenuItem mi;
		mi = menu.add(Menu.NONE, -2, Menu.NONE, R.string.settings);
		mi = menu.add(Menu.NONE, -1, Menu.NONE, R.string.new_event_class);
		mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		int nc = PrefsManager.getNumClasses(this);
		for (int i = 0; i < nc; ++i)
		{
			if (PrefsManager.isClassUsed(this, i))
			{
				String className =
					"<i>" + htmlEncode(PrefsManager.getClassName(this, i)) +
					"</i>";
				menu.add(Menu.NONE, i, Menu.NONE,
					fromHtml(getResources().getString(
						R.string.edit_event_class, className)));
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
		int i = item.getItemId();
		if (i == -2)
		{
			Intent it = new Intent(this, SettingsActivity.class);
			startActivity(it);
		}
		else if (i == -1)
		{
			CreateClassDialog newFragment = new CreateClassDialog();
		    newFragment.show(getFragmentManager(), "CreateClassDialog");
		}
		else
		{
			// edit (or delete) an existing event class
			String name = PrefsManager.getClassName(this, i);
			Intent it = new Intent(this, EditActivity.class);
			it.putExtra("classname", name);
			startActivity(it);
		}
		return true;
	}
}
