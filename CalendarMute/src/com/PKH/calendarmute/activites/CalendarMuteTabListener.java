package com.PKH.calendarmute.activites;

import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;

public class CalendarMuteTabListener<T extends Fragment> implements TabListener {
	
	private Fragment fragment;
	private final Activity activity;
	private final String tag;
	private final Class<T> fragmentClass;
	
	public CalendarMuteTabListener(Activity activity, String tag, Class<T> fragmentClass) {
		this.activity = activity;
		this.tag = tag;
		this.fragmentClass = fragmentClass;
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction fragmentTransaction) {
		
		fragment = activity.getFragmentManager().findFragmentByTag(tag);
		
		if(fragment == null) { // Création du fragment
			fragment = Fragment.instantiate(activity, fragmentClass.getName());
			fragmentTransaction.add(android.R.id.content, fragment, tag);
		}
		else { // Réutilisation
			fragmentTransaction.attach(fragment);
		}
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction fragmentTransaction) {
		// Suppression du fragment
		if(fragment != null)
			fragmentTransaction.detach(fragment);
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction fragmentTransaction) {
		// Rien ici
	}

}
