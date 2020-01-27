package uk.co.yahoo.p1rpp.calendartrigger.Widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.Spinner;

public class ClickableSpinner extends Spinner {

    private boolean m_firstDraw = true;
    private View.OnLongClickListener m_help;
    private View.OnTouchListener m_touched;

    public ClickableSpinner(Context context) {
        super(context);
    }

    public void setup(View.OnLongClickListener help, View.OnTouchListener touched) {
        m_firstDraw = true;
        m_help = help;
        m_touched = touched;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (m_firstDraw) {
            View v = getChildAt(0);
            if (v != null) {
                v.setOnTouchListener(m_touched);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performClick();
                    }
                });
                v.setLongClickable(true);
                v.setOnLongClickListener(m_help);
                setOnTouchListener(m_touched);
                setLongClickable(true);
                setOnLongClickListener(m_help);
                m_firstDraw = false;
            }
        }
    }
}
