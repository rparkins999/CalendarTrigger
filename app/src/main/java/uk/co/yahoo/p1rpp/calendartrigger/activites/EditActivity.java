/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

public class EditActivity extends Activity {

    private String className;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_activity);
    }

    public void setButtonVisibility(int visibility) {
        Button b = (Button)findViewById(R.id.deleteclassbutton);
        b.setVisibility(visibility);
        b = (Button)findViewById(R.id.defineclassbutton);
        b.setVisibility(visibility);
        b = (Button)findViewById(R.id.definestartbutton);
        b.setVisibility(visibility);
        b = (Button)findViewById(R.id.actionstartbutton);
        b.setVisibility(visibility);
        b = (Button)findViewById(R.id.definestopbutton);
        b.setVisibility(visibility);
        b = (Button)findViewById(R.id.actionstopbutton);
        b.setVisibility(visibility);
    }

    @Override
    protected void onResume() {
        final EditActivity me = this;
        super.onResume();
        Intent i = getIntent();
        className = i.getStringExtra("classname");
        Button b = (Button)findViewById(R.id.deleteclassbutton);
        b.setText(getString(
            R.string.deleteButtonLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PrefsManager.removeClass(me, className);
                // we can't edit once the class has gone
                finish();
            }
        });
        b = (Button)findViewById(R.id.defineclassbutton);
        b.setText(getString(
            R.string.defineButtonLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setButtonVisibility(View.INVISIBLE);
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = DefineClassFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "dcf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.definestartbutton);
        b.setText(getString(
            R.string.defineStartLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setButtonVisibility(View.INVISIBLE);
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = DefineStartFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "dtf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.actionstartbutton);
        b.setText(getString(
            R.string.actionStartLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setButtonVisibility(View.INVISIBLE);
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = ActionStartFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "atf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.definestopbutton);
        b.setText(getString(
            R.string.defineStopLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setButtonVisibility(View.INVISIBLE);
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = DefineStopFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "dpf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.actionstopbutton);
        b.setText(getString(
            R.string.actionStopLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setButtonVisibility(View.INVISIBLE);
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = ActionStopFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "apf")
                  .addToBackStack(null)
                  .commit();
            }
        });
    }
}
