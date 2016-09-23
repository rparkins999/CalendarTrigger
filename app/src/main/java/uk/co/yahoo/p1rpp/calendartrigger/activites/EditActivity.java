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
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

public class EditActivity extends Activity {

    private String className;
    private String italicName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_activity);
    }

    public void setButtonVisibility(int visibility) {
        TextView tv = (TextView)findViewById (R.id.backgroundtext);
        tv.setVisibility(visibility);
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
        b = (Button)findViewById(R.id.eventnowbutton);
        b.setVisibility(visibility);
    }

    @Override
    protected void onResume() {
        final EditActivity ac = this;
        super.onResume();
        Intent i = getIntent();
        className = i.getStringExtra("classname");
        italicName = "<i>" + htmlEncode(className) + "</i>";
        TextView tv = (TextView)findViewById (R.id.backgroundtext);
        tv.setText(R.string.longpresslabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                               fromHtml(getString(R.string.editclassHelp,
                                                  className)),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        Button b = (Button)findViewById(R.id.deleteclassbutton);
        b.setText(fromHtml(getString(
            R.string.deleteButtonLabel, italicName)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PrefsManager.removeClass(ac, className);
                // we can't edit once the class has gone
                finish();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.deleteButtonHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.defineclassbutton);
        b.setText(fromHtml(getString(
            R.string.defineButtonLabel, italicName)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = DefineClassFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "dcf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.defineButtonHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.definestartbutton);
        b.setText(fromHtml(getString(
            R.string.defineStartLabel, italicName)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = DefineStartFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "dtf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.defineStartHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.actionstartbutton);
        b.setText(fromHtml(getString(
            R.string.actionStartLabel, italicName)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = ActionStartFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "atf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.actionStartHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.definestopbutton);
        b.setText(fromHtml(getString(
            R.string.defineStopLabel, italicName)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = DefineStopFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "dpf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.defineStopHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.actionstopbutton);
        b.setText(fromHtml(getString(
            R.string.actionStopLabel, italicName)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentTransaction ft =
                    getFragmentManager().beginTransaction();
                Fragment f = ActionStopFragment.newInstance(className);
                ft.replace(R.id.edit_activity_container, f, "apf")
                  .addToBackStack(null)
                  .commit();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.actionStopHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.eventnowbutton);
        b.setText(fromHtml(getString(
            R.string.eventNowLabel, italicName)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PrefsManager.getClassNum(ac, className);
                PrefsManager.setClassTriggered(
                    ac, PrefsManager.getClassNum(ac, className), true);
                MuteService.startIfNecessary(ac, "Immediate Event");
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.eventNowHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
    }
}
