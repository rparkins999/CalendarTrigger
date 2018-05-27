/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;

import uk.co.yahoo.p1rpp.calendartrigger.R;

/**
 * Created by rparkins on 25/12/17.
 */

public class FileBrowserFragment extends Fragment {

	private ActionFragment mOwner;

	public FileBrowserFragment() {
		mOwner = null;
	}

	public void setOwner(ActionFragment owner) {
		mOwner = owner;
	}

	/* (non-Javadoc)
	 * @see android.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup c,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.standard_filebrowser, c, false);
	}

	/* (non-Javadoc)
	 * @see android.app.Fragment#onViewCreated(android.view.View, android.os.Bundle)
	 */
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		View v = getView();
		FileListView fileList = (FileListView)v.findViewById(R.id.fileListView);
		fileList.setOnDirectoryOrFileClickListener(new FileListView.OnDirectoryOrFileClickListener() {
			public void onDirectoryOrFileClick(File file) {
				if (!file.isDirectory()) {
					mOwner.openThis(file);
				}
			}
		});

		TextView textViewDirectory = (TextView)v.findViewById(R.id.textViewDirectory);
		fileList.setTextViewDirectory(textViewDirectory);

		TextView textViewFile = (TextView) v.findViewById(R.id.textViewFile);
		fileList.setTextViewFile(textViewFile);
		String s = mOwner.getDefaultDir();
		if (s != null) {
			fileList.init(new File (s));
		} else {
			fileList.init(Environment.getExternalStorageDirectory());
		}
	}
}

