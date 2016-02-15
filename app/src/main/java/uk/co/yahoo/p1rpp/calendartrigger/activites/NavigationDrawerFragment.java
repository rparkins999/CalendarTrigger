/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import uk.co.yahoo.p1rpp.calendartrigger.R;

/**
 * Fragment used for managing interactions for and presentation of a navigation
 * drawer. See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented
 * here.
 */
public class NavigationDrawerFragment extends Fragment {

    /**
     * Remember the position of the selected item.
     */
    private static final String STATE_SELECTED_POSITION =
        "selected_navigation_drawer_position";

    /**
     * A pointer to the current callbacks instance (the Activity).
     */
    private NavigationDrawerCallbacks mCallbacks;

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerListView;
    private View mFragmentContainerView;

    private int mCurrentSelectedPosition = 0;

    public NavigationDrawerFragment() {
    }

    /**
     * Users of this fragment must call this method to set up the navigation
     * drawer interactions.
     *
     * @param fragmentId   The android:id of this fragment in its activity's
     *                     layout.
     * @param drawerLayout The DrawerLayout containing this fragment's UI.
     */
    public void setUp(int fragmentId, DrawerLayout drawerLayout) {
        mFragmentContainerView = getActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        mDrawerLayout.openDrawer(mFragmentContainerView);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try
        {
            mCallbacks = (NavigationDrawerCallbacks)activity;
        }
        catch (ClassCastException e)
        {
            throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null)
        {
            mCurrentSelectedPosition =
                savedInstanceState.getInt(STATE_SELECTED_POSITION);
        }

        // Select either the default item (0) or the last selected item.
        selectItem(mCurrentSelectedPosition);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        mDrawerListView = (ListView)inflater.inflate(
            R.layout.drawer_edit, container, false);
        mDrawerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectItem(position);
            }
        });
        mDrawerListView.setAdapter(new ArrayAdapter<String>(
            getActionBar().getThemedContext(),
            android.R.layout.simple_list_item_activated_1,
            android.R.id.text1,
            new String[] {
                getString(R.string.title_section1),
                getString(R.string.title_section2),
                getString(R.string.title_section3),
                getString(R.string.title_section4),
            }));
        mDrawerListView.setItemChecked(mCurrentSelectedPosition, true);
        return mDrawerListView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Indicate that this fragment would like to influence the set of actions in the action bar.
        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mDrawerLayout.openDrawer(mFragmentContainerView);
        mCallbacks = null;
    }

    private ActionBar getActionBar() {
        return getActivity().getActionBar();
    }

    private void selectItem(int position) {
        mCurrentSelectedPosition = position;
        if (mDrawerListView != null)
        {
            mDrawerListView.setItemChecked(position, true);
        }
        if (mDrawerLayout != null)
        {
            mDrawerLayout.closeDrawer(mFragmentContainerView);
        }
        if (mCallbacks != null)
        {
            mCallbacks.onNavigationDrawerItemSelected(position);
        }
    }

    /**
     * Callbacks interface that all activities using this fragment must
     * implement.
     */
    public static interface NavigationDrawerCallbacks {
        /**
         * Called when an item in the navigation drawer is selected.
         */
        void onNavigationDrawerItemSelected(int position);
    }
}
