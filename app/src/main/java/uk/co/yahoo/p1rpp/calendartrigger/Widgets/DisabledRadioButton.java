/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.Widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioButton;

/**
 * Created by rparkins on 22/03/17.
 * This behaves like a disabled RadioButton, but allows long clicks which I
 * use to pop up an explanation of why it is disabled
 */

public class DisabledRadioButton extends RadioButton {

    private boolean m_enabled = false;
    private View.OnClickListener m_listener;

    public DisabledRadioButton(Context context) {
        super(context);
        setAlpha(0.5F);
    }

    public DisabledRadioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(0.5F);
    }

    public DisabledRadioButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setAlpha(0.5F);
    }

    @Override
    public void setOnClickListener(View.OnClickListener l) {
        m_listener = l;
    }

    @Override
    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
        if (m_enabled) {
            setAlpha(1.0F);
        }
        else
        {
            setAlpha(0.5F);
        }
    }

    @Override
    public boolean isEnabled() {
        return m_enabled;
    }

    // suppress warning that we don't call super.performClick();
    @SuppressLint("ClickableViewAccessibility")
    @Override
    // Normally you can't un-click a RadioButton; this hack does it.
    public boolean performClick() {
        if (m_enabled) {
            setChecked(!isChecked());
            m_listener.onClick(this);
            return true;
        }
        else
        {
            // override the click to do nothing
            return true;
        }
    }
}
