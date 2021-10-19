/*
 * Copyright (c) 2021. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.widget.TextView;

import java.io.File;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

/**
 * Created by rparkins on 25/12/17.
 */

public class FileBrowserActivity extends Activity {
	private boolean nofile = true;

	/**
	 * Called when the activity is starting.  This is where most initialization
	 * should go: calling {@link #setContentView(int)} to inflate the
	 * activity's UI, using {@link #findViewById} to programmatically interact
	 * with widgets in the UI, calling
	 * {@link #managedQuery(Uri, String[], String, String[], String)} to retrieve
	 * cursors for data being displayed, etc.
	 *
	 * <p>You can call {@link #finish} from within this function, in
	 * which case onDestroy() will be immediately called without any of the rest
	 * of the activity lifecycle ({@link #onStart}, {@link #onResume},
	 * {@link #onPause}, etc) executing.
	 *
	 * <p><em>Derived classes must call through to the super class's
	 * implementation of this method.  If they do not, an exception will be
	 * thrown.</em></p>
	 *
	 * @param savedInstanceState If the activity is being re-initialized after
	 *                           previously being shut down then this Bundle contains the data it most
	 *                           recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
	 * @see #onStart
	 * @see #onSaveInstanceState
	 * @see #onRestoreInstanceState
	 * @see #onPostCreate
	 */
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.standard_filebrowser);
	}

	private String getDefaultDir() {
		return PrefsManager.getDefaultDir(this);
	}

	/**
	 * Called after {@link #onRestoreInstanceState}, {@link #onRestart}, or
	 * {@link #onPause}, for your activity to start interacting with the user.
	 * This is a good place to begin animations, open exclusive-access devices
	 * (such as the camera), etc.
	 *
	 * <p>Keep in mind that onResume is not the best indicator that your activity
	 * is visible to the user; a system window such as the keyguard may be in
	 * front.  Use {@link #onWindowFocusChanged} to know for certain that your
	 * activity is visible to the user (for example, to resume a game).
	 *
	 * <p><em>Derived classes must call through to the super class's
	 * implementation of this method.  If they do not, an exception will be
	 * thrown.</em></p>
	 *
	 * @see #onRestoreInstanceState
	 * @see #onRestart
	 * @see #onPostResume
	 * @see #onPause
	 */
	@Override
	protected void onResume() {
		super.onResume();
		final FileBrowserActivity ac = this;
		FileListView fileList = (FileListView)findViewById(R.id.fileListView);
		fileList.setOnDirectoryOrFileClickListener(new FileListView.OnDirectoryOrFileClickListener() {
			public void onDirectoryOrFileClick(File file) {
				if (!file.isDirectory()) {
					PrefsManager.setDefaultDir(ac, file.getParent());
					Intent it = new Intent();
					it.putExtra("filename", file.getPath());
					setResult(RESULT_OK, it);
					nofile = false;
					finish();
				}
			}
		});
		TextView textViewDirectory = (TextView)findViewById(R.id.textViewDirectory);
		fileList.setTextViewDirectory(textViewDirectory);

		TextView textViewFile = (TextView) findViewById(R.id.textViewFile);
		fileList.setTextViewFile(textViewFile);
		String s = getDefaultDir();
		if (s != null) {
			fileList.init(new File (s));
		} else {
			fileList.init(Environment.getExternalStorageDirectory());
		}
	}

	/**
	 * Called as part of the activity lifecycle when an activity is going into
	 * the background, but has not (yet) been killed.  The counterpart to
	 * {@link #onResume}.
	 *
	 * <p>When activity B is launched in front of activity A, this callback will
	 * be invoked on A.  B will not be created until A's onPause returns,
	 * so be sure to not do anything lengthy here.
	 *
	 * <p>This callback is mostly used for saving any persistent state the
	 * activity is editing, to present a "edit in place" model to the user and
	 * making sure nothing is lost if there are not enough resources to start
	 * the new activity without first killing this one.  This is also a good
	 * place to do things like stop animations and other things that consume a
	 * noticeable amount of CPU in order to make the switch to the next activity
	 * as fast as possible, or to close resources that are exclusive access
	 * such as the camera.
	 *
	 * <p>In situations where the system needs more memory it may kill paused
	 * processes to reclaim resources.  Because of this, you should be sure
	 * that all of your state is saved by the time you return from
	 * this function.  In general {@link #onSaveInstanceState} is used to save
	 * per-instance state in the activity and this method is used to store
	 * global persistent data (in content providers, files, etc.)
	 *
	 * <p>After receiving this call you will usually receive a following call
	 * to {@link #onStop} (after the next activity has been resumed and
	 * displayed), however in some cases there will be a direct call back to
	 * {@link #onResume} without going through the stopped state.
	 *
	 * <p><em>Derived classes must call through to the super class's
	 * implementation of this method.  If they do not, an exception will be
	 * thrown.</em></p>
	 *
	 * @see #onResume
	 * @see #onSaveInstanceState
	 * @see #onStop
	 */
	@Override
	protected void onPause() {
		super.onPause();
		if (isFinishing() && nofile) {
			setResult(RESULT_CANCELED);
		}
	}
}

