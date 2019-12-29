/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

/* This is basically a wrapper around an SQLiteCursor.
 * However it keeps around its database, table name, and the query
 * which built it in order to ease error recovery and properly handle
 * row deletion, and it leaves out some methods which I don't use.
 * In particular since all my tables are small and have only a few columns,
 * I only do SELECT *. This avoids having to keep track separately of columns
 * in the table itself and the cursor.
 * We assume that table names and column names don't contain any nonalphanumeric
 * characters apart from _ and are chosen so that they can't be confused with
 * reserved words. No assumption is made about WHERE arguments.
 */

/* It is permissible to open the database more than once and typically if both
 * foreground and background tasks are active they will each have it open.
 * However to save time and memory I allow an SQLtable to be created from
 * another one using the same open database. It isn't obligatory to do this
 * but I expect to do so within a single class instance. or static method.
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class SQLtable extends Object {
    private Context m_context;
    private String m_tableName;
    private String m_query;
    private String m_args[];
    private SQLiteDatabase m_database;
    private SQLtable m_parent;
    private ArrayList<SQLtable> m_children;

    // m_cursor uses lazy evaluation: we don't actually do the query to create it
    // until some method is called which needs it. This is done so that we can
    // keep cursors up to date with any changes that we make to the table.
    private Cursor m_cursor;
    private int m_position;

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

    // Here we describe all the tables in our database

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
                // Instance_id's are unique, so we don't need the calendar id.
                + " INSTANCE_ID INTEGER,"
                // This is the start time of the event in wall clock time.
                // It is used to recalculate the UTC start time (DTSTART)
                // from the time zone offset when it changes.
                + " START_WALLTIME_MILLIS INTEGER,"
                // This is the end time of the event in wall clock time.,
                // It is used to recalculate DTEND for non-recurring events.
                // For recurring events Android uses DURATION instead.
                + " END_WALLTIME_MILLIS INTEGER )";
        }
        else if (s.equals("ACTIVEINSTANCES")) {
            // This table keeps track of active event instances.
            return "CREATE TABLE ACTIVEINSTANCES ("
                // This is the class name of the event.
                // An instance has a separate entry in this table
                // for each class in which it is active.
                // This is because the start or end time offset of
                // events can be different for different classes.
                // If the class no longer exists, we delete the record.
                + " ACTIVE_CLASS_NAME TEXT,"
                // This is 1 if this is an immediate event, and 0 otherwise.
                // If it is 1, ACTIVE_INSTANCE_ID is meaningless.
                + " ACTIVE_IMMEDIATE INTEGER,"
                // This is the _ID of a non-immediate active instance.
                // If the instance no longer exists, we try to do the end actions.
                // Some end actions require information from the event and
                // so cannot be done if the instance has been deleted.
                + " ACTIVE_INSTANCE_ID INTEGER,"
                // This is the state of an active instance:
                // the states are defined below.
                + " ACTIVE_STATE INTEGER,"
                // This is the time when we next need to wake up for this instance.
                + " ACTIVE_NEXT_ALARM INTEGER,"
                // This is the target step count for this event,
                // or zero if it has no step count wait active.
                + " ACTIVE_STEPS_TARGET"
                + " )";

        }
        // If this occurs. it's a programming error
        String big = "getCreator(" + s + m_context.getString(R.string.unknowntable);
        fatal(m_context.getString(R.string.badtable), big);
        return null; // unreachable
    }

    public static final int ACTIVE_CLASS_NAME = 0;
    public static final int ACTIVE_IMMEDIATE = 1;
    public static final int ACTIVE_INSTANCE_ID = 2;
    public static final int ACTIVE_STATE = 3;
    public static final int ACTIVE_NEXT_ALARM = 4;
    public static final int ACTIVE_STEPS_TARGET = 5;

    // Possible values for ACTIVE_STATE:-
    // This state is only here for completeness.
    // It should never occur because inactive instances get deleted from the table.
    public static final int NOT_ACTIVE = 0;
    // The instance has reached its start time, but is waiting for other conditions
    // to be satisfied before it can become fully active.
    public static final int ACTIVE_START_WAITING = 1;
    // The instance was inactive before and has become fully active.
    // This state exists only for one pass through the table.
    // Currently it is never actually set in the database because we always fall
    // through into it and then immediately set ACTIVE_START_SENDING or ACTIVE_STARTED.
    public static final int ACTIVE_STARTING = 2;
    // The instance has completed some of the class's start actions,
    // but is waiting for some resource (such as an internet connection) to
    // become available before it can complete others.
    public static final int ACTIVE_START_SENDING = 3;
    // The instance has completed all the class's start actions and is now fully active.
    public static final int ACTIVE_STARTED = 4;
    // The instance has reached its end time, but is waiting for other conditions
    // to be satisfied before it can become inactive.
    public static final int ACTIVE_END_WAITING = 5;
    // The instance was active and has become inactive.
    // This state exists only for one pass through the table.
    // Currently it is never actually set in the database because we always fall
    // through into it and then immediately set ACTIVE_END_SENDING or NOT_ACTIVE.
    public static final int ACTIVE_ENDING = 6;
    // The instance has completed some of the class's end actions,
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

    private Cursor cursor() {
        if (m_cursor == null) {
            for (int i = 10; ; --i) {
                try {
                    m_cursor = m_database.rawQuery(m_query, m_args);
                    m_cursor.moveToPosition(m_position);
                    break;
                } catch (SQLiteException e) {
                    handleException(i, e, m_tableName);
                }
            }
        }
        return m_cursor;
    }

    public String rowVacToString() {
        return "(" + cursor().getString(0) + ")";
    }

    public String rowFloatToString() {
        StringBuilder builder =
            new StringBuilder("(event ID " + cursor().getString(0));
        builder.append(", ");
        DateFormat df = DateFormat.getDateTimeInstance();
        try {
            builder.append(df.format(getUnsignedLong(1)));
        } catch (NumberFormatException e) {
            builder.append("?);");
            builder.append(cursor().getString(1));
            builder.append("?);");
        }
        builder.append(" to ");
        try {
            builder.append(df.format(getUnsignedLong(2)));
        } catch (NumberFormatException e) {
            builder.append("?);");
            builder.append(cursor().getString(2));
            builder.append("?);");
        }
        builder.append(")");
        return builder.toString();
    }

    public String rowActiveToString() {
        StringBuilder builder =
            new StringBuilder("(");
        builder.append(cursor().getString(ACTIVE_CLASS_NAME));
        builder.append(", ");
        if (cursor().getString(ACTIVE_IMMEDIATE) != "0") {
            builder.append(m_context.getString(R.string.immediate));
        }
        else
        {
            builder.append("instance ID ");

            builder.append(cursor().getString(ACTIVE_INSTANCE_ID));
        }
        builder.append(", ");
        try {
            int n = (int)getUnsignedLong(ACTIVE_STATE);
            builder.append(m_context.getString(R.string.state));
            builder.append(getActiveStateName(n));
        } catch (NumberFormatException e) {
            builder.append(m_context.getString(R.string.badstate));
            builder.append(cursor().getString(ACTIVE_STATE));
        }
        builder.append(", ");
        builder.append(m_context.getString(R.string.alarm));
        try {
            DateFormat df = DateFormat.getDateTimeInstance();
            builder.append(df.format(getUnsignedLong(ACTIVE_NEXT_ALARM)));
        } catch (NumberFormatException e) {
            builder.append("?");
            builder.append(cursor().getString(ACTIVE_NEXT_ALARM));
            builder.append("?");
        }
        builder.append(")");
        return builder.toString();
    }

    // Return a string describing the row pointed to by our Cursor.
    // Note that this is used for error messages, so it has to work
    // if the row contains bad data. However it doesn't have to work if the
    // table does not contain the right columns, since we deal with that
    // elsewhere.
    // The versions above can be used if we know which table it is from.
    public String rowToString() {
        if (m_tableName.equals("VACUUMDATA")) {
            return rowVacToString();
        }
        else if (m_tableName.equals("FLOATINGEVENTS")) {
            return rowFloatToString();
        }
        else if (m_tableName.equals("ACTIVEINSTANCES")) {
            return rowActiveToString();
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
    private void handleException (int i, SQLiteException e, String tableName) {
        String s = e.getMessage();
        if (s.startsWith("no such table:")) {
            new MyLog(m_context,
                m_context.getString(R.string.table) + tableName +
                    m_context.getString(R.string.creating));
            try {
                m_database.execSQL(getCreator(tableName));
                return;
            } catch (SQLiteException ee) {
                e = ee;
            }
        }
        else if (s.startsWith("no such column:")) {
            String columnName = s.split(" ")[3];
            new MyLog(m_context,
                m_context.getString(R.string.creatingnew) + tableName,
                m_context.getString(R.string.column) + columnName +
                    m_context.getString(R.string.dropping) + tableName + ".");
            try {
                m_database.execSQL("DROP TABLE " + tableName);
                m_database.execSQL(getCreator(tableName));
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
        // If all recovery attempts fail, we give up.
        String small = m_context.getString(R.string.databaseerror);
        String big = DataStore.getDatabaseFile(m_context) +
            m_context.getString(R.string.unrecoverable)
            + e.getCause().toString() + e.getLocalizedMessage();
        fatal(small, big);
    }

    // Open the (fixed) database
    private void open() {
        String fileName = DataStore.getDatabaseFile(m_context);
        if (fileName != null) {
            for (int i = 10; ; --i) {
                try {
                    m_database = SQLiteDatabase.openOrCreateDatabase(
                        fileName, null, null);
                } catch (SQLiteException e) {
                    handleException(i, e, m_tableName);
                }
            }
        }
        m_parent = null;
        m_children = new ArrayList<SQLtable>();
    }

    private void makeQuery(String tableName, String where, String order) {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT * FROM " + m_tableName);
        if (where != null) {
            String w = " WHERE " + where;
            builder.append(w);
        }
        if (order != null) {
            builder.append(" ORDER BY " + order);
        }
        m_query = builder.toString();
    }

    // After we do an insert, update, or delete,
    // all cursors on the same table are invalid, so we close them.
    // Any cursor which is referenced later will be recreated.
    private void invalidate(SQLtable top, String tableName) {
        if (top.m_tableName == tableName) {
            if (top.m_cursor != null) {
                top.m_cursor.close();
                top.m_cursor = null;
            }
        }
        for (SQLtable child : top.m_children) {
            invalidate(child, tableName);
        }
    }
    private void invalidate(String tableName) {
        SQLtable top = this;
        while (top.m_parent != null) { top = top.m_parent; }
        invalidate(top, tableName);
    }

    // Constructor, open the database and query the whole table
    public SQLtable(Context context, String tableName) {
        m_context = context;
        m_tableName = tableName;
        m_query = "SELECT * FROM " + m_tableName;
        m_args = null;
        open();
        m_cursor = null;
        m_position = -1;
    }

    // Constructor, open the database and query the whole table with an
    // (optional) WHERE clause excluding the WHERE and some (optional) arguments
    // and an optional ORDER BY clause excluding the ORDER BY.
    // Unused optional items are nulls.
    public SQLtable(Context context, String tableName,
                    String where, String args[], String order) {
        m_context = context;
        m_tableName = tableName;
        makeQuery(tableName, where, order);
        m_args = args;
        open();
        m_cursor = null;
        m_position = -1;
    }

    public SQLtable(Context context, String tableName,
                    String where, ArrayList<String> args, String order) {
        m_context = context;
        m_tableName = tableName;
        makeQuery(tableName, where, order);
        m_args = args.toArray(m_args);
        open();
        m_cursor = null;
        m_position = -1;
    }

    // Constructor, query another table in the same database
    public SQLtable(SQLtable parent, String tableName) {
        m_context = parent.m_context;
        m_tableName = tableName;
        m_query = "SELECT * FROM " + m_tableName;
        m_args = null;
        m_database = m_parent.m_database;
        m_parent = parent;
        m_parent.m_children.add(this);
        m_cursor = null;
        m_position = -1;
    }

    // Constructor, query another table in the same database with an
    // (optional) WHERE clause excluding the WHERE and some (optional) arguments
    // and an optional ORDER BY clause excluding the ORDER BY.
    // Unused optional items are nulls.
    public SQLtable(SQLtable parent, String tableName,
                    String where, String[] args, String order) {
        m_context = parent.m_context;
        m_tableName = tableName;
        makeQuery(tableName, where, order);
        m_args = args;
        m_database = parent.m_database;
        m_parent = parent;
        m_parent.m_children.add(this);
        m_cursor = null;
        m_position = -1;
    }

    // Constructor, query another table in the same database with an
    // (optional) WHERE clause excluding the WHERE and some (optional) arguments
    // and an optional ORDER BY clause excluding the ORDER BY.
    // Unused optional items are nulls.
    public SQLtable(SQLtable parent, String tableName,
                    String where, ArrayList<String> args, String order) {
        m_context = parent.m_context;
        m_tableName = tableName;
        makeQuery(tableName, where, order);
        m_args = args.toArray(m_args);
        m_database = parent.m_database;
        m_parent = parent;
        m_parent.m_children.add(this);
        m_cursor = null;
        m_position = -1;
    }

    public boolean isEmpty() {
        return cursor().getCount() == 0;
    }

    // These methods basically wrap the corresponding methods of Cursor
    // but with some error recovery. I only wrap the ones that I use.

    public boolean moveToNext() {
        boolean result = cursor().moveToNext();
        if (result) { m_position++; }
        return result;
    }

    // IllegalStateException is incorrectly thrown if the columnIndex
    // is out of range. This is a known Android bug.
    // It can only be a programming error so I report a fatal error if it
    // happens.
    public String getString(int columnIndex) {
        try {
            return cursor().getString(columnIndex);
        } catch (IllegalStateException e) {
            String small = m_context.getString(
                R.string.badcolnum, String.valueOf(columnIndex));
            String big = m_context.getString(R.string.getstring, m_tableName);
            fatal(small, big);
        }
        return null; //unreachable
    }

    public long getUnsignedLong(int columnIndex)
        throws NumberFormatException
    {
        try {
            String s = getString(columnIndex);
            if ((s != null) && s.matches("^[0-9]+$")) {
                return Long.parseLong(s);
            }
            else
            {
                throw(new NumberFormatException("Bad number " + s));
            }
        } catch (IllegalStateException e) {
            String small = m_context.getString(
                R.string.badcolnum, String.valueOf(columnIndex));
            String big = m_context.getString(R.string.getunsignedlong, m_tableName);
            fatal(small, big);
        }
        return 0; //unreachable
    }

    // The Android implementation doesn't guarantee that a cursor on this
    // table will reflect the insertion.
    // So we call invalidate() to destroy all the cursors on this table owned
    // by this database connection. Any cursor which is subsequently referenced
    // will be recreated.
    public long insert(ContentValues values)  {
        for (int i = 10; ; --i) {
            try {
                long result = m_database.insert(m_tableName, null, values);
                invalidate(m_tableName);
                return result;
            } catch (SQLiteException e) {
                handleException(i, e, m_tableName);
            }
        }
    }

    // This updates the row that our cursor is looking at.
    public int update(ContentValues cv) {
        StringBuilder whereClause = new StringBuilder();
        ArrayList<String> whereArgs = new ArrayList<String>();
        int columns = cursor().getColumnCount();
        boolean first = true;
        int i;
        for ( i = 0; i < columns; ++i) {
            if (first) { first = false; } else {
                whereClause.append(" AND ");
            }
            // It's safe to use m_cursor here because we called cursor() above.
            whereClause.append(m_cursor.getColumnName(i));
            whereClause.append(" IS ?");
            whereArgs.add(m_cursor.getString(i));
        }
        for (i = 10; ; --i) {
            try {
                // m_args is only used for its type here.
                int result = m_database.update(m_tableName, cv,
                    whereClause.toString(), whereArgs.toArray(m_args));
                invalidate(m_tableName);
                return result;
            } catch (SQLiteException e) {
                // Nonexistent table or column is almost certainly a programming error:
                // we should only ever delete records that we have just found.
                // However it's possible that some other process deleted the
                // table while we were looking at it, and maybe created it
                // with different columns, so we handle these in the normal way.
                handleException(i, e, m_tableName);
            }
        }
    }

    public int update(String columnName, String value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return update(cv);
    }

    public int update(String columnName, long value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return update(cv);
    }

    // This deletes the row that our cursor is looking at.
    // The Android implementation uses AbstractWindowedCursor, so it doesn't
    // guarantee that the cursor caches all the rows selected.
    // So we make a new Cursor using the previous query, created on the table
    // after the deletion and positioned to the row before the deleted one
    // so that moveToNext() will do as expected and move to the row after
    // the deleted one.
    public int delete() {
        StringBuilder whereClause = new StringBuilder();
        ArrayList<String> whereArgs = new ArrayList<String>();
        int columns = cursor().getColumnCount();
        boolean first = true;
        int i;
        for ( i = 0; i < columns; ++i) {
            if (first) { first = false; } else {
                whereClause.append(" AND ");
            }
            // It's safe to use m_cursor here because we called cursor() above.
            whereClause.append(m_cursor.getColumnName(i));
            whereClause.append(" IS ?");
            whereArgs.add(m_cursor.getString(i));
        }
        for (i = 10; ; --i) {
            try {
                int result = m_database.delete(
                    m_tableName, whereClause.toString(), whereArgs.toArray(m_args));
                invalidate(m_tableName);
                // Move back to before deleted record so that moveToNext() will
                // get to the record after it.
                if (m_position >= 0) { --m_position; }
                return result;
            } catch (SQLiteException e) {
                // Nonexistent table or column is almost certainly a programming error:
                // we should only ever delete records that we have just found.
                // However it's possible that some other process deleted the
                // table while we were looking at it, and maybe created it
                // with different columns, so we handle these in the normal way.
                handleException(i, e, m_tableName);
            }
        }
    }

    // same as above, but with an error message
    public void deleteBad() {
        String small = m_context.getString(R.string.deletinginvalid);
        String big = small + rowToString() +
            m_context.getString(R.string.fromtable) + m_tableName;
        new MyLog(m_context, big ,small);
        delete();
    }

    // Vacuuming needs to be done from time to time, but it isn't urgent.
    private static final int WRITECOUNT = 1000;
    public void tryVacuum()  {
        long count = 0;
        try {
            Cursor cr = m_database.rawQuery(
                "SELECT VACUUM_COUNT FROM VACUUMDATA", null);
            if (cr.moveToFirst()) {
                String s = cr.getString(0);
                if ((s != null) && s.matches("^[0-9]+$")) {
                    count = Long.parseLong(s);
                    if (count > WRITECOUNT) {
                        // Time to vacuum, do so and reset count
                        m_database.execSQL("VACUUM");
                        count = 0;
                    }
                    // fall through to update count
                }
                else
                {
                    new MyLog(m_context, m_context.getString(R.string.countnoninteger, s));
                    // fall through to replace invalid count with 1
                }
            }
            else
            {
                // No row in VACUUMDATA yet, make one
                new MyLog(m_context,
                    m_context.getString(R.string.norows));
                m_database.execSQL("INSERT INTO VACUUMDATA ( VACUUM_COUNT ) VALUES ( "
                    + (count + 1) + " )");
                return;
            }
        } catch (SQLiteException e) {
            handleException(1, e, "VACUUMDATA");
            invalidate("VACUUMDATA");
        }
        try {
            m_database.execSQL("UPDATE VACUUMDATA SET VACUUM_COUNT = " + (count + 1));
            return;
        } catch (SQLiteException e) {
            handleException(1, e, "VACUUMDATA");
            invalidate("VACUUMDATA");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("calendartrigger.utilities.SQLtable(");
        sb.append(m_tableName);
        sb.append(", ");
        if (m_args == null) {
            sb.append(m_query);
        }
        else
        {
            String q = m_query;
            for (String s : m_args) {
                q = q.replaceFirst("\\?",
                    "'" + s.replace("'", "''") + "'");
            }
            sb.append(q);
        }
        sb.append(")");
        return sb.toString();
    }

    public void close() {
        if (m_cursor != null) {
            m_cursor.close();
            m_cursor = null;
            m_position = -1;
        }
    }

    // Arrange to close the database when we have no more tables open on it.
    @Override
    protected void finalize() throws Throwable {
        if (m_cursor != null) { m_cursor.close(); }
        tryVacuum();
        if (m_children.size() == 0) {
            if (m_parent == null) {
                m_database.close();
            }
            else
            {
                m_parent.m_children.remove(this);
            }
        }
        else
        {
            // oops, closing a parent before its child(ren)
            // Relink so as to maintain the references of the database.
            SQLtable firstChild = m_children.get(0);
            firstChild.m_parent = m_parent;
            m_children.remove(0);
            while (m_children.size() > 0) {
                SQLtable child = m_children.get(0);
                m_children.remove(child);
                child.m_parent = firstChild;
                firstChild.m_children.add(child);
            }
            if (m_parent != null) {
                m_parent.m_children.remove(this);
                m_parent.m_children.add(firstChild);
            }
        }
        super.finalize();
    }
}
