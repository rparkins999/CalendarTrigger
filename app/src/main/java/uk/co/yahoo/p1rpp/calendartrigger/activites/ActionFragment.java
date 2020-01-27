/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.support.v4.content.PermissionChecker;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import uk.co.yahoo.p1rpp.calendartrigger.Widgets.ClickableSpinner;
import uk.co.yahoo.p1rpp.calendartrigger.contacts.ContactCreator;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.DisabledCheckBox;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.DisabledRadioButton;

import static android.view.MotionEvent.ACTION_DOWN;

/**
 * Created by rparkins on 25/12/17.
 *
 * This contains a bit of logic common to
 * ActionStartFragment and ActionStopFragment
 */

public class ActionFragment extends Fragment
    implements View.OnFocusChangeListener, TextWatcher, View.OnTouchListener {

    private final Intent intentm
        = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:"));
    private final Intent intents
        = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:"));

    protected float scale;
    protected CheckBox showNotification;
    protected CheckBox playSound;
    protected TextView soundFilename;
    protected Boolean hasFileName;
    protected Boolean gettingFile;
    protected DisabledCheckBox sendMessage;
    protected RadioButton sendEmail;
    protected RadioButton sendEmailOrText;
    protected RadioButton sendText;
    protected RadioButton sendTextOrEmail;
    protected DisabledCheckBox sendToEmail;
    protected EditText emailAddress;
    protected DisabledCheckBox sendToNumber;
    protected EditText smsNumber;
    protected DisabledCheckBox sendToContact;
    protected EditText contactName;
    protected DisabledCheckBox sendToContactFromEventName;
    private TextView firstLabel;
    protected EditText firstWordNum;
    private TextView firstFrom;
    protected ClickableSpinner firstWordStartEnd;
    private TextView lastLabel;
    protected EditText lastWordNum;
    private TextView lastFrom;
    protected ClickableSpinner lastWordStartEnd;
    protected DisabledCheckBox removePunctuation;
    protected DisabledRadioButton smsTextClass;
    protected DisabledRadioButton smsTextName;
    protected DisabledRadioButton smsTextDescription;
    protected DisabledRadioButton smsTextLiteral;
    protected EditText messageText;
    protected DisabledRadioButton subjectClass;
    protected DisabledRadioButton subjectName;
    protected DisabledRadioButton subjectDescription;
    protected DisabledRadioButton subjectLiteral;
    protected EditText subjectText;
    private boolean haveContactsPermission;

    public ActionFragment() {
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus)
        {
            v.scrollTo(0, 0);
        }
    }

    /* This TextWatcher interface is only used for contact name, to check validity.
     * Other TextWatchers just select the option when text is entered
     */
    @Override
    public void beforeTextChanged(
        CharSequence s, int start, int count, int after) {
        // nothing
    }

    @Override
    public void onTextChanged(
        CharSequence s, int start, int before, int count) {
        // nothing
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if ((event.getActionMasked() == ACTION_DOWN) && !v.hasFocus()) {
            v.requestFocus();
        }
        return false;
    }

    public void enableSendToContact(boolean enable) {
        if (enable) {
            haveContactsPermission =
                (PackageManager.PERMISSION_GRANTED ==
                    PermissionChecker.checkSelfPermission(
                        getActivity(), Manifest.permission.READ_CONTACTS));
            if (haveContactsPermission) {
                sendToContact.setEnabled(true);
                return;
            }
        }
        sendToContact.setEnabled(false);
    }

    public void enableSendToContactFromEventName(boolean enable) {
        if (enable) {
            haveContactsPermission =
                (PackageManager.PERMISSION_GRANTED ==
                    PermissionChecker.checkSelfPermission(
                        getActivity(), Manifest.permission.READ_CONTACTS));
            if (haveContactsPermission) {
                sendToContactFromEventName.setEnabled(true);
                return;
            }
        }
        sendToContactFromEventName.setEnabled(false);
    }

    public void enableContactName(boolean enable) {
        if (enable) {
            haveContactsPermission =
                (PackageManager.PERMISSION_GRANTED ==
                    PermissionChecker.checkSelfPermission(
                        getActivity(), Manifest.permission.READ_CONTACTS));
            if (haveContactsPermission) {
                contactName.setEnabled(true);
                return;
            }
        }
        contactName.setEnabled(false);
    }

    private void fixupContactName(String s) {
        String[] sa = ContactCreator.getMessaging(getActivity(), s);
        if (   (sa[0] != null)
            && (   sendText.isChecked()
            || sendEmailOrText.isChecked()
            || sendTextOrEmail.isChecked())) {
            contactName.setTextColor(0xFF00FF00);
        }
        else if (   (sa[1] != null)
            && (   sendEmail.isChecked()
            || sendEmailOrText.isChecked()
            || sendTextOrEmail.isChecked())) {
            contactName.setTextColor(0xFF00FF00);
        }
        else
        {
            contactName.setTextColor(0xFFFF0000);
        }
    }

    @Override
    public void afterTextChanged(Editable e) {
        sendToEmail.setChecked(false);
        sendToNumber.setChecked(false);
        sendToContact.setChecked(true);
        sendToContactFromEventName.setChecked(false);
        String s = e.toString();
        fixupContactName(s);
    }

    public String getDefaultDir() {
        return PrefsManager.getDefaultDir(getActivity());
    }

    public void getFile() {
        gettingFile = true;
        FileBrowserFragment fb = new FileBrowserFragment();
        fb.setOwner(this);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.edit_activity_container, fb, "fb")
          .addToBackStack(null)
          .commit();
    }

    public void openThis(File file) {}

    private void adjustSendType() {
        if (   sendToEmail.isChecked()
            || sendToNumber.isChecked()
            || sendToContact.isChecked()
            || sendToContactFromEventName.isChecked()) {
            smsTextClass.setEnabled(true);
            smsTextName.setEnabled(true);
            smsTextDescription.setEnabled(true);
            smsTextLiteral.setEnabled(true);
            messageText.setEnabled(true);
        }
        else
        {
            smsTextClass.setEnabled(false);
            smsTextName.setEnabled(false);
            smsTextDescription.setEnabled(false);
            smsTextLiteral.setEnabled(false);
            messageText.setEnabled(false);
        }
        if (   sendToEmail.isChecked()
            || sendToContact.isChecked()
            || sendToContactFromEventName.isChecked()) {
            subjectClass.setEnabled(true);
            subjectName.setEnabled(true);
            subjectDescription.setEnabled(true);
            subjectLiteral.setEnabled(true);
            subjectText.setEnabled(true);
        }
        else
        {
            subjectClass.setEnabled(false);
            subjectName.setEnabled(false);
            subjectDescription.setEnabled(false);
            subjectLiteral.setEnabled(false);
            subjectText.setEnabled(false);
        }
    }

    private void setSendMessage() {
        sendMessage.setChecked(true);
        adjustSendType();
    }

    private void clearSendToEmail() {
        sendToEmail.setChecked(false);
        adjustSendType();
    }

    private void clearSendToNumber() {
        sendToNumber.setChecked(false);
        adjustSendType();
    }

    private void clearSendToContact() {
        sendToContact.setChecked(false);
        adjustSendType();
    }

    private void clearSendToContactFromEventName() {
        sendToContactFromEventName.setChecked(false);
        firstLabel.setEnabled(false);
        firstWordNum.setEnabled(false);
        firstFrom.setEnabled(false);
        firstWordStartEnd.setEnabled(false);
        lastLabel.setEnabled(false);
        lastWordNum.setEnabled(false);
        lastFrom.setEnabled(false);
        lastWordStartEnd.setEnabled(false);
        removePunctuation.setEnabled(false);
        adjustSendType();
    }

    private void setSendToEmail() {
        sendToEmail.setChecked(true);
        emailAddress.setEnabled(true);
        clearSendToContact();
        clearSendToContactFromEventName();
        adjustSendType();
    }

    private void setSendToNumber() {
        sendToNumber.setChecked(true);
        smsNumber.setEnabled(true);
        clearSendToContact();
        clearSendToContactFromEventName();
        adjustSendType();
    }

    private void setSendToContact() {
        sendToContact.setChecked(true);
        contactName.setEnabled(true);
        clearSendToEmail();
        clearSendToNumber();
        clearSendToContactFromEventName();
        adjustSendType();
    }

    private void setSendToContactFromEventName() {
        sendToContactFromEventName.setChecked(true);
        contactName.setEnabled(true);
        firstLabel.setEnabled(true);
        firstWordNum.setEnabled(true);
        firstFrom.setEnabled(true);
        firstWordStartEnd.setEnabled(true);
        lastLabel.setEnabled(true);
        lastWordNum.setEnabled(true);
        lastFrom.setEnabled(true);
        lastWordStartEnd.setEnabled(true);
        removePunctuation.setEnabled(true);
        clearSendToEmail();
        clearSendToNumber();
        clearSendToContact();
        adjustSendType();
    }

    private void setSendEmail() {
        sendMessage.setChecked(true);
        sendEmail.setChecked(true);
        sendEmailOrText.setChecked(false);
        sendText.setChecked(false);
        sendTextOrEmail.setChecked(false);
        sendToEmail.setEnabled(true);
        emailAddress.setEnabled(true);
        sendToNumber.setEnabled(false);
        enableSendToContact(true);
        enableContactName(true);
        enableSendToContactFromEventName(true);
    }

    private void setSendEmailOrText() {
        sendMessage.setChecked(true);
        sendEmail.setChecked(false);
        sendEmailOrText.setChecked(true);
        sendText.setChecked(false);
        sendTextOrEmail.setChecked(false);
        sendToEmail.setEnabled(true);
        emailAddress.setEnabled(true);
        sendToNumber.setEnabled(true);
        smsNumber.setEnabled(true);
        enableSendToContact(true);
        enableContactName(true);
        enableSendToContactFromEventName(true);
    }

    private void setSendText() {
        sendMessage.setChecked(true);
        sendEmail.setChecked(false);
        sendEmailOrText.setChecked(false);
        sendText.setChecked(true);
        sendTextOrEmail.setChecked(false);
        sendToEmail.setChecked(false);
        sendToEmail.setEnabled(false);
        emailAddress.setEnabled(false);
        sendToNumber.setEnabled(true);
        smsNumber.setEnabled(true);
        enableSendToContact(true);
        enableContactName(true);
        enableSendToContactFromEventName(true);
    }

    private void setSendTextOrEmail() {
        setSendMessage();
        sendEmail.setChecked(false);
        sendEmailOrText.setChecked(false);
        sendText.setChecked(false);
        sendTextOrEmail.setChecked(true);
        sendToEmail.setEnabled(true);
        emailAddress.setEnabled(true);
        sendToNumber.setEnabled(true);
        smsNumber.setEnabled(true);
        enableSendToContact(true);
        enableContactName(true);
        enableSendToContactFromEventName(true);
    }

    private void clearSendMessage() {
        sendMessage.setChecked(false);
        sendEmail.setChecked(false);
        sendEmailOrText.setChecked(false);
        sendText.setChecked(false);
        sendTextOrEmail.setChecked(false);
        sendToEmail.setChecked(false);
        sendToEmail.setEnabled(false);
        emailAddress.setEnabled(false);
        sendToNumber.setChecked(false);
        sendToNumber.setEnabled(false);
        smsNumber.setEnabled(false);
        sendToContact.setChecked(false);
        enableSendToContact(false);
        enableContactName(false);
        sendToContactFromEventName.setChecked(false);
        enableSendToContactFromEventName(false);
        firstLabel.setEnabled(false);
        firstWordNum.setEnabled(false);
        firstFrom.setEnabled(false);
        firstWordStartEnd.setEnabled(false);
        lastLabel.setEnabled(false);
        lastWordNum.setEnabled(false);
        lastFrom.setEnabled(false);
        lastWordStartEnd.setEnabled(false);
        removePunctuation.setEnabled(false);
        smsTextClass.setEnabled(false);
        smsTextName.setEnabled(false);
        smsTextDescription.setEnabled(false);
        smsTextLiteral.setEnabled(false);
        messageText.setEnabled(false);
        subjectClass.setEnabled(false);
        subjectName.setEnabled(false);
        subjectDescription.setEnabled(false);
        subjectLiteral.setEnabled(false);
        subjectText.setEnabled(false);
    }

    /* Common UI setup for sending message at start or end of event
     */
    public void layoutSendMessage(
        LinearLayout ll, ViewGroup.LayoutParams ww, int classNum, int startOrEnd) {
        final Activity ac = getActivity();
        boolean canEmail = intentm.resolveActivity(ac.getPackageManager()) != null;
        boolean canSms = intents.resolveActivity(ac.getPackageManager()) != null;
        InputFilter[] digits = {
            new android.text.method.DigitsKeyListener(false, false)
        };
        Configuration config = getResources().getConfiguration();
        sendMessage = new DisabledCheckBox(ac);
        sendMessage.setEnabled(canEmail || canSms);
        sendEmail = new DisabledRadioButton(ac);
        sendEmail.setEnabled(canEmail);
        sendEmailOrText = new DisabledRadioButton(ac);
        sendEmailOrText.setEnabled(canEmail && canSms);
        sendText = new DisabledRadioButton(ac);
        sendText.setEnabled(canSms);
        sendTextOrEmail = new DisabledRadioButton(ac);
        sendTextOrEmail.setEnabled(canEmail && canSms);
        sendToEmail = new DisabledCheckBox(ac);
        emailAddress = new EditText(ac);
        sendToNumber = new DisabledCheckBox(ac);
        smsNumber = new EditText(ac);
        sendToContact = new DisabledCheckBox(ac);
        contactName = new EditText(ac);
        sendToContactFromEventName = new DisabledCheckBox(ac);
        firstLabel = new TextView(ac);
        firstWordNum = new EditText(ac);
        firstFrom = new TextView(ac);
        firstWordStartEnd = new ClickableSpinner(ac);
        lastLabel = new TextView(ac);
        lastWordNum = new EditText(ac);
        lastFrom = new TextView(ac);
        lastWordStartEnd = new ClickableSpinner(ac);
        removePunctuation = new DisabledCheckBox(ac);
        smsTextClass = new DisabledRadioButton(ac);
        smsTextName = new DisabledRadioButton(ac);
        smsTextDescription = new DisabledRadioButton(ac);
        smsTextLiteral = new DisabledRadioButton(ac);
        messageText = new EditText(ac);
        subjectClass = new DisabledRadioButton(ac);
        subjectName = new DisabledRadioButton(ac);
        subjectDescription = new DisabledRadioButton(ac);
        subjectLiteral = new DisabledRadioButton(ac);
        subjectText = new EditText(ac);

        int n = PrefsManager.getMessageType(ac, classNum, startOrEnd);
        sendMessage.setText(getString(R.string.sendmessage));
        if (canEmail || canSms) {
            sendMessage.setEnabled(true);
            if (n != PrefsManager.SEND_NO_MESSAGE) {
                setSendMessage();
            }
            else
            {
                clearSendMessage();
            }
            sendMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (sendMessage.isChecked()) {
                        setSendMessage();
                    }
                    else
                    {
                        clearSendMessage();
                    }
                }
            });
            sendMessage.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.sendmessagehelp),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        else
        {
            clearSendMessage();
            sendMessage.setEnabled(false);
            sendMessage.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.sendmessagedisabled),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        ll.addView(sendMessage, ww);
        LinearLayout lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.VERTICAL);
        lll.setPadding((int) (scale * 40.0), 0, 0, 0);
        if (canEmail) {
            sendEmail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (sendEmail.isChecked()) {
                        if (intentm.resolveActivity(ac.getPackageManager()) != null) {
                            setSendEmail();
                        }
                    }
                    else
                    {
                        clearSendMessage();
                    }
                }
            });
            sendEmail.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.sendemailhelp),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            if (n == PrefsManager.SEND_MESSAGE_EMAIL) {
                setSendEmail();
            }
            else
            {
                sendEmail.setChecked(false);
            }
        }
        else
        {
            sendEmail.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.sendemaildisabled),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            sendEmail.setChecked(false);
            sendEmail.setEnabled(false);
        }
        sendEmail.setText(getString(R.string.byemail));
        lll.addView(sendEmail, -1, ww);
        if (canEmail && canSms) {
            sendEmailOrText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (sendEmailOrText.isChecked()) {
                        PackageManager pm = ac.getPackageManager();
                        if (   (intentm.resolveActivity(pm) != null)
                            || (intents.resolveActivity(pm) != null)) {
                            setSendEmailOrText();
                        }
                    }
                    else
                    {
                        clearSendMessage();
                    }
                }
            });
            sendEmailOrText.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.emailorsmshelp),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            if (n == PrefsManager.SEND_MESSAGE_EMAIL_OR_SMS) {
                setSendEmailOrText();
            }
            else
            {
                sendEmailOrText.setChecked(false);
            }
        }
        else
        {
            sendEmailOrText.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.emailorsmsdisabled),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            sendEmailOrText.setChecked(false);
            sendEmailOrText.setEnabled(false);
        }
        sendEmailOrText.setText(getString(R.string.emailorsms));
        lll.addView(sendEmailOrText, -1, ww);
        if (canSms) {
            sendText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (sendText.isChecked()) {
                        PackageManager pm = ac.getPackageManager();
                        if (   (intentm.resolveActivity(pm) != null)
                            || (intents.resolveActivity(pm) != null)) {
                            setSendText();
                        }
                    }
                    else
                    {
                        clearSendMessage();
                    }
                }
            });
            sendText.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.sendsmshelp),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            if (n == PrefsManager.SEND_MESSAGE_SMS) {
                setSendText();
            }
            else
            {
                sendText.setChecked(false);
            }
        }
        else
        {
            sendText.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.sendsmsdisabled),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            sendText.setChecked(false);
            sendText.setEnabled(false);
        }
        sendText.setText(getString(R.string.sendsms));
        lll.addView(sendText, -1, ww);
        if (canEmail && canSms) {
            sendTextOrEmail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (sendTextOrEmail.isChecked()) {
                        PackageManager pm = ac.getPackageManager();
                        if (   (intentm.resolveActivity(pm) != null)
                            || (intents.resolveActivity(pm) != null)) {
                            setSendTextOrEmail();
                        }
                    }
                    else
                    {
                        clearSendMessage();
                    }
                }
            });
            sendTextOrEmail.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.smsoremailhelp),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            if (n == PrefsManager.SEND_MESSAGE_SMS_OR_EMAIL) {
                setSendTextOrEmail();
            }
            else
            {
                sendTextOrEmail.setChecked(false);
            }
        }
        else
        {
            sendTextOrEmail.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, getString(R.string.smsoremaildisabled),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            sendTextOrEmail.setChecked(false);
            sendTextOrEmail.setEnabled(false);
        }
        sendTextOrEmail.setText(getString(R.string.smsoremail));
        lll.addView(sendTextOrEmail, -1, ww);
        TextView tv = new TextView(ac);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.destinationhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv.setText(getString(R.string.destination));
        lll.addView(tv, -1, ww);
        LinearLayout llll = new LinearLayout(ac);
        llll.setOrientation(LinearLayout.HORIZONTAL);
        if (   sendEmail.isChecked()
            || sendEmailOrText.isChecked()
            || sendTextOrEmail.isChecked()) {
            sendToEmail.setEnabled(true);
            sendToEmail.setChecked(PrefsManager.getMessageAddress(
                ac, classNum, startOrEnd) != null);
            emailAddress.setEnabled(true);
        }
        else
        {
            sendToEmail.setChecked(false);
            sendToEmail.setEnabled(false);
            emailAddress.setEnabled(false);
        }
        sendToEmail.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sendToEmail.isChecked()) {
                    setSendToEmail();
                }
                else
                {
                    clearSendToEmail();
                }
            }
        });
        sendToEmail.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sendToEmail.isEnabled()) {
                    Toast.makeText(ac, getString(R.string.emailaddresshelp),
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ac, getString(R.string.emailaddressdisabled),
                        Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        sendToEmail.setText(getString(R.string.emailaddress));
        llll.addView(sendToEmail, -1, ww);
        emailAddress.setMaxLines(1);
        emailAddress.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailAddress.setEnabled(sendToEmail.isEnabled());
        emailAddress.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.emailaddressfield),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        emailAddress.setText(PrefsManager.getMessageAddress(
            ac, classNum, startOrEnd));
        emailAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                CharSequence s, int start, int count, int after) {
                // nothing
            }

            @Override
            public void onTextChanged(
                CharSequence s, int start, int before, int count) {
                // nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                setSendToEmail();
            }
        });
        emailAddress.setOnFocusChangeListener(this);
        llll.addView(emailAddress, -1, ww);
        lll.addView(llll, -1, ww);
        llll = new LinearLayout(ac);
        llll.setOrientation(LinearLayout.HORIZONTAL);
        if (   sendText.isChecked()
            || sendEmailOrText.isChecked()
            || sendTextOrEmail.isChecked()) {
            sendToNumber.setEnabled(true);
            sendToNumber.setChecked(PrefsManager.getMessageNumber(
                ac, classNum, startOrEnd) != null);
            smsNumber.setEnabled(true);
        }
        else
        {
            sendToNumber.setEnabled(false);
            smsNumber.setEnabled(false);
        }
        sendToNumber.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sendToNumber.isChecked()) {
                    setSendToNumber();
                }
                else
                {
                    clearSendToNumber();
                }
            }
        });
        sendToNumber.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sendToNumber.isEnabled()) {
                    Toast.makeText(ac, getString(R.string.mobilenumberhelp),
                        Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(ac, getString(R.string.mobilenumberdisabled),
                        Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        sendToNumber.setText(getString(R.string.mobilenumber));
        llll.addView(sendToNumber, -1, ww);
        smsNumber.setMaxLines(1);
        smsNumber.setInputType(InputType.TYPE_CLASS_PHONE);
        // FIXME try to reject alphabetic characters
        smsNumber.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.mobilenumberfield),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        smsNumber.setText(PrefsManager.getMessageNumber(
            ac, classNum, startOrEnd));
        smsNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                CharSequence s, int start, int count, int after) {
                // nothing
            }

            @Override
            public void onTextChanged(
                CharSequence s, int start, int before, int count) {
                // nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                setSendToNumber();
            }
        });
        smsNumber.setOnFocusChangeListener(this);
        llll.addView(smsNumber, -1, ww);
        lll.addView(llll, -1, ww);
        llll = new LinearLayout(ac);
        llll.setOrientation(LinearLayout.HORIZONTAL);
        String s = PrefsManager.getMessageContact(
                ac, classNum, startOrEnd);
        if (   sendText.isChecked()
            || sendEmail.isChecked()
            || sendEmailOrText.isChecked()
            || sendTextOrEmail.isChecked()) {
            enableSendToContact(true);
            sendToContact.setChecked(s != null);
            enableContactName(true);
        }
        else
        {
            enableSendToContact(false);
            enableContactName(false);
        }
        sendToContact.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sendToContact.isChecked()) {
                    setSendToContact();
                }
                else
                {
                    clearSendToContact();
                }
            }
        });
        sendToContact.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sendToContact.isEnabled()) {
                    Toast.makeText(ac, getString(R.string.tocontacthelp),
                        Toast.LENGTH_LONG).show();
                }
                else if (haveContactsPermission)
                {
                    Toast.makeText(ac, getString(R.string.tocontactdisabled),
                        Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(ac, getString(R.string.tocontactdenied),
                        Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        sendToContact.setText(getString(R.string.tocontact));
        llll.addView(sendToContact, -1, ww);
        contactName.setMaxLines(1);
        contactName.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        contactName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sendEmailOrText.isChecked() || sendTextOrEmail.isChecked()) {
                    Toast.makeText(ac, getString(R.string.contacthelpboth),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
                else if (sendEmail.isChecked()) {
                    Toast.makeText(ac, getString(R.string.contacthelpemail),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
                else if (sendText.isChecked()) {
                    Toast.makeText(ac, getString(R.string.contacthelpsms),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
                return false;
            }
        });
        contactName.setText(s);
        if (s != null) { fixupContactName(s); }
        contactName.addTextChangedListener(this);
        contactName.setOnFocusChangeListener(this);
        llll.addView(contactName, -1, ww);
        lll.addView(llll, -1, ww);
        if (   sendText.isChecked()
            || sendEmail.isChecked()
            || sendEmailOrText.isChecked()
            || sendTextOrEmail.isChecked()) {
            enableSendToContactFromEventName(true);
            boolean enable = PrefsManager.getMessageExtract(
                ac, classNum, startOrEnd);
            sendToContactFromEventName.setChecked(enable);
            firstLabel.setEnabled(enable);
            firstWordNum.setEnabled(enable);
            firstFrom.setEnabled(enable);
            firstWordStartEnd.setEnabled(enable);
            lastLabel.setEnabled(enable);
            lastWordNum.setEnabled(enable);
            lastFrom.setEnabled(enable);
            lastWordStartEnd.setEnabled(enable);
            removePunctuation.setEnabled(enable);
        }
        else
        {
            enableSendToContactFromEventName(false);
            enableContactName(false);
            firstLabel.setEnabled(false);
            firstWordNum.setEnabled(false);
            firstFrom.setEnabled(false);
            firstWordStartEnd.setEnabled(false);
            lastLabel.setEnabled(false);
            lastWordNum.setEnabled(false);
            lastFrom.setEnabled(false);
            lastWordStartEnd.setEnabled(false);
            removePunctuation.setEnabled(false);
        }
        sendToContactFromEventName.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sendToContactFromEventName.isChecked()) {
                    setSendToContactFromEventName();
                }
                else
                {
                    clearSendToContactFromEventName();
                }
            }
        });
        sendToContactFromEventName.setOnLongClickListener(
            new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sendToContactFromEventName.isEnabled()) {
                    Toast.makeText(ac, getString(R.string.contactfromnamehelp),
                        Toast.LENGTH_LONG).show();
                }
                else if (haveContactsPermission)
                {
                    Toast.makeText(ac, getString(R.string.contactfromnamedisabled),
                        Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(ac, getString(R.string.contactfromnamedenied),
                        Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        sendToContactFromEventName.setText(getString(R.string.contactfromname));
        lll.addView(sendToContactFromEventName, -1, ww);
        llll = new LinearLayout(ac);
        llll.setOrientation(LinearLayout.HORIZONTAL);
        llll.setPadding((int) (scale * 30.0), 0, 0, 0);
        firstLabel.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.getfirsthelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        firstLabel.setText(getString(R.string.firstlabel));
        llll.addView(firstLabel, -1, ww);
        firstWordNum.setText(String.valueOf(PrefsManager.getMessageFirstCount(
            ac, classNum, startOrEnd)));
        firstWordNum.setInputType(InputType.TYPE_NUMBER_VARIATION_NORMAL);
        firstWordNum.setFilters(digits);
        firstWordNum.setSelectAllOnFocus(true);
        firstWordNum.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.firstnumhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        llll.addView(firstWordNum, -1, ww);
        firstFrom.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.firstfromhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        firstFrom.setText(getString(R.string.fromlabel));
        llll.addView(firstFrom, -1, ww);
        ArrayAdapter<?> ad = ArrayAdapter.createFromResource(
            ac, R.array.startorend, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        firstWordStartEnd.setAdapter(ad);
        n = PrefsManager.getMessageFirstDir(ac, classNum, startOrEnd);
        if ((n < 0) || (n >= ad.getCount())) { n = 0; }
        firstWordStartEnd.setSelection(n);
        firstWordStartEnd.setup(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.firstnumhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        }, this);
        llll.addView(firstWordStartEnd);
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            lll.addView(llll, -1, ww);
            llll = new LinearLayout(ac);
            llll.setOrientation(LinearLayout.HORIZONTAL);
            llll.setPadding((int) (scale * 30.0), 0, 0, 0);
        }
        lastLabel.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.getlasthelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        lastLabel.setText(getString(R.string.lastlabel));
        llll.addView(lastLabel, -1, ww);
        lastWordNum.setText(String.valueOf(PrefsManager.getMessageLastCount(
            ac, classNum, startOrEnd)));
        lastWordNum.setInputType(InputType.TYPE_NUMBER_VARIATION_NORMAL);
        lastWordNum.setFilters(digits);
        lastWordNum.setSelectAllOnFocus(true);
        lastWordNum.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.lastnumhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        llll.addView(lastWordNum, -1, ww);
        lastFrom.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.lastfromhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        lastFrom.setText(getString(R.string.fromlabel));
        llll.addView(lastFrom, -1, ww);
        ad = ArrayAdapter.createFromResource(
            ac, R.array.startorend, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lastWordStartEnd.setAdapter(ad);
        n = PrefsManager.getMessageLastDir(ac, classNum, startOrEnd);
        if ((n < 0) || (n >= ad.getCount())) { n = 0; }
        lastWordStartEnd.setSelection(n);
        lastWordStartEnd.setup(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.lastnumhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        }, this);
        llll.addView(lastWordStartEnd);
        lll.addView(llll, -1, ww);
        llll = new LinearLayout(ac);
        llll.setOrientation(LinearLayout.HORIZONTAL);
        llll.setPadding((int) (scale * 60.0), 0, 0, 0);
        if (sendToContactFromEventName.isChecked()) {
            removePunctuation.setEnabled(true);
        }
        else
        {
            removePunctuation.setEnabled(false);
        }
        removePunctuation.setOnLongClickListener(
            new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (removePunctuation.isEnabled()) {
                        Toast.makeText(ac, getString(R.string.removesuffixhelp),
                            Toast.LENGTH_LONG).show();
                    }
                    else
                    {
                        Toast.makeText(ac, getString(R.string.removesuffixdisabled),
                            Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
            });
        removePunctuation.setChecked(
            PrefsManager.getMessageTrim(ac, classNum, startOrEnd));
        removePunctuation.setText(getString(R.string.removesuffixlabel));
        llll.addView(removePunctuation, -1, ww);
        lll.addView(llll, -1, ww);
        tv = new TextView(ac);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.bodyhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv.setText(getString(R.string.bodylabel));
        lll.addView(tv, -1, ww);
        n = PrefsManager.getMessageTextType(ac, classNum, startOrEnd);
        smsTextClass.setChecked(n == PrefsManager.MESSAGE_TEXT_CLASSNAME);
        smsTextClass.setEnabled(sendMessage.isChecked());
        smsTextClass.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (smsTextClass.isChecked()) {
                    smsTextName.setChecked(false);
                    smsTextDescription.setChecked(false);
                    smsTextLiteral.setChecked(false);
                }
            }
        });
        smsTextClass.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.bodyclassnamehelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        smsTextClass.setText(getString(R.string.bodyclassnamelabel));
        lll.addView(smsTextClass, -1, ww);
        smsTextName.setChecked(n == PrefsManager.MESSAGE_TEXT_EVENTNAME);
        smsTextName.setEnabled(sendMessage.isChecked());
        smsTextName.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (smsTextName.isChecked()) {
                    smsTextClass.setChecked(false);
                    smsTextDescription.setChecked(false);
                    smsTextLiteral.setChecked(false);
                }
            }
        });
        smsTextName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.bodyeventnamehelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        smsTextName.setText(getString(R.string.bodyeventnamelabel));
        lll.addView(smsTextName, -1, ww);
        smsTextDescription.setChecked(n == PrefsManager.MESSAGE_TEXT_EVENTDESCRIPTION);
        smsTextDescription.setEnabled(sendMessage.isChecked());
        smsTextDescription.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (smsTextDescription.isChecked()) {
                    smsTextClass.setChecked(false);
                    smsTextName.setChecked(false);
                    smsTextLiteral.setChecked(false);
                }
            }
        });
        smsTextDescription.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.bodyeventdescriptionhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        smsTextDescription.setText(R.string.bodyeventdescriptionlabel);
        lll.addView(smsTextDescription, -1, ww);
        llll = new LinearLayout(ac);
        llll.setOrientation(LinearLayout.HORIZONTAL);
        smsTextLiteral.setChecked(n == PrefsManager.MESSAGE_TEXT_LITERAL);
        smsTextLiteral.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (smsTextLiteral.isChecked())
                {
                    smsTextClass.setChecked(false);
                    smsTextName.setChecked(false);
                    smsTextDescription.setChecked(false);
                }
            }
        });
        smsTextLiteral.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.bodyliteralhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        llll.addView(smsTextLiteral, -1, ww);
        messageText.setEnabled(smsTextLiteral.isEnabled());
        messageText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.bodyliterallabel),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        messageText.setText(PrefsManager.getMessageLiteral(ac, classNum, startOrEnd));
        messageText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                CharSequence s, int start, int count, int after) {
                // nothing
            }

            @Override
            public void onTextChanged(
                CharSequence s, int start, int before, int count) {
                // nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                smsTextClass.setChecked(false);
                smsTextName.setChecked(false);
                smsTextDescription.setChecked(false);
                smsTextLiteral.setChecked(true);
            }
        });
        messageText.setOnFocusChangeListener(this);
        llll.addView(messageText, -1, ww);
        lll.addView(llll, -1, ww);
        tv = new TextView(ac);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.subjecthelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv.setText(getString(R.string.subjectlabel));
        lll.addView(tv, -1, ww);
        n = PrefsManager.getSubjectTextType(ac, classNum, startOrEnd);
        subjectClass.setChecked(n == PrefsManager.SUBJECT_TEXT_CLASSNAME);
        subjectClass.setEnabled(sendMessage.isChecked());
        subjectClass.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (subjectClass.isChecked()) {
                    subjectName.setChecked(false);
                    subjectDescription.setChecked(false);
                    subjectLiteral.setChecked(false);
                }
            }
        });
        subjectClass.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.subjectnamehelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        subjectClass.setText(getString(R.string.subjectclasslabel));
        lll.addView(subjectClass, -1, ww);
        subjectName.setChecked(n == PrefsManager.SUBJECT_TEXT_EVENTNAME);
        subjectName.setEnabled(sendMessage.isChecked());
        subjectName.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (subjectName.isChecked()) {
                    subjectClass.setChecked(false);
                    subjectDescription.setChecked(false);
                    subjectLiteral.setChecked(false);
                }
            }
        });
        subjectName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, "Message with the event name as its text",
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        subjectName.setText(getString(R.string.subjectnamelabel));
        lll.addView(subjectName, -1, ww);
        subjectDescription.setChecked(n == PrefsManager.SUBJECT_TEXT_EVENTDESCRIPTION);
        subjectDescription.setEnabled(sendMessage.isChecked());
        subjectDescription.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (subjectDescription.isChecked()) {
                    subjectClass.setChecked(false);
                    subjectName.setChecked(false);
                    subjectLiteral.setChecked(false);
                }
            }
        });
        subjectDescription.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.subjectdescriptionhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        subjectDescription.setText(getString(R.string.subjectdescriptionlabel));
        lll.addView(subjectDescription, -1, ww);
        llll = new LinearLayout(ac);
        llll.setOrientation(LinearLayout.HORIZONTAL);
        subjectLiteral.setChecked(n == PrefsManager.SUBJECT_TEXT_LITERAL);
        subjectLiteral.setOnClickListener(new  View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (subjectLiteral.isChecked())
                {
                    subjectClass.setChecked(false);
                    subjectName.setChecked(false);
                    subjectDescription.setChecked(false);
                }
            }
        });
        subjectLiteral.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, getString(R.string.subjectliteralhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        llll.addView(subjectLiteral, -1, ww);
        subjectText.setEnabled(subjectLiteral.isEnabled());
        subjectText.setMaxLines(1);
        subjectText.setInputType(InputType.TYPE_CLASS_TEXT);
        subjectText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,getString(R.string.subjectliterallabel),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        subjectText.setText(PrefsManager.getSubjectLiteral(ac, classNum, startOrEnd));
        subjectText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                CharSequence s, int start, int count, int after) {
                // nothing
            }

            @Override
            public void onTextChanged(
                CharSequence s, int start, int before, int count) {
                // nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                subjectClass.setChecked(false);
                subjectName.setChecked(false);
                subjectDescription.setChecked(false);
                subjectLiteral.setChecked(true);
            }
        });
        subjectText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                    "Text Typed here is the subject if an email is sent.",
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        subjectText.setOnFocusChangeListener(this);
        llll.addView(subjectText, -1, ww);
        lll.addView(llll, -1, ww);
        ll.addView(lll, ww);
        adjustSendType();
    }

    public void saveOnPause(Activity ac, int classNum, int startOrEnd) {
        String s;
        int n;
        if (sendEmail.isChecked()) {
            PrefsManager.setMessageType(
                ac, classNum, startOrEnd, PrefsManager.SEND_MESSAGE_EMAIL);
        }
        else if (sendEmailOrText.isChecked()) {
            PrefsManager.setMessageType(
                ac, classNum, startOrEnd, PrefsManager.SEND_MESSAGE_EMAIL_OR_SMS);
        }
        else if (sendText.isChecked()) {
            PrefsManager.setMessageType(
                ac, classNum, startOrEnd, PrefsManager.SEND_MESSAGE_SMS);
        }
        else if (sendTextOrEmail.isChecked()) {
            PrefsManager.setMessageType(
                ac, classNum, startOrEnd, PrefsManager.SEND_MESSAGE_SMS_OR_EMAIL);
        }
        else if (sendMessage.isChecked()) {
            PrefsManager.setMessageType(
                ac, classNum, startOrEnd, PrefsManager.SEND_MESSAGE_NOWHERE);
        }
        else {
            PrefsManager.setMessageType(
                ac, classNum, startOrEnd, PrefsManager.SEND_NO_MESSAGE);
        }
        if (sendToEmail.isChecked()) {
            s = emailAddress.getText().toString();
        }
        else { s = null; }
        PrefsManager.setMessageAddress(ac, classNum, startOrEnd, s);
        if (sendToNumber.isChecked()) {
            s = smsNumber.getText().toString();
        }
        else { s = null; }
        PrefsManager.setMessageNumber(ac, classNum, startOrEnd, s);
        if (sendToContact.isChecked()) {
            s = contactName.getText().toString();
        }
        else { s = null; }
        PrefsManager.setMessageContact(ac, classNum, startOrEnd, s);
        PrefsManager.setMessageExtract(
            ac, classNum, startOrEnd, sendToContactFromEventName.isChecked());
        //noinspection CatchMayIgnoreException
        try {
            n = Integer.valueOf(firstWordNum.getText().toString());
        } catch (Exception e) {
            n = 1;
        }
        PrefsManager.setMessageFirstCount(
            ac, classNum, startOrEnd, n);
        //noinspection CatchMayIgnoreException
        try {
            n = Integer.valueOf(lastWordNum.getText().toString());
        } catch (Exception e) {
            n = 1;
        }
        PrefsManager.setMessageLastCount(
            ac, classNum, startOrEnd, n);
        PrefsManager.setMessageFirstDir(
            ac, classNum, startOrEnd, firstWordStartEnd.getSelectedItemPosition());
        PrefsManager.setMessageLastDir(
            ac, classNum, startOrEnd, lastWordStartEnd.getSelectedItemPosition());
        PrefsManager.setMessageTrim(
            ac, classNum, startOrEnd, removePunctuation.isChecked());
        if (smsTextClass.isChecked()) {
            PrefsManager.setMessageTextType(
                ac, classNum, startOrEnd, PrefsManager.MESSAGE_TEXT_CLASSNAME);
        }
        else if (smsTextName.isChecked()) {
            PrefsManager.setMessageTextType(
                ac, classNum, startOrEnd, PrefsManager.MESSAGE_TEXT_EVENTNAME);
        }
        else if (smsTextDescription.isChecked()) {
            PrefsManager.setMessageTextType(
                ac, classNum, startOrEnd, PrefsManager.MESSAGE_TEXT_EVENTDESCRIPTION);
        }
        else if (smsTextLiteral.isChecked()) {
            PrefsManager.setMessageTextType(
                ac, classNum, startOrEnd, PrefsManager.MESSAGE_TEXT_LITERAL);
        }
        else {
            PrefsManager.setMessageTextType(
                ac, classNum, startOrEnd, PrefsManager.MESSAGE_TEXT_NONE);
        }
        PrefsManager.setMessageLiteral(
                ac, classNum, startOrEnd, messageText.getText().toString());
        if (subjectClass.isChecked()) {
            PrefsManager.setSubjectTextType(
                ac, classNum, startOrEnd, PrefsManager.SUBJECT_TEXT_CLASSNAME);
        }
        else if (subjectName.isChecked()) {
            PrefsManager.setSubjectTextType(
                ac, classNum, startOrEnd, PrefsManager.SUBJECT_TEXT_EVENTNAME);
        }
        else if (subjectDescription.isChecked()) {
            PrefsManager.setSubjectTextType(
                ac, classNum, startOrEnd, PrefsManager.SUBJECT_TEXT_EVENTDESCRIPTION);
        }
        else if (subjectLiteral.isChecked()) {
            PrefsManager.setSubjectTextType(
                ac, classNum, startOrEnd, PrefsManager.SUBJECT_TEXT_LITERAL);
        }
        else {
            PrefsManager.setSubjectTextType(
                ac, classNum, startOrEnd, PrefsManager.SUBJECT_TEXT_NONE);
        }
        PrefsManager.setSubjectLiteral(
            ac, classNum, startOrEnd, subjectText.getText().toString());
    }
}
