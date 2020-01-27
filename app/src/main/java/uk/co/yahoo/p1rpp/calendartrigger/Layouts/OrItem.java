package uk.co.yahoo.p1rpp.calendartrigger.Layouts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.ClickableSpinner;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;

import static android.text.Html.fromHtml;
import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.TextUtils.htmlEncode;
import static android.view.MotionEvent.ACTION_DOWN;

public class OrItem extends HorizontalScrollView
    implements TextWatcher, OnFocusChangeListener, OnTouchListener {

    boolean m_wasEmpty;
    OrList m_owner;
    boolean m_firstDraw;
    TextView m_prefix;
    LinearLayout m_layout;
    Context m_context;
    ClickableSpinner m_nameSelector;
    String m_className;
    ClickableSpinner m_contSelector;
    EditText m_matchString;
    int m_classNum;
    int m_andIndex;
    int m_orIndex;

    public void beforeTextChanged(CharSequence cs, int start, int count, int after) {}
    public void onTextChanged(CharSequence cs, int start, int count, int after) {}

    public void afterTextChanged(Editable e) {
        if (e.toString().isEmpty())
        {
            if (!m_wasEmpty)
            {
                m_owner.setChildEmpty(m_orIndex, true);
                m_wasEmpty = true;
            }
        }
        else
        {
            if (m_wasEmpty)
            {
                m_owner.setChildEmpty(m_orIndex, false);
                m_wasEmpty = false;
            }
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus)
        {
            scrollTo(0, 0);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if ((event.getActionMasked() == ACTION_DOWN) && !v.hasFocus()) {
            v.requestFocus();
        }
        return false;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (m_firstDraw)
        {
            m_firstDraw = false;
            updateHelp();
        }
    }

    public OrItem(Context context) {
        super(context);
        m_wasEmpty = true;
        m_firstDraw = true;
        m_prefix = null;
        m_layout = new LinearLayout(context);
        m_context = context;
        m_nameSelector = new ClickableSpinner(context);
        m_contSelector = new ClickableSpinner(context);
        m_matchString = new EditText(context);
        m_matchString.setMaxLines(1);
        m_matchString.setInputType(TYPE_CLASS_TEXT);
        m_matchString.setHorizontallyScrolling(true);
    }

    // prefix: 0-> nothing, 1-> AND, 2-> OR indented
    private void updatePrefix(int prefix) {
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
                ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
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

    // Don't moan about touchListener not calling performClick since it alwaya
    // passes the event back to the View by returning false.
    @SuppressLint("ClickableViewAccessibility")
    private void updateHelp() {
        View.OnLongClickListener longClickListener
            = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean cont = m_contSelector.getSelectedItemPosition() == 0;
                switch (m_nameSelector.getSelectedItemPosition()) {
                    case 0:
                        if (cont) {
                            Toast.makeText(m_context,
                                fromHtml(m_context.getString(R.string.nameconthelp,
                                    m_className)), Toast.LENGTH_LONG).show();
                        }
                        else
                        {
                            Toast.makeText(m_context,
                                fromHtml(m_context.getString(R.string.namenothelp,
                                    m_className)), Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 1:
                        if (cont) {
                            Toast.makeText(m_context,
                                fromHtml(m_context.getString(R.string.locconthelp,
                                    m_className)), Toast.LENGTH_LONG).show();
                        }
                        else
                        {
                            Toast.makeText(m_context,
                                fromHtml(m_context.getString(R.string.locnothelp,
                                    m_className)), Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 2:
                        if (cont) {
                            Toast.makeText(m_context,
                                fromHtml(m_context.getString(R.string.descconthelp,
                                    m_className)), Toast.LENGTH_LONG).show();
                        }
                        else
                        {
                            Toast.makeText(m_context,
                                fromHtml(m_context.getString(R.string.descnothelp,
                                    m_className)), Toast.LENGTH_LONG).show();
                        }
                        break;
                }
                return true;
            }
        };
        View.OnLongClickListener typelongClickListener
            = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (m_contSelector.getSelectedItemPosition() == 0)
                {
                    Toast.makeText(m_context,
                        fromHtml(m_context.getString(R.string.eventconthelp,
                            m_className)), Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(m_context,
                        fromHtml(m_context.getString(R.string.eventnotconthelp,
                            m_className)), Toast.LENGTH_LONG).show();
                }
                return true;
            }
        };
        View.OnLongClickListener contlongClickListener
            = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                switch (m_nameSelector.getSelectedItemPosition()) {
                    case 0:
                        Toast.makeText(m_context,
                            fromHtml(m_context.getString(R.string.eventnamehelp,
                                m_className)), Toast.LENGTH_LONG).show();
                        break;
                    case 1:
                        Toast.makeText(m_context,
                            fromHtml(m_context.getString(R.string.eventlocationhelp,
                                m_className)), Toast.LENGTH_LONG).show();
                        break;
                    case 2:
                        Toast.makeText(m_context,
                            fromHtml(m_context.getString(R.string.eventdescriptionhelp,
                                m_className)), Toast.LENGTH_LONG).show();
                        break;
                }
                return true;
            }
        };
        setLongClickable(true);
        setOnLongClickListener(longClickListener);
        m_prefix.setOnLongClickListener(longClickListener);
        m_prefix.setOnTouchListener(this);
        m_nameSelector.setup(typelongClickListener, this);
        m_contSelector.setup(contlongClickListener, this);
    }

    // prefix: 0-> nothing, 1-> AND, 2-> OR indented
    public boolean setup(
        OrList owner, int classNum, int andIndex, int orIndex, int prefix) {
        ViewGroup.LayoutParams vv = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        addView(m_layout, vv);
        m_owner = owner;
        m_classNum = classNum;
        m_className =
            "<i>" + htmlEncode(PrefsManager.getClassName(m_context, m_classNum)) + "</i>";
        m_andIndex = andIndex;
        m_orIndex = orIndex;
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        AdapterView.OnItemSelectedListener listener
            = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                AdapterView<?> parent, View view, int position, long id) {
                updateHelp();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        m_layout.setOrientation(LinearLayout.HORIZONTAL);
        updatePrefix(prefix);
        String[] sa = PrefsManager.getEventComparison(
            m_context, m_classNum, m_andIndex, m_orIndex);
        ArrayAdapter<?> ad = ArrayAdapter.createFromResource(
            m_context, R.array.nametypes, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        m_nameSelector.setAdapter(ad);
        m_nameSelector.setSelection(Integer.decode(sa[0]));
        m_nameSelector.setOnItemSelectedListener(listener);
        m_layout.addView(m_nameSelector, ww);
        ad = ArrayAdapter.createFromResource
            (m_context, R.array.containsornot, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        m_contSelector.setAdapter(ad);
        m_contSelector.setSelection(Integer.decode(sa[1]));
        m_contSelector.setOnItemSelectedListener(listener);
        m_layout.addView(m_contSelector, ww);
        m_matchString.setText(sa[2]);
        m_wasEmpty = sa[2].isEmpty();
        m_matchString.addTextChangedListener(this);
        m_matchString.setOnFocusChangeListener(this);
        m_layout.addView(m_matchString, ww);
        updateHelp();
        return m_wasEmpty;
    }

    public void updatePreferences() {
        PrefsManager.setEventComparison(
            m_context, m_classNum, m_andIndex, m_orIndex,
            m_nameSelector.getSelectedItemPosition(),
            m_contSelector.getSelectedItemPosition(),
            m_matchString.getText().toString());
    }

    public void decrementAndIndex(int prefix) {
        --m_andIndex;
        updatePreferences();
        updatePrefix(prefix);
    }

    public void decrementOrIndex(int prefix) {
        --m_orIndex;
        updatePreferences();
        updatePrefix(prefix);
        updateHelp();
    }
}
