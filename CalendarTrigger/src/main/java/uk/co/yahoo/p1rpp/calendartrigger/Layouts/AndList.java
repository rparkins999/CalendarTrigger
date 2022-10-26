package uk.co.yahoo.p1rpp.calendartrigger.Layouts;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import uk.co.yahoo.p1rpp.calendartrigger.activites.DefineClassFragment;

public class AndList extends LinearLayout {

    DefineClassFragment m_owner;
    Context m_context;
    int m_classNum;

    public AndList(Context context) {
        super(context);
        m_context = context;
    }

    public void setup(DefineClassFragment owner, int classNum, boolean first) {
        m_owner = owner;
        m_classNum = classNum;
        removeAllViews();
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        setOrientation(LinearLayout.VERTICAL);
        for (int andIndex = 0; true; ++andIndex) {
            OrList ol = new OrList(m_context);
            boolean done = ol.setup(this, m_classNum, andIndex, first);
            addView(ol, ww);
            if (done) { break; }
            first = false;
        }
    }

    public void updatePreferences(int classNum) {
        int n = getChildCount();
        for (int andIndex = 0; andIndex < n; ++andIndex) {
            OrList ol = (OrList)getChildAt(andIndex);
            ol.updatePreferences();
        }
    }

    public void forceTouchMode() {
        m_owner.forceTouchMode();
    }

    public void setChildEmpty(int andIndex, boolean empty) {
        int n = getChildCount() - 1;
        if (empty)
        {
            // We removed the only item in an orList, close up
            removeViewAt(andIndex);
            for (; andIndex < n; ++andIndex) {
                OrList ol = (OrList)getChildAt(andIndex);
                ol.decrementAndIndex();
            }
        }
        else
        {
            // Only the last one can have changed from empty to nonempty,
            // so add a new empty one after it.
            ++andIndex;
            OrList ol = new OrList(m_context);
            ol.setup(this, m_classNum, andIndex, false);
            addView(ol);
        }
    }
}
