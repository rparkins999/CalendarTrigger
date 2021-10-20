/*
 * Copyright (c) 2021. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;

/**
 * Created by rparkins on 20/10/21.
 * This behaves like a normal CheckBox, but allows long clicks when it is disabled,
 * which I use to pop up an explanation of why it is disabled.
 */
public class SoundBox extends CheckBox {
    private final Context ac;
    private boolean enabled = false;
    private int help;

    public SoundBox(Context context) {
        super(context);
        ac = context;
        setLongClickable(true);
    }

    public SoundBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        ac = context;
        setLongClickable(true);
    }

    public SoundBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ac = context;
        setLongClickable(true);
    }

    public void setHelpString (int resource) {
        help = resource;
    }

    public void enable(boolean enableIt) {
        enabled = enableIt;
        setTextColor(enableIt ? 0xFF000000 : 0x80000000);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean performClick() {
        if (enabled) {
            return super.performClick();
        } else {
            // override the click to do nothing
            return true;
        }
    }

    @Override
    public boolean performLongClick() {
        Toast.makeText(ac,
            enabled ? help : R.string.noPlaySoundHelp,
            Toast.LENGTH_LONG).show();
        return true;
    }
}
