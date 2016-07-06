/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

public class EditActivity extends Activity {

    private String className;
    private Activity me = this; // needed for nested classes
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
    }

    @Override
    protected void onResume() {
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
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                ft.add(DefineClassFragment.newInstance(className), "dc")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.definestartbutton);
        b.setText(getString(
            R.string.defineStartLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                ft.add(DefineStartFragment.newInstance(className), "")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.actionstartbutton);
        b.setText(getString(
            R.string.actionStartLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                ft.add(ActionStartFragment.newInstance(className), "")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.definestopbutton);
        b.setText(getString(
            R.string.defineStopLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                ft.add(DefineStopFragment.newInstance(className), "")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b = (Button)findViewById(R.id.actionstopbutton);
        b.setText(getString(
            R.string.actionStopLabel, className));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                ft.add(ActionStopFragment.newInstance(className), "")
                  .addToBackStack(null)
                  .commit();
            }
        });
    }
}
