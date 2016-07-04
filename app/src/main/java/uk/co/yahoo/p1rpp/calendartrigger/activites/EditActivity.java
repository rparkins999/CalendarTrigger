/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

public class EditActivity extends Activity
    implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the
     * navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private String className;
    private Fragment activeFragment;
    private boolean drawerOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        drawerOpen = false;

        mNavigationDrawerFragment = (NavigationDrawerFragment)
            getFragmentManager().findFragmentById(R.id.navigation_drawer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent i = getIntent();
        className = i.getStringExtra("classname");
        if (className != null)
        {
            setTitle(getString(R.string.editing, className));
        }

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
            R.id.navigation_drawer,
            (DrawerLayout)findViewById(R.id.drawer_layout));
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        switch (position)
        {
            case 0:
                setTitle(getString(R.string.title_section1));
                activeFragment =
                    DeleteClassFragment.newInstance(className);
                break;
            case 1:
                setTitle(getString(R.string.title_defining, className));
                activeFragment =
                    DefineClassFragment.newInstance(className);
                break;
            case 2:
                setTitle(getString(R.string.title_section3));
                activeFragment = PlaceholderFragment.newInstance(position + 1);
                break;
            case 3 :
                setTitle(getString(R.string.title_section4));
                activeFragment = PlaceholderFragment.newInstance(position + 1);
                break;
            default: return;
        }
        // update the main content by replacing fragments
        FragmentManager fm = getFragmentManager();
        fm.beginTransaction()
          .add(R.id.container, activeFragment)
          .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
          .commit();
        drawerOpen = true;
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
            View rootView =
                inflater.inflate(R.layout.fragment_edit, container, false);
            return rootView;
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen)
        {
            onResume();
            drawerOpen = false;
        }
        else
        {
            super.onBackPressed();
        }
    }

    private void closeActiveFragment(View v) {
        FragmentManager fm = getFragmentManager();
        fm.beginTransaction()
          .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
          .remove(activeFragment)
          .commit();
        activeFragment = null;
    }

    public void doCancel(View v) {
        closeActiveFragment(v);
        onResume();
    }

    public void doDeletion(View v) {
        PrefsManager.removeClass(this, className);
        closeActiveFragment(v);
        drawerOpen = false;

        // we can't edit once the class has gone
        finishAfterTransition();
    }
}
