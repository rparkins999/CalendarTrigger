/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioButton;

/**
 * Created by rparkins on 22/03/17.
 * This behaves like a disabled RadioButton, but allows long clicks which I
 * use to pop up an explanation of why it is disabled
 */

public class DisabledRadioButton extends RadioButton {

    public DisabledRadioButton(Context context) {
        super(context);
        setTextColor(0x80000000);
        setAlpha(0.4F);
    }

    public DisabledRadioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTextColor(0x80000000);
        setAlpha(0.4F);
    }

    public DisabledRadioButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTextColor(0x80000000);
        setAlpha(0.4F);
    }

    public DisabledRadioButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setTextColor(0x80000000);
        setAlpha(0.4F);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean performClick() {
        // override the click to do nothing
        return true;
    }
}
