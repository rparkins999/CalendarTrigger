/*
 * Copyright (c) 2021. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

/*
 * Created by rparkins on 25/12/17.
 *
 * This contains a bit of logic common to
 * ActionStartActivity and ActionStopActivity
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.content.Intent;
import android.widget.CheckBox;

public class ActionActivity extends Activity {
    protected CheckBox showNotification;
    protected SoundBox playSound;
    protected SoundFileLabel soundFilename;
    protected Boolean gettingFile;

    public void getFile() {
        gettingFile = true;
        Intent it = new Intent(this, FileBrowserActivity.class);
        startActivityForResult(it, 0, null);
    }

    protected void openThis(String fileName) {}

    /**
     * Called when an activity you launched exits, giving you the requestCode
     * you started it with, the resultCode it returned, and any additional
     * data from it.  The <var>resultCode</var> will be
     * {@link #RESULT_CANCELED} if the activity explicitly returned that,
     * didn't return any result, or crashed during its operation.
     *
     * <p>You will receive this call immediately before onResume() when your
     * activity is re-starting.
     *
     * <p>This method is never invoked if your activity sets
     * android.R.styleable#AndroidManifestActivity_noHistory noHistory to
     * <code>true</code>.
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param data        An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     * @see #startActivityForResult
     * @see #createPendingResult
     * @see #setResult(int)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String fileName;
        if (resultCode == RESULT_OK) {
            fileName = data.getStringExtra("filename");
        } else {
            fileName = "";
        }
        openThis(fileName);
        soundFilename.setFile(fileName);
    }
}
