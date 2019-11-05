/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.Layouts;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;


public class OrList extends LinearLayout {

    Context m_context;
    AndList m_owner;
    int m_classNum;
    int m_andIndex;
    boolean m_first;

    public OrList(Context context) {
        super(context);
        m_context = context;
    }

    // first is true if this is the first condition on this screen:
    // this can happen if there are no calendars.
    public boolean setup(AndList owner, int classNum, int andIndex, boolean first) {
        m_owner = owner;
        m_classNum = classNum;
        m_andIndex = andIndex;
        m_first = first;
        removeAllViews();
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        setOrientation(LinearLayout.VERTICAL);
        int prefix = m_first ? 0 : 1;
        int orIndex;
        for (orIndex = 0; true; ++orIndex) {
            OrItem oi = new OrItem(m_context);
            boolean done = oi.setup(
                this, m_classNum, m_andIndex, orIndex, prefix);
            addView(oi);
            if (done) { break; }
            prefix = 2;
        }
        return orIndex == 0;
    }

    public void updatePreferences() {
        int n = getChildCount();
        for (int orIndex = 0; orIndex < n; ++orIndex) {
            OrItem oi = (OrItem)getChildAt(orIndex);
            oi.updatePreferences();
        }
    }

    public void setChildEmpty(int orIndex, boolean empty) {
        int n = getChildCount() - 1;
        if (empty)
        {
            // We removed one, close up list.
            int prefix = (orIndex == 0) ? (m_first ? 0 : 1) : 2;
            removeViewAt(orIndex);
            for (; orIndex < n; ++orIndex) {
                OrItem oi = (OrItem)getChildAt(orIndex);
                oi.decrementOrIndex(prefix);
                prefix = 2;
            }
            PrefsManager.setEventComparison(m_context, m_classNum, m_andIndex,
                orIndex, 0, 0, "");
            if (n == 1)
            {
                // If there was a real one and an empty one,
                // and we removed the real one, our list has become empty.
                m_owner.setChildEmpty(m_andIndex, true);
            }
            m_owner.forceTouchMode();
        }
        else
        {
            // Only the last one can have changed from empty to nonempty,
            // so add a new empty one after it.
            ++orIndex;
            OrItem oi = new OrItem(m_context);
            oi.setup(
                this, m_classNum, m_andIndex, orIndex, 2);
            addView(oi);
            if (n == 0)
            {
                // If there was only an empty one before,
                // our list has become nonempty.
                m_owner.setChildEmpty(m_andIndex, false);
            }
        }
    }

    public void decrementAndIndex() {
        --m_andIndex;
        int prefix = m_first ? 0 : 1;
        int n = getChildCount();
        for (int orIndex = 0; orIndex < n; ++orIndex) {
            ((OrItem)getChildAt(orIndex)).decrementAndIndex(prefix);
            prefix = 2;
        }
    }
}
