/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.co.yahoo.p1rpp.calendartrigger.R;

/**
 *
 *
 *
 * @author strangeoptics
 *
 */
public class FileSystemAdapter extends ArrayAdapter<FileData> {

    private String root;
    private List<String> paths = new ArrayList<String>();
    private File path;
    private File file;
    private Map<String, Integer> mapExtentions = new HashMap<String, Integer>();
    private OnGetView onGetView;

    public FileSystemAdapter(Context context, int rowViewResourceId) {
        super(context, rowViewResourceId, new ArrayList<FileData>());
        //   /mnt/sdcard
        root = Environment.getExternalStorageDirectory().getPath();
        path = new File(root + "/");
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        TextView textView;
        ImageView imageView;

        if (convertView == null) {
            view = mInflater.inflate(mResource, parent, false);
        } else {
            view = convertView;
        }

        try {
            textView = (TextView) view.findViewById(R.id.textFileName);
            imageView = (ImageView) view.findViewById(R.id.imageFileType);
        } catch(ClassCastException e) {
            Log.e("ArrayAdapter", "You must supply a resource ID for a TextView");
            throw new IllegalStateException(
                "ArrayAdapter requires the resource ID to be a TextView", e);
        }


        FileData item = getItem(position);
        textView.setText((CharSequence)item.name);

        if(item.directory) {
            imageView.setImageResource(mapExtentions.get("folder"));
        } else {
            String fileExtention = getFileExtention(item.name);
            Integer resource = mapExtentions.get(fileExtention);

            if(resource != null) {
                imageView.setImageResource(resource);
            } else {
                imageView.setImageResource(mapExtentions.get("file"));
            }
        }

        if(onGetView != null) {
            onGetView.onGetView(position, convertView, parent, item);
        }

        return view;
    }

    /**
     * Primitive extraction of the file extention in lower case. <br>
     * file.ext -> ext <br>
     * If there is no '.' in the file name, null is returned!
     * @param fileName
     * @return extention or null
     */
    private String getFileExtention(String fileName) {
        int lastIndexOfPoint = fileName.lastIndexOf('.');
        if(lastIndexOfPoint != -1) {
            return fileName.substring(lastIndexOfPoint+1).toLowerCase();
        }
        return null;
    }


    /**
     * Reflect the folder structure to the ui
     */
    public void showFileSystem() {
        List<FileData> items = new ArrayList<FileData>();
        File[] files = path.listFiles();

        if (!path.getAbsolutePath().equals(root)) {
            items.add(new FileData(null, "../", true));
        }

        for (int i = 0; i < files.length; i++) {
            File file = files[i];

            if (!file.isHidden() && file.canRead()) {
				/* special check to omit sql journal files */
                String s = getFileExtention(file.getName());
                if (   (s == null)
                       || (!s.contentEquals("sql-journal"))) {
                    if (file.isDirectory()) {
                        items.add(new FileData(file, file.getName() + "/", true));
                    } else {
                        items.add(new FileData(file, file.getName(), false));
                    }
                }
            }
        }

        clearNoNotification();
        addAll(items);
    }

    public String getRoot() {
        return root;
    }

    /**
     * Moves the Adapter to the next folder/file: <br>
     * @param pathSuffix suffix to the path like "images/"
     * @return returns the actual position
     */
    public File move(String pathSuffix) {
        if(pathSuffix.equalsIgnoreCase("../")) {
            if(path.isDirectory()) {
                path = path.getParentFile();
            } else {
                path = path.getParentFile().getParentFile();
            }
        } else {
            if(path.isDirectory()) {
                File newPath = new File(path, pathSuffix);
                if(newPath.canRead()) {
                    path = newPath;
                }
            } else {
                File newPath = new File(path.getParentFile(), pathSuffix);
                if(newPath.canRead()) {
                    path = newPath;
                }
            }
        }

        return path;
    }

    /**
     *
     * @param ext in lower case without a point e.g. "zip"
     * @param resource R.drawable.xxx
     */
    public void addExtention(String ext, int resource) {
        mapExtentions.put(ext, Integer.valueOf(resource));
    }

    public void clearExtentionMapping() {
        mapExtentions.clear();
    }

    public boolean hasExtentions() {
        return !mapExtentions.isEmpty();
    }

    public File getPath() {
        return path;
    }

    public void setPath(File path) {
        this.path = path;
    }

    public void setOnGetView(OnGetView onGetView) {
        this.onGetView = onGetView;
    }

    /**
     * Interface definition for a callback to be invoked when the getView()-Method of the Adapter is called.
     */
    public interface OnGetView {

        /**
         * Callback method to be invoked when the getView()-Method of the Adapter is called.
         */
        void onGetView(int position, View convertView, ViewGroup paren, FileData item);
    }
}
