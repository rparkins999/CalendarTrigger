/*
 * Copyright (c) 2021. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

/**
 * Created by rparkins on 20/10/21.
 * This is a special TextView which can display a file name or a message,
 * and also allows long clicks when disabled.
 * Clicking on it when enabled will call its parent activity's getFile(),
 * which brings up a file browser.
 * Clicking on it when disabled does nothing.
 * Long clicking on it displays some appropriate help depending on whether
 * it is enabled, and if so whether it currently holds a file name.
 */
public class SoundFileLabel extends TextView {
    private final Context ac;
    private boolean enabled = false;
    private boolean hasFile;

    public SoundFileLabel(Context context) {
        super(context);
        ac = context;
        setLongClickable(true);
    }

    public void enable(boolean enableIt) {
        enabled = enableIt;
        setTextColor(enableIt ? 0xFF000000 : 0x80000000);
    }

    public void setFile(String s) {
        if (s.isEmpty()) {
            hasFile = false;
            String browse = "<i>" +
                htmlEncode(ac.getString(R.string.browsenofile)) +
                "</i>";
            setText(fromHtml(browse));
        } else {
            hasFile = true;
            setText(s);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean performClick() {
        if (enabled) {
            ((ActionActivity)ac).getFile();
        }
        return true;
    }

    @Override
    public boolean performLongClick() {
        Toast.makeText(ac,
            enabled ? (hasFile ? R.string.browsefileHelp : R.string.browsenofileHelp)
                    : R.string.noPlaySoundHelp,
            Toast.LENGTH_LONG).show();
        return true;
    }
}
