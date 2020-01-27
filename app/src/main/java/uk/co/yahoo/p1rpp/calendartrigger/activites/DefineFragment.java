package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.ClickableSpinner;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;

import static android.view.MotionEvent.ACTION_DOWN;

// Common code for DefineStartFragment and DefineSTopFragment
public class DefineFragment extends Fragment
    implements View.OnTouchListener {

    protected String className;
    protected String italicClassName;
    protected float scale;
    protected EditActivity owner;
    protected EditText minutesEditor;
    protected ClickableSpinner beforeAfter;
    protected CheckBox faceUp;
    protected CheckBox faceDown;
    protected CheckBox anyPosition;
    protected CheckBox wirelessCharger;
    protected CheckBox fastCharger;
    protected CheckBox slowchcarger;
    protected CheckBox peripheral;
    protected CheckBox nothing;
    private boolean isBefore;

    // Don't moan about touchListener not calling performClick since it alwaya
    // passes the event back to the View by returning false.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if ((event.getActionMasked() == ACTION_DOWN) && !v.hasFocus()) {
            v.requestFocus();
        }
        return false;
    }
    public DefineFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView =
            inflater.inflate(
                R.layout.dynamicscrollview, container, false);
        scale = getResources().getDisplayMetrics().density;
        return rootView;
    }

    protected void setupStateUi(
        LinearLayout ll, ViewGroup.LayoutParams ww, boolean before) {
        isBefore = before;
        int classNum = PrefsManager.getClassNum(owner, className);
        int orientations = before ?
            PrefsManager.getBeforeOrientation(owner, classNum) :
            PrefsManager.getAfterOrientation(owner, classNum);
        LinearLayout lll = new LinearLayout(owner);
        lll.setOrientation(LinearLayout.VERTICAL);
        lll.setPadding((int)(scale * 50.0), 0, 0, 0);
        faceUp = new CheckBox(owner);
        faceUp.setText(R.string.devicefaceuplabel);
        faceUp.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.devicefaceuphelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        faceUp.setChecked((orientations & PrefsManager.BEFORE_FACE_UP) != 0);
        lll.addView(faceUp, ww);
        faceDown = new CheckBox(owner);
        faceDown.setText(R.string.devicefacedownlabel);
        faceDown.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.devicefacedownhelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        faceDown.setChecked(
            (orientations & PrefsManager.BEFORE_FACE_DOWN) !=0);
        lll.addView(faceDown, ww);
        anyPosition = new CheckBox(owner);
        anyPosition.setText(R.string.deviceanypositionlabel);
        anyPosition.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.deviceanypositionhelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        anyPosition.setChecked(
            (orientations & PrefsManager.BEFORE_OTHER_POSITION) !=0);
        lll.addView(anyPosition, ww);
        ll.addView(lll, ww);
        TextView tv = new TextView(owner);
        tv.setPadding((int)(scale * 25.0), 0, 0, 0);
        tv.setText(R.string.deviceUSBlabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.devicestartUSBhelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        int connections = before ?
            PrefsManager.getBeforeConnection(owner, classNum) :
            PrefsManager.getAfterConnection(owner, classNum);
        lll = new LinearLayout(owner);
        lll.setOrientation(LinearLayout.VERTICAL);
        lll.setPadding((int)(scale * 50.0), 0, 0, 0);
        wirelessCharger = new CheckBox(owner);
        wirelessCharger.setText(R.string.wirelesschargerlabel);

        wirelessCharger.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.wirelesschargerhelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        wirelessCharger.setChecked(
            (connections & PrefsManager.BEFORE_WIRELESS_CHARGER) != 0);
        lll.addView(wirelessCharger, ww);
        fastCharger = new CheckBox(owner);
        fastCharger.setText(R.string.fastchargerlabel);

        fastCharger.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.fastchargerhelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        fastCharger.setChecked(
            (connections & PrefsManager.BEFORE_FAST_CHARGER) != 0);
        lll.addView(fastCharger, ww);
        slowchcarger = new CheckBox(owner);
        slowchcarger.setText(R.string.plainchargerlabel);

        slowchcarger.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.plainchargerhelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        slowchcarger.setChecked(
            (connections & PrefsManager.BEFORE_PLAIN_CHARGER) != 0);
        lll.addView(slowchcarger, ww);
        peripheral = new CheckBox(owner);
        peripheral = new CheckBox(owner);
        peripheral.setText(R.string.usbotglabel);

        peripheral.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.usbotghelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        peripheral.setChecked(
            (connections & PrefsManager.BEFORE_PERIPHERAL) != 0);
        lll.addView(peripheral, ww);
        nothing = new CheckBox(owner);
        nothing.setText(R.string.usbnothinglabel);

        nothing.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.usbnothinghelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        nothing.setChecked(
            (connections & PrefsManager.BEFORE_UNCONNECTED) != 0);
        lll.addView(nothing, ww);
        ll.addView(lll, ww);
    }

    public void onPause() {
        super.onPause();
        int classNum = PrefsManager.getClassNum(owner, className);
        int orientations = 0;
        if (faceUp.isChecked())
        {
            orientations |= PrefsManager.BEFORE_FACE_UP;
        }
        if (faceDown.isChecked())
        {
            orientations |= PrefsManager.BEFORE_FACE_DOWN;
        }
        if (anyPosition.isChecked())
        {
            orientations |= PrefsManager.BEFORE_OTHER_POSITION;
        }
        if (isBefore) {
            PrefsManager.setBeforeOrientation(owner, classNum, orientations);
        }
        else
        {
            PrefsManager.setAfterOrientation(owner, classNum, orientations);
        }
        int connections = 0;
        if (wirelessCharger.isChecked())
        {
            connections |= PrefsManager.BEFORE_WIRELESS_CHARGER;
        }
        if (fastCharger.isChecked())
        {
            connections |= PrefsManager.BEFORE_FAST_CHARGER;
        }
        if (slowchcarger.isChecked())
        {
            connections |= PrefsManager.BEFORE_PLAIN_CHARGER;
        }
        if (peripheral.isChecked())
        {
            connections |= PrefsManager.BEFORE_PERIPHERAL;
        }
        if (nothing.isChecked())
        {
            connections |= PrefsManager.BEFORE_UNCONNECTED;
        }
        if (isBefore) {
            PrefsManager.setBeforeConnection(owner, classNum, connections);
        }
        else
        {
            PrefsManager.setAfterConnection(owner, classNum, connections);
        }
        owner.setButtonVisibility(View.VISIBLE);
    }
}
