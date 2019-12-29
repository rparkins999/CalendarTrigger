/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */
package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

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
// However this does mean that I have to be very careful when I read from
// my database because it may have been corrupted by the user or another app.

public class sqlite extends Object {
    private Context m_context;
    private SQLiteDatabase m_db;
    private boolean m_written;
    private String m_saveQuerySql;
    private String[] m_saveQueryArgs;

    // Report a fatal error.
    // We send a message to the log file (if logging is enabled).
    // If this thread is the UI thread, we display a Toast:
    // otherwise we show a notification.
    // Then we throw an Error exception which will cause Android to
    // terminate the thread and display a (not so helpful) message.
    private void fatal(String small, String big) {
        new MyLog(m_context, big);
        if (   (Activity.class.isInstance(m_context))
            && ((Activity)m_context).hasWindowFocus())
        {
            Toast.makeText(m_context, big, Toast.LENGTH_LONG).show();
        }
        else
        {
            new Notifier(m_context, small, big);
        }
        throw new Error(big);
    }

    // All table names are concatenated words so that they shouldn't be reserved words
    // All column names contain "_" so that they shouldn't be reserved words
    private String getCreator(String s) {
        if (s.equals("VACUUMDATA")) {
            return "CREATE TABLE VACUUMDATA (" +
                // This counts the number of writes to the database.
                // When it reaches 1000, we try a VACUUM.
                // If it succeeds, we reset the count to 1.
                "VACUUM_COUNT INTEGER)";
        }
        else if (s.equals("FLOATINGEVENTS")) {
            // This table keeps track of floating time events.
            // It is used to adjust the UTC times when the time zone changes.
            return "CREATE TABLE FLOATINGEVENTS ("
                 // This is the _ID of a floating time event.
                 // Event_id's are unique, so we don't need the calendar id.
                 + " EVENT_ID INTEGER,"
                 // This is the start time of the event in wall clock time.
                 // It is used to recalculate the UTC start time (DTSTART)
                 // from the time zone offset when it changes.
                 + " START_WALLTIME_MILLIS INTEGER,"
                 // This is the end time of the event in wall clock time.,
                 // It is used to recalculate DTEND for non-recurring events.
                 // For recurring events Android uses DURATION instead.
                 + " END_WALLTIME_MILLIS INTEGER)";
        }
        else if (s.equals("ACTIVEEVENTS")) {
            // This table keeps track of active events.
            return "CREATE TABLE ACTIVEEVENTS ("
                 // This is the class name of the event.
                 // An event has a separate entry in this table
                 // for each class in which it is active.
                 // This is because the start or end time offset of
                 // events can be different for different classes.
                 // If the class no longer exists, we delete the record.
                 + "ACTIVE_CLASS_NAME TEXT"
                 // This is 1 if this is an immediate event, and 0 otherwise.
                 // If it is 1, ACTIVE_EVENT_ID is meaningless.
                 + "ACTIVE_IMMEDIATE INTEGER"
                 // This is the _ID of a non-immediate active event.
                 // If the event no longer exists, we try to do the end actions.
                 // Some end actions require information from the event and
                 // so cannot be done if the event has been deleted.
                 + "ACTIVE_EVENT_ID INTEGER"
                 // This is the state of an active event:
                 // the states are defined below.
                 + "ACTIVE_STATE INTEGER"
                 // This is the time when we next need to wake up for this event.
                 + "ACTIVE_NEXT_ALARM INTEGER"
                 + ")";

        }
        // If this occurs. it's a programming error
        String big = "getCreator(" + s + m_context.getString(R.string.unknowntable);
        fatal(m_context.getString(R.string.badtable), big);
        return null; // unreachable
    }

    public static final int ACTIVE_CLASS_NAME = 0;
    public static final int ACTIVE_IMMEDIATE = 1;
    public static final int ACTIVE_EVENT_ID = 2;
    public static final int ACTIVE_STATE = 3;
    public static final int ACTIVE_NEXT_ALARM = 4;

    // Possible values for ACTIVE_STATE:-
    // This state is only here for completeness.
    // It should never occur because inactive events get deleted from the table.
    public static final int NOT_ACTIVE = 0;
    // The event has reached its start time, but is waiting for other conditions
    // to be satisfied before it can become fully active.
    public static final int ACTIVE_START_WAITING = 1;
    // The event was inactive before and has become fully active.
    // This state exists only for one pass through the table.
    public static final int ACTIVE_STARTING = 2;
    // The event has completed some of the class's start actions,
    // but is waiting for some resource (such as an internet connection) to
    // become available before it can complete others.
    public static final int ACTIVE_START_SENDING = 3;
    // The event has completed all the class's start actions and is now fully active.
    public static final int ACTIVE_STARTED = 4;
    // The event has reached its end time, but is waiting for other conditions
    // to be satisfied before it can become inactive.
    public static final int ACTIVE_END_WAITING = 5;
    // The event was active and has become inactive.
    // This state exists only for one pass through the table.
    public static final int ACTIVE_ENDING = 6;
    // The event has completed some of the class's end actions,
    // but is waiting for some resource (such as an internet connection) to
    // become available before it can complete others.
    public static final int ACTIVE_END_SENDING = 7;
    // The next state would be NOT_ACTIVE, but the record gets deleted
    // because we don't need to keep track of this event for this class any more.

    public String getActiveStateName(int n) {
        switch (n) {
            case NOT_ACTIVE: return "NOT_ACTIVE";
            case ACTIVE_START_WAITING: return "ACTIVE_START_WAITING";
            case ACTIVE_STARTING: return "ACTIVE_STARTING";
            case ACTIVE_START_SENDING: return "ACTIVE_START_SENDING";
            case ACTIVE_STARTED: return "ACTIVE_STARTED";
            case ACTIVE_END_WAITING: return "ACTIVE_START_SENDING";
            case ACTIVE_ENDING: return "ACTIVE_ENDING";
            case ACTIVE_END_SENDING: return "ACTIVE_END_SENDING";
            default: return "Unknown state" + n;
        }
    }

    // We assume here that we don't re-use column names in different tables.
    private String getTableName(String s) {
        if (s.equals("VACUUM_COUNT")) { return "VACUUMDATA"; }
        else if (s.equals("EVENT_ID")) { return "FLOATINGEVENTS"; }
        else if (s.equals("START_WALLTIME_MILLIS")) { return "FLOATINGEVENTS"; }
        else if (s.equals("END_WALLTIME_MILLIS")) { return "FLOATINGEVENTS"; }
        else if (s.equals("ACTIVE_CLASS_NAME")) { return "ACTIVEEVENTS"; }
        else if (s.equals("ACTIVE_IMMEDIATE")) { return "ACTIVEEVENTS"; }
        else if (s.equals("ACTIVE_EVENT_ID")) { return "ACTIVEEVENTS"; }
        else if (s.equals("ACTIVE_STATE")) { return "ACTIVEEVENTS"; }
        else if (s.equals("ACTIVE_NEXT_ALARM")) { return "ACTIVEEVENTS"; }
        // If this occurs. it's a programming error
        String big = "getTableName(" + s + m_context.getString(R.string.unknowncolumn);
        fatal(m_context.getString(R.string.badcolumn), big);
        return null; // unreachable
    }

    // Get table name from Cursor.
    private String getTableName(Cursor cr) {
        String table = getTableName(cr.getColumnName(0));
        if (table == null) {
            // This should be impossible because the query should not
            // have succeeded if there was no table in it.
            String small = m_context.getString(R.string.notablename);
            fatal(small, small + m_saveQuerySql);
        }
        return table;
    }

    public String rowVacToString(Cursor cr) {
        return "(" + cr.getString(0) + ")";
    }

    public String rowFloatToString(Cursor cr) {
        StringBuilder builder =
            new StringBuilder("(event ID " + cr.getString(0));
        builder.append(", ");
        DateFormat df = DateFormat.getDateTimeInstance();
        try {
            builder.append(df.format(getUnsignedLong(cr,1)));
        } catch (NumberFormatException e) {
            builder.append("?);");
            builder.append(cr.getString(1));
            builder.append("?);");
        }
        builder.append(" to ");
        try {
            builder.append(df.format(getUnsignedLong(cr,2)));
        } catch (NumberFormatException e) {
            builder.append("?);");
            builder.append(cr.getString(2));
            builder.append("?);");
        }
        builder.append(")");
        return builder.toString();
    }

    public String rowActiveToString(Cursor cr) {
        StringBuilder builder =
            new StringBuilder("(");
        builder.append(cr.getString(ACTIVE_CLASS_NAME));
        builder.append(", ");
        if (cr.getLong(ACTIVE_IMMEDIATE) != 0) {
            builder.append(m_context.getString(R.string.immediate));
        }
        else
        {
            builder.append("event ID ");

            builder.append(cr.getString(ACTIVE_EVENT_ID));
        }
        builder.append(", ");
        try {
            int n = (int)getUnsignedLong(cr, ACTIVE_STATE);
            builder.append(m_context.getString(R.string.state));
            builder.append(getActiveStateName(n));
        } catch (NumberFormatException e) {
            builder.append(m_context.getString(R.string.badstate));
            builder.append(cr.getString(3));
        }
        builder.append(m_context.getString(R.string.alarm));
        try {
            DateFormat df = DateFormat.getDateTimeInstance();
            builder.append(df.format(getUnsignedLong(cr, ACTIVE_NEXT_ALARM)));
        } catch (NumberFormatException e) {
            builder.append("?");
            builder.append(cr.getString(4));
            builder.append("?");
        }
        builder.append(")");
        return builder.toString();
    }

    // Return a string describing the row pointed to by a Cursor.
    // Note that this is used for error messages, so it has to work
    // if the row contains bad data. However it doesn't have to work if the
    // table does not contain the right columns, since we deal with that
    // elsewhere.
    // The versions above can be used if we know which table it is from.
    public String rowToString(Cursor cr) {
        String tableName = getTableName(cr);
        if (tableName.equals("VACUUMDATA")) {
            return rowVacToString(cr);
        }
        else if (tableName.equals("FLOATINGEVENTS")) {
            return rowFloatToString(cr);
        }
        else if (tableName.equals("ACTIVEEVENTS")) {
            return rowActiveToString(cr);
        }
        // If this occurs. it's a programming error
        String small = m_context.getString(R.string.badcursor);
        String big = m_context.getString(R.string.badrowtocolumn);
        fatal(small, big);
        return null; // unreachable
    }

    // This normally returns, having patched up the database if necessary
    // so that the caller can try again if it wants to.
    // Unrecoverable problems result in a call to fatal() above.
    // We assume here that our column names don't contain spaces
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
                // Try waiting 0.1 second for the other transaction to complete
                if (i > 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ee) {}
                    return;
                }
            }
        }
        String small = m_context.getString(R.string.databaseerror);
        String big = DataStore.getDatabaseFile(m_context) +
            m_context.getString(R.string.unrecoverable)
            + e.getCause().toString()+ e.getLocalizedMessage();
        fatal(small, big);
    }

    private Cursor rawQueryNoSave(String sql, String[] selectionArgs) {
        for (int i = 10; ; --i) {
            try {
                return m_db.rawQuery(sql, selectionArgs);
            } catch (SQLiteException e) {
                handleException(i, e);
            }
        }
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        m_saveQuerySql = sql;
        m_saveQueryArgs = selectionArgs;
        return rawQueryNoSave(sql, selectionArgs);
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

    public int update(
        String table, ContentValues values, String whereClause, String[] whereArgs) {
        for (int i = 10; ; --i) {
            try {
                int result =  m_db.update(table, values, whereClause, whereArgs);
                m_written = true;
                return result;
            } catch (SQLiteException e) {
                // Nonexistent table or column is almost certainly a programming error:
                // we should only ever update records that we have just found.
                // However it's possible that some other process deleted the
                // table while we were looking at it, and maybe created it
                // with different columns, so we handle these in the normal way.
                handleException(i, e);
            }
        }
    }

    public int update(Cursor cr, ContentValues cv) {
        StringBuilder whereClause = new StringBuilder();
        ArrayList<String> whereArgs = new ArrayList<String>();
        int columns = cr.getColumnCount();
        boolean first = true;
        int i;
        for ( i = 0; i < columns; ++i) {
            if (first) { first = false; } else {
                whereClause.append(" AND ");
            }
            whereClause.append(cr.getColumnName(i));
            whereClause.append(" IS ?");
            whereArgs.add(cr.getString(i));
        }
        String parts[] = m_saveQuerySql.split("\\s+");
        for (i  = 4; i < parts.length; ++i) {
            if (   (parts[i].compareTo("IS") == 0)
                && (cr.getColumnIndex(parts[i - 1]) < 0)) {
                // This can't be the first one because that would mean no
                // columns were returned by the query.
                whereClause.append(" AND ");
                whereClause.append(parts[i - 1]);
                whereClause.append(" IS ?");
                whereArgs.add(parts[++i]);
            }
        }
        // m_saveQueryArgs is only used for its type here.
        return update(getTableName(cr), cv,
            whereClause.toString(), whereArgs.toArray(m_saveQueryArgs));
    }

    public int update(Cursor cr, String columnName, String value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return update(cr, cv);
    }

    public int update(Cursor cr, String columnName, long value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return update(cr, cv);
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

    // This deletes the row that a Cursor returned by rawQuery() is looking at.
    // We assume that the columns returned plus the original whereClause
    // are sufficient to identify the row to be deleted.
    // The Android implementation uses AbstractWindowedCursor, so it doesn't
    // guarantee that the cursor caches all the rows selected.
    // So we return a new Cursor using the previous query, created on the table
    // after the deletion and positioned to the row before the deleted one.
    public Cursor delete(Cursor cr) {
        int pos = cr.getPosition();
        StringBuilder whereClause = new StringBuilder();
        ArrayList<String> whereArgs = new ArrayList<String>();
        int columns = cr.getColumnCount();
        String table =  getTableName(cr);
        boolean first = true;
        int i;
        for ( i = 0; i < columns; ++i) {
            if (first) { first = false; } else {
                whereClause.append(" AND ");
            }
            String columnName = cr.getColumnName(i);
            whereClause.append(columnName);
            whereClause.append(" IS ?");
            whereArgs.add(cr.getString(i));
        }
        String parts[] = m_saveQuerySql.split("\\s+");
        for (i  = 4; i < parts.length; ++i) {
            if (   (parts[i].compareTo("IS") == 0)
                && (cr.getColumnIndex(parts[i - 1]) < 0)) {
                // This can't be the first one because that would mean no
                // columns were returned by the query.
                whereClause.append(" AND ");
                whereClause.append(parts[i - 1]);
                whereClause.append(" IS ?");
                whereArgs.add(parts[++i]);
            }
        }
        // m_saveQueryArgs is only used for its type here.
        delete (table, whereClause.toString(), whereArgs.toArray(m_saveQueryArgs));
        cr = rawQueryNoSave(m_saveQuerySql, m_saveQueryArgs);
        cr.moveToPosition(pos - 1);
        return cr;
    }

    public long getLong(Cursor cursor, int columnIndex)
        throws NumberFormatException, IllegalStateException
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
        throws NumberFormatException, IllegalStateException
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
    private static final int WRITECOUNT = 1000;
    public void tryVacuum ()  {
        long count = 0;
        try {
            Cursor cursor = m_db.rawQuery(
                "SELECT VACUUM_COUNT FROM VACUUMDATA", null);
            if (cursor.moveToFirst()) {
                count = getUnsignedLong(cursor, 0);
                if (count > WRITECOUNT) {
                    // Time to vacuum, do so and reset count
                    m_db.execSQL("VACUUM");
                    count = 0;
                }
                // fall through to update count
            }
            else {
                new MyLog(m_context,
                    m_context.getString(R.string.norows));
                m_db.execSQL("INSERT INTO VACUUMDATA ( VACUUM_COUNT ) VALUES ( "
                             + (count + 1) + " )");
                return;
            }
        } catch (SQLiteException e) {
            handleException(0, e);
        } catch (NumberFormatException ee) {
            new MyLog(m_context,m_context.getString(R.string.value)
                + ee.getMessage().replace("Bad number ", "")
                + m_context.getString(R.string.countnoninteger));
            // fall through to replace invalid count with 1
        }
        try {
            m_db.execSQL("UPDATE VACUUMDATA SET VACUUM_COUNT = " + (count + 1));
            return;
        } catch (SQLiteException e) {
            handleException(0, e);
        }
    }

    public sqlite (Context context) {
        m_context = context;
        m_db = null;
        m_written = false;
        m_saveQuerySql = null;
        m_saveQueryArgs = null;
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
