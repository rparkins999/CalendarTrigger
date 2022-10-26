package uk.co.yahoo.p1rpp.calendartrigger.Widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CheckBox;

public class DisabledCheckBox extends CheckBox {

    private boolean m_enabled = false;
    public DisabledCheckBox(Context context) {
        super(context);
        setAlpha(0.5F);
    }

    public DisabledCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(0.5F);
    }

    public DisabledCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setAlpha(0.5F);
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

    @Override
    public boolean performClick() {
        if (m_enabled) {
            return super.performClick();
        }
        else
        {
            // override the click to do nothing
            return true;
        }
    }
}
