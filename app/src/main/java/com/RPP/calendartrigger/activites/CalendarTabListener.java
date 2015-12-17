package com.RPP.calendartrigger.activites;

import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;

public class CalendarTabListener<T extends Fragment> implements TabListener {
	
	private Fragment fragment;
	private final Activity activity;
	private final String tag;
	private final Class<T> fragmentClass;
	
	public CalendarTabListener(Activity activity, String tag, Class<T> fragmentClass) {
		this.activity = activity;
		this.tag = tag;
		this.fragmentClass = fragmentClass;
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction fragmentTransaction) {
		
		fragment = activity.getFragmentManager().findFragmentByTag(tag);
		
		if(fragment == null) { // Create fragment
			fragment = Fragment.instantiate(activity, fragmentClass.getName());
			fragmentTransaction.add(android.R.id.content, fragment, tag);
		}
		else { // Reuse
			fragmentTransaction.attach(fragment);
		}
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction fragmentTransaction) {
		// Remove fragment
		if(fragment != null)
			fragmentTransaction.detach(fragment);
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction fragmentTransaction) {
		// Nothing here
	}

}
