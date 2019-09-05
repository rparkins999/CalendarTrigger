package uk.co.yahoo.p1rpp.calendartrigger.Layouts;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

import static android.text.InputType.TYPE_CLASS_TEXT;

public class OrItem extends HorizontalScrollView
    implements TextWatcher, OnFocusChangeListener {

    Context m_context;
    OrList m_owner;
    int m_classNum;
    int m_andIndex;
    int m_orIndex;
    LinearLayout m_layout;
    TextView m_prefix;
    Spinner m_nameSelector;
    Spinner m_contSelector;
    EditText m_matchString;
    boolean m_wasEmpty;

    public void beforeTextChanged(CharSequence cs, int start, int count, int after) {}
    public void onTextChanged(CharSequence cs, int start, int count, int after) {}

    public void afterTextChanged(Editable e) {
        if (e.toString().isEmpty())
        {
            if (!m_wasEmpty)
            {
                new MyLog(m_context,"OrItem["
                    + String.valueOf(m_classNum)
                    + " " + String.valueOf(m_andIndex)
                    + " " + String.valueOf(m_orIndex)
                    + "] changed from nonempty to empty");
                m_owner.setChildEmpty(m_orIndex, true);
                m_wasEmpty = true;
            }
        }
        else
        {
            if (m_wasEmpty)
            {
                new MyLog(m_context,"OrItem["
                    + String.valueOf(m_classNum)
                    + " " + String.valueOf(m_andIndex)
                    + " " + String.valueOf(m_orIndex)
                    + "] changed from empty to nonempty");
                m_owner.setChildEmpty(m_orIndex, false);
                m_wasEmpty = false;
            }
        }
    }

    public void onFocusChange(View v, boolean hasFocus) {
        String s = ((EditText)v).getText().toString();
        new MyLog(m_context, "EditText "
            + s + (hasFocus ? "gains focus" : "loses focus"));
        if (!hasFocus)
        {
            fullScroll(View.FOCUS_LEFT);
        }
    }

    public OrItem(Context context) {
        super(context);
        m_context = context;
        m_prefix = null;
        m_layout = new LinearLayout(m_context);
        m_nameSelector = new Spinner(m_context);
        m_contSelector = new Spinner(m_context);
        m_matchString = new EditText(m_context);
        m_matchString.setMaxLines(1);
        m_matchString.setInputType(TYPE_CLASS_TEXT);
        m_matchString.setHorizontallyScrolling(false);
        m_wasEmpty = true;
    }

    // prefix: 0-> nothing, 1-> AND, 2-> OR indented
    private void updatePrefix(int prefix) {
        new MyLog(m_context, "OrItem.updatePrefix(" + String.valueOf(prefix) + ")");
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        float scale = getResources().getDisplayMetrics().density;
        if (prefix == 0) {
            setPadding((int)(scale * 25.0), 0, 0, 0);
            if (m_prefix != null)
            {
                m_layout.removeView(m_prefix);
                m_prefix = null;
            }
        }
        else
        {
            if (m_prefix == null) {
                m_prefix = new TextView(m_context);
                m_layout.addView(m_prefix, ww);
            }
            if (prefix == 1)
            {
                setPadding((int)(scale * 25.0), 0, 0, 0);
                m_prefix.setText(R.string.and);
            }
            else // prefix must be 2
            {
                setPadding((int)(scale * 50.0), 0, 0, 0);
                m_prefix.setText(R.string.or);
            }
        }
    }

    private void updateHelp() {
        View.OnLongClickListener listener
            = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean cont = m_contSelector.getSelectedItemPosition() == 0;
                switch (m_nameSelector.getSelectedItemPosition()) {
                    case 0:
                        if (cont)
                        {
                            Toast.makeText(m_context, R.string.eventnameconthelp,
                                Toast.LENGTH_LONG).show();
                        }
                        else
                        {
                            Toast.makeText(m_context, R.string.eventnamenothelp,
                                Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 1:
                        if (cont) {
                            Toast.makeText(m_context, R.string.locationconthelp,
                                Toast.LENGTH_LONG).show();
                        }
                        else
                        {
                            Toast.makeText(m_context, R.string.locationnothelp,
                                Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 2:
                        if (cont) {
                            Toast.makeText(m_context, R.string.descriptionconthelp,
                                Toast.LENGTH_LONG).show();
                        }
                        else
                        {
                            Toast.makeText(m_context, R.string.descriptionnothelp,
                                Toast.LENGTH_LONG).show();
                        }
                        break;
                }
                return true;
            }
        };
        setLongClickable(true);
        setOnLongClickListener(listener);
        m_layout.setOnLongClickListener(listener);
        m_prefix.setOnLongClickListener(listener);
        m_nameSelector.setLongClickable(true);
        m_nameSelector.setOnLongClickListener(listener);
        m_contSelector.setLongClickable(true);
        m_contSelector.setOnLongClickListener(listener);
    }

    // prefix: 0-> nothing, 1-> AND, 2-> OR indented
    public boolean setup(
        OrList owner, int classNum, int andIndex, int orIndex, int prefix) {
        new MyLog(m_context,
            "OrItem.setup(owner, " + String.valueOf(classNum)
            + ", " + String.valueOf(andIndex)
            + ", " + String.valueOf(orIndex)
            + ", " + String.valueOf(prefix) + ")");
        addView(m_layout);
        m_owner = owner;
        m_classNum = classNum;
        m_andIndex = andIndex;
        m_orIndex = orIndex;
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        m_layout.setOrientation(LinearLayout.HORIZONTAL);
        updatePrefix(prefix);
        ArrayAdapter<?> ad = ArrayAdapter.createFromResource(
            m_context, R.array.nametypes, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        m_nameSelector.setAdapter(ad);
        m_layout.addView(m_nameSelector, ww);
        ad = ArrayAdapter.createFromResource
            (m_context, R.array.containsornot, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        m_contSelector.setAdapter(ad);
        m_layout.addView(m_contSelector, ww);
        String[] sa = PrefsManager.getEventComparison(
        m_context, m_classNum, m_andIndex, m_orIndex);
        new MyLog(m_context, "getEventComparison => \"" + sa[0]
            + "\" \"" + sa[1] + "\" \"" + sa[2] + "\"");
        m_nameSelector.setSelection(Integer.decode(sa[0]));
        m_contSelector.setSelection(Integer.decode(sa[1]));
        m_matchString.setText(sa[2]);
        m_wasEmpty = sa[2].isEmpty();
        m_matchString.addTextChangedListener(this);
        OnFocusChangeListener old = m_matchString.getOnFocusChangeListener();
        m_matchString.setOnFocusChangeListener(this);
        m_layout.addView(m_matchString, ww);
        updateHelp();
        return m_wasEmpty;
    }

    public void updatePreferences() {
        new MyLog(m_context, "setEventComparison(context, "
            + String.valueOf(m_classNum)
            + ", " + String.valueOf(m_andIndex)
            + ", " + String.valueOf(m_orIndex)
            + ", " + String.valueOf(m_nameSelector.getSelectedItemPosition())
            + ", " + String.valueOf(m_contSelector.getSelectedItemPosition())
            + ", \"" + m_matchString.getText().toString() + "\")");
        PrefsManager.setEventComparison(
            m_context, m_classNum, m_andIndex, m_orIndex,
            m_nameSelector.getSelectedItemPosition(),
            m_contSelector.getSelectedItemPosition(),
            m_matchString.getText().toString());
    }

    public void decrementAndIndex(int prefix) {
        new MyLog(m_context, "OrItem.decrementAndIndex(" + String.valueOf(prefix) + ")");
        --m_andIndex;
        updatePreferences();
        updatePrefix(prefix);
    }

    public void decrementOrIndex(int prefix) {
        new MyLog(m_context, "OrItem.decrementOrIndex(" + String.valueOf(prefix) + ")");
        --m_orIndex;
        updatePreferences();
        updatePrefix(prefix);
        updateHelp();
    }
}
