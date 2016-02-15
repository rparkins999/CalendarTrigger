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

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class EditActivity extends Activity
    implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the
     * navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
            getFragmentManager().findFragmentById(R.id.navigation_drawer);

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
            R.id.navigation_drawer,
            (DrawerLayout)findViewById(R.id.drawer_layout));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = getIntent();
        String s = i.getStringExtra("classname");
        if (s != null) {
            setTitle(getString(R.string.editing, s));
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fm = getFragmentManager();
        fm.beginTransaction()
          .replace(R.id.container,
                   PlaceholderFragment.newInstance(position + 1))
          .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
          .addToBackStack(null)
          .commit();
    }

    public void onSectionAttached(int number) {
        switch (number)
        {
            case 1:
                setTitle(getString(R.string.title_section1));
                break;
            case 2:
                setTitle(getString(R.string.title_section2));
                break;
            case 3:
                setTitle(getString(R.string.title_section3));
                break;
            case 4:
                setTitle(getString(R.string.title_section4));
                break;
        }
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
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((EditActivity)activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
            View rootView =
                inflater.inflate(R.layout.fragment_edit, container, false);
            return rootView;
        }
    }

}
