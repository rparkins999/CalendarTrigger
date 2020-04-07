/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.ClickableSpinner;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

/**
 * Created by rparkins on 05/07/16.
 */
public class DefineStartFragment extends DefineFragment {

    public DefineStartFragment() {
    }

    public static DefineStartFragment newInstance(String className) {
        DefineStartFragment fragment = new DefineStartFragment();
        fragment.className = className;
        fragment.italicClassName = "<i>" + htmlEncode(className) + "</i>";
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        owner = (EditActivity)getActivity();
        owner.setButtonVisibility(View.INVISIBLE);
        int classNum = PrefsManager.getClassNum( owner, className);
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        InputFilter[] lf = {
            new InputFilter.LengthFilter(6)
        };
        LinearLayout ll =
            (LinearLayout)owner.findViewById(R.id.dynamicscrollview);
        ll.removeAllViews();
        View.OnLongClickListener longClickListener =
            new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner,
                               fromHtml(getString(
                                   R.string.definestartpopup, italicClassName)),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        };
        TextView tv = new TextView(owner);
        tv.setText(R.string.longpresslabel);
        tv.setOnLongClickListener(longClickListener);
        ll.addView(tv, ww);
        tv = new TextView(owner);
        tv.setText(fromHtml(getString(R.string.definestartlist, italicClassName)));
        tv.setOnLongClickListener(longClickListener);
        ll.addView(tv, ww);
        LinearLayout lll = new LinearLayout(owner);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        lll.setPadding((int)(scale * 25.0), 0, 0, 0);
        minutesEditor = new EditText(owner);
        minutesEditor.setInputType(
            android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        minutesEditor.setFilters(lf);
        int i = PrefsManager.getBeforeMinutes(owner, classNum);
        minutesEditor.setText(String.valueOf(i > 0 ? i : -i),
            TextView.BufferType.EDITABLE);
        lll.addView(minutesEditor);
        tv = new TextView(owner);
        tv.setText(R.string.minutes);
        View.OnLongClickListener minuteslistener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, getString(R.string.startminuteshelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        };
        tv.setOnLongClickListener(minuteslistener);
        lll.addView(tv, ww);
        ArrayAdapter<?> ad = ArrayAdapter.createFromResource(
            owner, R.array.beforeorafter, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        beforeAfter = new ClickableSpinner(owner);
        beforeAfter.setAdapter(ad);
        beforeAfter.setSelection((i >= 0 ? 0 : 1));
        View.OnLongClickListener beforeafterlistener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, getString(R.string.beforeafterstarthelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        };
        beforeAfter.setup(beforeafterlistener, this);
        lll.addView(beforeAfter);
        tv = new TextView(owner);
        tv.setText(R.string.startminuteslabel);
        tv.setOnLongClickListener(minuteslistener);
        lll.addView(tv);
        ll.addView(lll, ww);
        tv = new TextView(owner);
        tv.setText(R.string.notuntillabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, getString(R.string.startnotuntilhelp),
                               Toast.LENGTH_LONG).show();
                return true;
            }

        });
        ll.addView(tv, ww);
        tv = new TextView(owner);
        tv.setPadding((int)(scale * 25.0), 0, 0, 0);
        tv.setText(R.string.devicepositionlabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner,
                    getString(R.string.devicepositionhelp, getString(R.string.atstart)),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        setupStateUi(ll, ww, true);
    }

    @Override
    public void onPause() {
        super.onPause();
        int classNum = PrefsManager.getClassNum(owner, className);
        String s = minutesEditor.getText().toString();
        int i = s.isEmpty() ? 0 : Integer.parseInt(s);
        PrefsManager.setAfterMinutes(owner, classNum, i);
        if (beforeAfter.getSelectedItemPosition() != 0) { i = -i; }
        PrefsManager.setBeforeMinutes(owner, classNum, i);
    }
}
