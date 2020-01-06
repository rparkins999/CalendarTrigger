/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;

import uk.co.yahoo.p1rpp.calendartrigger.Comparator.ComparatorChain;
import uk.co.yahoo.p1rpp.calendartrigger.Comparator.DirectoryFileComparator;
import uk.co.yahoo.p1rpp.calendartrigger.Comparator.NameComparator;
import uk.co.yahoo.p1rpp.calendartrigger.R;

/**
 * Modified from:-
 *
 * Supports multiple Comparators by a ComparatorChain see getComparator(). <br>
 *
 * root: /mnt/sdcard
 *
 * @author strangeoptics
 *
 */

public class FileListView extends ListView implements AdapterView
    .OnItemClickListener {

	private FileSystemAdapter adapter;
	private OnDirectoryOrFileClickListener onDirectoryOrFileClickListener;
	private TextView textViewDirectory;
	private TextView textViewFile;
	private ComparatorChain<FileData> comparator;

	public FileListView(Context context, AttributeSet attrs) {
		super(context, attrs);
				adapter = new FileSystemAdapter(context, R.layout.filemanager_row_icon);
		setAdapter(adapter);
				comparator = new ComparatorChain<>();
		comparator.addComparator(new DirectoryFileComparator());
		comparator.addComparator(new NameComparator());
		setOnItemClickListener(this);
	}

	/**
	 * Initializes the FileListView <br>
	 * - sets the root path. If null it will be the root of the sdcard <br>
	 * - sets the default extentions if no customs are present <br>
	 * - updates the UI <br>
	 * <br>
	 * Call this method at the end of the onCreate()-Methode.
	 *
	 * @param path root directory
	 */
	public void init(File path) {
		adapter.setPath(path);

		if(!adapter.hasExtentions()) {
			setDefaultFileExtentions();
		}

		adapter.showFileSystem();

		updateUI(adapter.getPath());
	}

	public FileSystemAdapter getAdapter() {
		return adapter;
	}

	public void setDefaultFileExtentions() {
		adapter.addExtention("folder", R.drawable.folder);
		adapter.addExtention("file", R.drawable.file);
		adapter.addExtention("jpg", R.drawable.image);
		adapter.addExtention("zip", R.drawable.packed);
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		FileData item = adapter.getItem(position);
		File file = adapter.move(item.name);

		if (file.isDirectory()) {
			if (file.canRead()) {
				adapter.showFileSystem();
				if(comparator != null) {
					adapter.sort(comparator);
				}
			}
		}
		updateUI(file);
	}

	private void updateUI(File file) {
		if(onDirectoryOrFileClickListener != null) {
			onDirectoryOrFileClickListener.onDirectoryOrFileClick(file);
		}

		if(textViewDirectory != null) {
			if(file.isDirectory()) {
				textViewDirectory.setText(file.getPath());
			} else {
				textViewDirectory.setText(file.getParent());
			}
		}
		if(textViewFile != null) {
			if(file.isFile()) {
				textViewFile.setText(file.getName());
			} else {
				textViewFile.setText("");
			}
		}
	}

	public void setTextViewDirectory(TextView textView) {
		this.textViewDirectory = textView;
	}

	public void setTextViewFile(TextView textView) {
		this.textViewFile = textView;
	}

	public void setOnDirectoryOrFileClickListener(OnDirectoryOrFileClickListener listener) {
		onDirectoryOrFileClickListener = listener;
	}

	/**
     * Interface definition for a callback to be invoked when an directory or file has been clicked.
     */
    public interface OnDirectoryOrFileClickListener {

        /**
         * Callback method to be invoked when an directory or file has been clicked.
         */
        void onDirectoryOrFileClick(File file);
    }
}
