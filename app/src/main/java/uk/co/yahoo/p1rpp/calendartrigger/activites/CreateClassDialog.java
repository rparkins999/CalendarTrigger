/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;

/**
 * Dialog to get a name for a new event class
 */
public class CreateClassDialog extends DialogFragment {

    public CreateClassDialog() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
        ViewGroup container,
        Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.create_class_dialog,
                                container, false);
        getDialog().setTitle(R.string.new_event_class);
        final Button create_button =
            (Button)v.findViewById(R.id.create_button);
        final EditText new_class_name =
            (EditText)v.findViewById(R.id.new_class_name);
        create_button.setEnabled(false); // initially we have no text
        create_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = new_class_name.getText().toString();
                Activity a = getActivity();
                int n = PrefsManager.getNewClass(a);
                PrefsManager.setClassName(a, n, s);
                Intent i = new Intent(a, EditActivity.class);
                i.putExtra("classname", s);
                startActivity(i);
                dismiss();
            }
        });
        new_class_name.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start,
                int before, int count) {
                // do nothing
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                int count, int after) {
                // do nothing
            }

            public void afterTextChanged(Editable e) {
                String s = e.toString();
                if (    s.isEmpty()
                    || (PrefsManager.getClassNum(getActivity(), s) >= 0))
                {
                    // no text or class already exists
                    create_button.setEnabled(false);
                }
                else
                {
                    create_button.setEnabled(true);
                }
            }
        });
        // Watch for button clicks.
        v.findViewById(R.id.cancel_button)
            .setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dismiss();
                }
            });
        return v;
    }

}
