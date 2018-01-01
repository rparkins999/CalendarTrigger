/*
 * Copyright (c) 2017. Richard P. Parkins, M. A.
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

 import java.io.File;

/**
 *
 * @author strangeoptics
 *
 */

public class FileData {

    public File file;
    public String name;
    public boolean directory = false;

    public FileData(File file, String name, boolean directory) {
        this.file = file;
        this.name = name;
        this.directory = directory;
    }
}
