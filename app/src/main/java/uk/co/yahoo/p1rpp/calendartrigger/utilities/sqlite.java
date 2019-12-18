/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */
package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;

import uk.co.yahoo.p1rpp.calendartrigger.R;

/* These descriptions were obtained by searching the Android source code
 * SQLiteAbortException (from SQLITE_ABORT)
 *      This can happen either through a call to ABORT in a trigger,
 *      or as the result of using the ABORT conflict clause.
 *          It can also be generated if the user aborts a transaction.
 * SQLiteAccessPermException (from SQLITE_PERM)
 *      This exception class is used when sqlite can't access the database file
 *      due to lack of permissions on the file.
 * SQLiteBindOrColumnIndexOutOfRangeException (sometimes from SQLITE_RANGE)
 *      Thrown if the the bind or column parameter index is out of range.
 *          Actually if the number of bind parameters is not equal to the number
 *          of ?s in the selection string
 *          or if the columnIndex argument of Cursor.get<type> is too big.
 * SQLiteBlobTooBigException (from SQLITE_TOOBIG)
 *      A blob argument was bigger than the implmentation's limit.
 * SQLiteCantOpenDatabaseException (from SQLITE_CANTOPEN)
 *      The SQLITE_CANTOPEN result code indicates that SQLite was unable to open a file.
 * SQLiteConstraintException (from SQLITE_CONSTRAINT)
 *      The SQLITE_CONSTRAINT error code means that an SQL constraint violation
 *      occurred while trying to process an SQL statement.
 *          This can be caused by a RAISE function being executed in a TRIGGER or
 *          a FOREIGN KEY constraint being violated or an ON CONFLICT
 *          clause finding a conflict.
 * SQLiteDatabaseCorruptException (from SQLITE_CORRUPT or SQLITE_NOTADB)
 *      This indicates that a file opened as a database is not a valid Sqlite database.
 * SQLiteDatabaseLockedException (from SQLITE_BUSY)
 *      This error (SQLITE_BUSY) occurs if one connection has the database
 *      open in WAL mode and another tries to change it to non-WAL.
 *          It can also occur if a thread using one connection tries to read from
 *          or write to the database while another thread using a different connection
 *          is in the middle of a conflicting (read with write or write with write)
 *          transaction. However the implementation uses a timeout (2.5 seconds) to
 *          cause a retry without throwing an exception in most such cases.
 *          Conflicting operations from different threads in the same process using
 *          the same connection only cause a conflict if they affect the same table,
 *          and in that case a SQLiteTableLockedException is thrown.
 * SQLiteDatatypeMismatchException
 * SQLiteDiskIOException
 * SQLiteDoneException
 * SQLiteFullException
 * SQLiteMisuseException
 * SQLiteOutOfMemoryException
 * SQLiteReadOnlyDatabaseException
 * SQLiteTableLockedException
 */

// Not using private storage here is a deliberate decision because
// I don't approve of applications storing information that they
// don't allow the device owner to read.
// However this does mean that I have to be very careful when I
// read data because it may have been corrupted by the user or another app..

public class sqlite extends Object {
    private Context m_context;
    private SQLiteDatabase m_db;
    private boolean m_written;

    private void report(String small, String big) {
        new MyLog(m_context, big);
        if (Looper.myLooper() == Looper.getMainLooper())
        {
            Toast.makeText(m_context, big, Toast.LENGTH_LONG).show();
        }
        else
        {
            new Notifier(m_context, small, big);
        }
        throw new Error(big);
    }

    // All column names contain "_" so that they can't be reserved words
    private String getCreator(String s) {
        if (s.equals("VACUUMDATA")) {
            return "CREATE TABLE VACUUMDATA (VACUUM_COUNT INTEGER)";
        }
        else if (s.equals("FLOATINGEVENTS")) {
            return "CREATE TABLE FLOATINGEVENTS "
                 + "(EVENT_ID INTEGER, START_WALLTIME_MILLIS INTEGER,"
                 + "END_WALLTIME_MILLIS INTEGER)";
        }
        // If this occurs. it's a programming error
        String big = "getCreator(" + s + ") unknown table name";
        report("Bad table name", big);
        return null; // unreachable
    }

    // We assume here that we don't re-use column names in different tables.
    private String getTableName(String s) {
        if (s.equals("VACUUM_COUNT")) { return "VACUUMDATA"; }
        else if (s.equals("EVENT_ID")) { return "FLOATINGEVENTS"; }
        else if (s.equals("START_WALLTIME_MILLIS")) { return "FLOATINGEVENTS"; }
        else if (s.equals("END_WALLTIME_MILLIS")) { return "FLOATINGEVENTS"; }
        // If this occurs. it's a programming error
        String big = "getTableName(" + s + ") unknown column name";
        report("Bad column name", big);
        return null; // unreachable
    }

    // Returns e if not handled or null to try again
    // We assume here that column names don't contain spaces
    private void handleException (int i, SQLiteException e) {
        String s = e.getMessage();
        if (s.startsWith("no such table:")) {
            String tableName = s.split(" ")[3];
            new MyLog(m_context,
                m_context.getString(R.string.table) + tableName +
                 m_context.getString(R.string.creating));
            try {
                m_db.execSQL(getCreator(tableName));
                return;
            } catch (SQLiteException ee) {
                e = ee;
            }
        }
        else if (s.startsWith("no such column:")) {
            String columnName = s.split(" ")[3];
            String tableName = getTableName(columnName);
            new MyLog(m_context,
                m_context.getString(R.string.creatingnew) + tableName,
                m_context.getString(R.string.column) + columnName +
                    m_context.getString(R.string.dropping) + tableName + ".");
            try {
                m_db.execSQL("DROP TABLE " + tableName);
                m_db.execSQL(getCreator(tableName));
                return ;
            } catch (SQLiteException ee) {
                e = ee;
            }
        }
        else
        {
            String t = e.getClass().getName();
            if (   (t.equals("SQLiteTableLockedException"))
                || (t.equals("SQLiteDatabaseLockedException")))
            {
                new MyLog(m_context, t);
                if (i > 0) { return; }
            }
        }
        String small = m_context.getString(R.string.databaseerror);
        String big = DataStore.getDatabaseFile(m_context) +
            m_context.getString(R.string.unrecoverable)
            + e.getCause().toString()+ e.getLocalizedMessage();
        report(small, big);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        for (int i = 10; ; --i) {
            try {
                return m_db.rawQuery(sql, selectionArgs);
            } catch (SQLiteException e) {
                handleException(i, e);
            }
        }
    }

    public long insert (String table, String nullColumnHack, ContentValues values)  {
        for (int i = 10; ; --i) {
            try {
                long result = m_db.insert(table, nullColumnHack, values);
                m_written = true;
                return result;
            } catch (SQLiteException e) {
                handleException(i, e);
           }
        }
    }

    public int delete (String table, String whereClause, String[] whereArgs) {
        for (int i = 10; ; --i) {
            try {
                int result =  m_db.delete(table, whereClause, whereArgs);
                m_written = true;
                return result;
            } catch (SQLiteException e) {
                // Nonexistent table or column is almost certainly a programming error:
                // we should only ever delete records that we have just found.
                // However it's possible that some other process deleted the
                // table while we were looking at it, and maybe created it
                // with different columns, so we handle these in the normal way.
                handleException(i, e);
            }
        }
    }

    public long getLong(Cursor cursor, int columnIndex)
        throws NumberFormatException
    {
        String s = cursor.getString(columnIndex);
        if ((s != null) && s.matches("^-?[0-9]+$")) {
            return Long.parseLong(s);
        }
        else
        {
            throw(new NumberFormatException("Bad number " + s));
        }
    }

    public long getUnsignedLong(Cursor cursor, int columnIndex)
        throws NumberFormatException
    {
        String s = cursor.getString(columnIndex);
        if ((s != null) && s.matches("^[0-9]+$")) {
            return Long.parseLong(s);
        }
        else
        {
            throw(new NumberFormatException("Bad number " + s));
        }
    }

    // Vacuuming needs to be done from time to time, but it isn't urgent.
    private static int WRITECOUNT = 1000;
    public void tryVacuum ()  {
        long count = 1;
        try {
            Cursor cursor = m_db.rawQuery(
                "SELECT VACUUM_COUNT FROM VACUUMDATA", null);
            if (cursor.moveToFirst()) {
                count = getUnsignedLong(cursor, 0) + 1;
                if (count > WRITECOUNT) {
                    // Time to vacuum, do so and reset count
                    m_db.execSQL("VACUUM");
                }
                // fall through to update count
            }
            else {
                new MyLog(m_context,
                    m_context.getString(R.string.norows));
                m_db.execSQL("INSERT INTO VACUUMDATA ( VACUUM_COUNT ) VALUES ( "
                             + count + " )");
                return;
            }
        } catch (SQLiteException e) {
            handleException(0, e);
        } catch (NumberFormatException ee) {
            new MyLog(m_context,m_context.getString(R.string.value)
                + ee.getMessage().replace("Bad number ", "")
                + m_context.getString(R.string.countnoninteger));
            // fall through to replace invalid count with 0
        }
        try {
            m_db.execSQL("UPDATE VACUUMDATA SET VACUUM_COUNT = " + count);
            return;
        } catch (SQLiteException e) {
            handleException(0, e);
        }
    }

    public sqlite (Context context) {
        m_context = context;
        m_db = null;
        m_written = false;
        Resources res = m_context.getResources();
        String fileName = DataStore.getDatabaseFile(context);
        if (fileName != null) {
            for (int i = 10; ; --i) {
                try {
                    m_db = SQLiteDatabase.openOrCreateDatabase(
                        fileName, null, null);
                    break;
                } catch (SQLiteException e) {
                    handleException(i, e);
                }
            }
        }
    }

    public void close()
    {
        if (m_db != null) {
            try {
                if (m_written) {
                    tryVacuum();
                }
                m_db.close();
                m_db = null;
            } catch (SQLiteException e) {}
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
