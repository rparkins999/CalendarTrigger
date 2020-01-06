/*
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.SQLtable;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

public class MainActivity extends Activity {

	private Activity mThis;

	// At the moment we have only one possible version upgrade,
	// so "was" is unused.
	private void UpdatePrefs (String was, String now) {
		// Check for 3.0 comparisons and convert to 3.2.
		int n = PrefsManager.getNumClasses(this);
		for (int classNum = 0; classNum < n; ++classNum) {
			if (was.compareTo("3.1") < 0) {
				if (PrefsManager.isClassUsed(this, classNum)) {
					String eventName = PrefsManager.getEventName(this, classNum);
					int andIndex = 0;
					if (eventName.length() > 0) {
						PrefsManager.setEventComparison(this, classNum, andIndex,
							0, 0, 0, eventName);
						++andIndex;
						PrefsManager.removeEventName(this, classNum);
					}
					String eventLocation = PrefsManager.getEventLocation(
						this, classNum);
					if (eventLocation.length() > 0) {
						PrefsManager.setEventComparison(this, classNum, andIndex,
							0, 1, 0, eventLocation);
						++andIndex;
						PrefsManager.removeEventLocation(this, classNum);
					}
					String eventDescription
						= PrefsManager.getEventDescription(this, classNum);
					if (eventDescription.length() > 0) {
						PrefsManager.setEventComparison(this, classNum, andIndex,
							0, 2, 0, eventDescription);
						++andIndex;
						PrefsManager.removeEventDescription(this, classNum);
					}
				}
				if (was.compareTo("3.2") < 0) {
					PrefsManager.removeClassWaiting(this, classNum);
					PrefsManager.removeTriggered(this, classNum);
					PrefsManager.removeLastTriggerEnd(this, classNum);
					PrefsManager.removeLastActive(this, classNum);
				}
			}
		}
		PrefsManager.setPrefVersionCode(this, now);
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mThis = this;
		int apiVersion = android.os.Build.VERSION.SDK_INT;
		if (   (savedInstanceState == null)
			&& (apiVersion >= android.os.Build.VERSION_CODES.M))
		{
			PowerManager
				pmg = (PowerManager) getSystemService(Context.POWER_SERVICE);
			if ((pmg != null) && !pmg.isIgnoringBatteryOptimizations(
				    "uk.co.yahoo.p1rpp.calendartrigger"))
			{
				Intent intent = new Intent();
				intent.setAction(
					Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				String packageName = getPackageName();
				intent.setData(Uri.parse("package:" + packageName));
				startActivity(intent);
			}
		}
		PackageManager pm = getPackageManager();
		String was = PrefsManager.getPrefVersionCode(this);
		//noinspection CatchMayIgnoreException
		try
		{
			PackageInfo pi = pm.getPackageInfo(
				"uk.co.yahoo.p1rpp.calendartrigger", 0);
			String now = pi.versionName;
			if (now.compareTo(was) > 0)
			{
				UpdatePrefs(was, now);
			}
		}
		catch (PackageManager.NameNotFoundException e)
		{
			// If we can't find a version name, update anyway for safety
			// It does nothing if we already have version 3.1 or later prefs
			UpdatePrefs(was, was);
		}
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
		menu.add(Menu.NONE, -3, Menu.NONE, R.string.floating);
		menu.add(Menu.NONE, -2, Menu.NONE, R.string.settings);
		MenuItem mi = menu.add(Menu.NONE, -1, Menu.NONE, R.string.new_event_class);
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
		@SuppressLint("InflateParams")
		View v = getLayoutInflater().inflate(R.layout.dynamicscrollview, null);
		setContentView(v);
		int classNum = PrefsManager.getLastImmediate(mThis);
		if (classNum >= 0) {
			LinearLayout ll =
				(LinearLayout)mThis.findViewById(R.id.dynamicscrollview);
			final String className = PrefsManager.getClassName(mThis, classNum);
			final String italicName = "<i>" + htmlEncode(className) + "</i>";
			Button b = new Button(mThis);
			b.setText(fromHtml(getString(
				R.string.eventNowLabel, italicName)));
			b.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					long now = System.currentTimeMillis();
					ContentValues cv = new ContentValues();
					cv.put("ACTIVE_CLASS_NAME", className);
					cv.put("ACTIVE_IMMEDIATE", 1);
					cv.put("ACTIVE_EVENT_ID", 0);
					cv.put("ACTIVE_STATE", SQLtable.ACTIVE_START_WAITING);
					cv.put("ACTIVE_NEXT_ALARM", now + CalendarProvider.FIVE_MINUTES);
					cv.put("ACTIVE_STEPS_TARGET", 0);
					SQLtable table = new SQLtable(mThis, "ACTIVEINSTANCES");
					table.insert(cv);
					table.close();
					MuteService.startIfNecessary(mThis, "Immediate Event");
				}
			});
			b.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					Toast.makeText(mThis, fromHtml(getString(
						R.string.eventNowHelp, italicName)),
						Toast.LENGTH_LONG).show();
					return true;
				}
			});
			ll.addView(b);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int i = item.getItemId();
		if (i == -3)
		{
			Intent it = new Intent(this, FloatActivity.class);
			startActivity(it);
		}
		else if (i == -2)
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
