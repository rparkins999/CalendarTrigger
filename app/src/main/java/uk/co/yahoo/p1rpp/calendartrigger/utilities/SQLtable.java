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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.text.DateFormat;
import java.util.ArrayList;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class SQLtable {
    private Context m_context;
    private String m_tableName;
    private String m_query;
    private String[] m_args;
    private SQLiteDatabase m_database;
    private SQLtable m_parent;
    private ArrayList<SQLtable> m_children;

    // m_cursor uses lazy evaluation: we don't actually do the query to create it
    // until some method is called which needs it. This is done so that we can
    // keep cursors up to date with any changes that we make to the table.
    private Cursor m_cursor;
    private int m_position;

    // Here we describe all the tables in our database

    // All table names are concatenated words so that they shouldn't be reserved words
    // All column names contain "_" so that they shouldn't be reserved words
    private String getCreator(String s) {
        switch (s) {
            case "VACUUMDATA":
                return "CREATE TABLE VACUUMDATA (" +
                    // This counts the number of writes to the database.
                    // When it reaches 1000, we try a VACUUM.
                    // If it succeeds, we reset the count to 1.
                    "VACUUM_COUNT INTEGER)";
            case "FLOATINGEVENTS":
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
                    + " END_WALLTIME_MILLIS INTEGER )";
            case "ACTIVEINSTANCES":
                // This table keeps track of active event instances.
                return "CREATE TABLE ACTIVEINSTANCES ("
                    // This is the class name of the event.
                    // An instance has a separate entry in this table
                    // for each class in which it is active.
                    // This is because the start or end time offset of
                    // events can be different for different classes.
                    // If the class no longer exists, we delete the record.
                    + " ACTIVE_CLASS_NAME TEXT,"
                    // This is the _ID of a non-immediate active instance.
                    // If the instance no longer exists, we try to do the end actions.
                    + " ACTIVE_INSTANCE_ID INTEGER,"
                    // This is the event name.
                    // It is "immediate event" for an immediate event
                    // or "deleted event" for a deleted instance
                    // We need it here because we can't search the instance table for an
                    // instance ID.
                    + " ACTIVE_EVENT_NAME STRING,"
                    // This is the event location. It is "" for an immediate event.
                    // We need it here because we can't search the instance table for an
                    // instance ID.
                    + " ACTIVE_LOCATION STRING,"
                    // This is the event description. It is "" for an immediate event.
                    // We need it here because we can't search the instance table for an
                    // instance ID.
                    + " ACTIVE_DESCRIPTION STRING,"
                    // This is the state of an active instance:
                    // the states are defined below.
                    + " ACTIVE_STATE INTEGER,"
                    // This is another state variable which records immeditae or deleted
                    // events: possible values are defined below.
                    + " ACTIVE_LIVE INTEGER,"
                    // This is the end time of this instance.
                    + " ACTIVE_END_TIME INTEBER,"
                    // This is the time when we next need to wake up for this instance.
                    + " ACTIVE_NEXT_ALARM INTEGER,"
                    // This is the target step count for this event,
                    // or zero if it has no step count wait active.
                    + " ACTIVE_STEPS_TARGET"
                    + " )";

        }
        // If this occurs. it's a programming error
        String big = "getCreator(" + s + m_context.getString(R.string.unknowntable);
        new MyLog(m_context, true, m_context.getString(R.string.badtable), big);
        return null; // unreachable
    }

    // Some of these aren't used (yet) but defined for completeness
    public static final int ACTIVE_CLASS_NAME = 0;
    public static final int ACTIVE_INSTANCE_ID = 1;
    public static final int ACTIVE_EVENT_NAME = 2;
    public static final int ACTIVE_LOCATION = 3;
    public static final int ACTIVE_DESCRIPTION = 4;
    public static final int ACTIVE_STATE = 5;
    public static final int ACTIVE_LIVE = 6;
    public static final int ACTIVE_END_TIME = 7;
    public static final int ACTIVE_NEXT_ALARM = 8;
    public static final int ACTIVE_STEPS_TARGET = 9;

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
            case ACTIVE_END_WAITING: return "ACTIVE_END_WAITING";
            case ACTIVE_ENDING: return "ACTIVE_ENDING";
            case ACTIVE_END_SENDING: return "ACTIVE_END_SENDING";
            default: return "Unknown state " + n;
        }
    }

    // Possible values for ACTIVE_LIVE:
    // Normal instance, not immediate or deleted.
    public static final int ACTIVE_LIVE_NORMAL = 0;
    // Deleted instance. All normal instances are set to deleted at the beginning
    // of CalendarProvider.fillActive. Those that still exist will be set back to
    // NORMAL.
    public static final int ACTIVE_DELETED = 1;
    // Immediate event: this has no instance ID or name or location or description.
    public static final int ACTIVE_IMMEDIATE = 2;

    private String getActiveLiveName(int n) {
        switch (n) {
            case ACTIVE_LIVE_NORMAL: return "ACTIVE_LIVE_NORMAL";
            case ACTIVE_DELETED: return "ACTIVE_DELETED";
            case ACTIVE_IMMEDIATE: return "ACTIVE_IMMEDIATE";
            default: return "Unknown live value " + n;
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

    private String rowVacToString() {
        return "(" + cursor().getString(0) + ")";
    }

    private String rowFloatToString() {
        try {
            StringBuilder builder =
                new StringBuilder("(event ID " + getString("EVENT_ID"));
            builder.append(", ");
            DateFormat df = DateFormat.getDateTimeInstance();
            try {
                builder.append(df.format(
                    getUnsignedLong("START_WALLTIME_MILLIS")));
            } catch (NumberFormatException ignore) {
                builder.append("?);");
                builder.append(getString("START_WALLTIME_MILLIS"));
                builder.append("?);");
            }
            builder.append(" to ");
            try {
                builder.append(df.format(getUnsignedLong(
                    "END_WALLTIME_MILLIS")));
            } catch (NumberFormatException ignore) {
                builder.append("?);");
                builder.append(getString("END_WALLTIME_MILLIS"));
                builder.append("?);");
            }
            builder.append(")");
            return builder.toString();
        } catch (NoColumnException ignore) {
            // This should never happen.
            return "";
        }
    }

    private String rowActiveToString() {
        try {
            StringBuilder builder =
                new StringBuilder("(");
            builder.append(getString("ACTIVE_CLASS_NAME"));
            builder.append(", ID ");
            builder.append(getString("ACTIVE_INSTANCE_ID"));
            builder.append(", ");
            builder.append(getString("ACTIVE_EVENT_NAME"));
            builder.append(", ");
            builder.append(getString("ACTIVE_LOCATION"));
            builder.append(", ");
            builder.append(getString("ACTIVE_DESCRIPTION"));
            try {
                int n = (int)getUnsignedLong("ACTIVE_STATE");
                builder.append(m_context.getString(R.string.state));
                builder.append(getActiveStateName(n));
            } catch (NumberFormatException ignore) {
                builder.append(m_context.getString(R.string.badstate));
                builder.append(getString("ACTIVE_STATE"));
            }
            builder.append(", ");
            try {
                int n = (int)getUnsignedLong("ACTIVE_LIVE");
                builder.append(getActiveLiveName(n));
            } catch (NumberFormatException ignore) {
                builder.append(m_context.getString(R.string.badlive));
                builder.append(getString("ACTIVE_LIVE"));
            }
            builder.append(m_context.getString(R.string.alarm));
            try {
                DateFormat df = DateFormat.getDateTimeInstance();
                builder.append(df.format(getUnsignedLong("ACTIVE_NEXT_ALARM")));
            } catch (NumberFormatException ignore) {
                builder.append("?");
                builder.append(getString("ACTIVE_NEXT_ALARM"));
                builder.append("?");
            }
            builder.append(m_context.getString(R.string.targetsteps));
            builder.append(getString("ACTIVE_STEPS_TARGET"));
            builder.append(")");
            return builder.toString();
        } catch (NoColumnException ignore) {
            // This should never happen.
            return "";
        }
    }

    // Return a string describing the row pointed to by our Cursor.
    // Note that this is used for error messages, so it has to work
    // if the row contains bad data. However it doesn't have to work if the
    // table does not contain the right columns, since we deal with that
    // elsewhere.
    // The versions above can be used if we know which table it is from.
    public String rowToString() {
        switch (m_tableName) {
            case "VACUUMDATA":
                return rowVacToString();
            case "FLOATINGEVENTS":
                return rowFloatToString();
            case "ACTIVEINSTANCES":
                return rowActiveToString();
            default:
                // If this occurs. it's a programming error
                String small = m_context.getString(R.string.badcursor);
                String big = m_context.getString(R.string.badrowtocolumn);
                new MyLog(m_context, true, small, big);
                return null; // unreachable
        }
    }

    // This normally returns, having patched up the database if necessary
    // so that the caller can try again if it wants to.
    // Unrecoverable problems result in a fatal error.
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
                m_context.getString(R.string.bigcreatingnew,
                    columnName, tableName));
            try {
                m_database.execSQL("DROP TABLE " + tableName);
                m_database.execSQL(getCreator(tableName));
                m_cursor = null;
                m_position = -1;
                return;
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
                    } catch (InterruptedException ignore) { return; }
                    return;
                }
            }
        }
        // If all recovery attempts fail, we give up.
        String small = m_context.getString(R.string.databaseerror);
        String big = DataStore.getDatabaseFile(m_context) +
            m_context.getString(R.string.unrecoverable)
            + e.getCause().toString() + e.getLocalizedMessage();
        new MyLog(m_context, true, small, big);
    }

    // Open the (fixed) database
    private void open() {
        String fileName = DataStore.getDatabaseFile(m_context);
        if (fileName != null) {
            for (int i = 10; ; --i) {
                try {
                    m_database = SQLiteDatabase.openOrCreateDatabase(
                        fileName, null, null);
                    // Useful for debugging
                    m_database.disableWriteAheadLogging();

                    m_parent = null;
                    m_children = new ArrayList<>();
                    return;
                } catch (SQLiteException e) {
                    handleException(i, e, m_tableName);
                }
            }
        }
    }

    private void makeQuery(String where, String order) {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT * FROM ").append(m_tableName);
        if (where != null) {
            String w = " WHERE " + where;
            builder.append(w);
        }
        if (order != null) {
            builder.append(" ORDER BY ").append(order);
        }
        m_query = builder.toString();
    }

    // After we do an insert, update, or delete,
    // all cursors on the same table are invalid, so we close them.
    // Any cursor which is referenced later will be recreated.
    private void invalidate(SQLtable top, String tableName) {
        if (top.m_tableName.equals(tableName)) {
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
        StringBuilder sb = new StringBuilder("SQLtable, no parent, tableName = ")
            .append(tableName);
        new MyLog(context, sb.toString());
        m_context = context;
        m_tableName = tableName;
        m_query = "SELECT * FROM " + m_tableName;
        m_args = new String[]{};
        open();
        m_cursor = null;
        m_position = -1;
        m_parent = null;
    }

    // Constructor, open the database and query the whole table with an
    // (optional) WHERE clause excluding the WHERE and some (optional) arguments
    // and an optional ORDER BY clause excluding the ORDER BY.
    // Unused optional items are nulls.
    // Not used yet but I may need it later.
    @SuppressWarnings("unused")
    public SQLtable(Context context, String tableName,
                    String where, String[] args, String order) {
        StringBuilder sb = new StringBuilder("SQLtable, no parent, tableName = ")
            .append(tableName);
        new MyLog(context, sb.toString());
        m_context = context;
        m_tableName = tableName;
        makeQuery(where, order);
        m_args = args != null ? args : new String[]{};
        open();
        m_cursor = null;
        m_position = -1;
        m_parent = null;
    }

    // Constructor, open the database and query the whole table with an
    // (optional) WHERE clause excluding the WHERE and some (optional) arguments
    // and an optional ORDER BY clause excluding the ORDER BY.
    // Unused optional items are nulls.
    // Not used yet but I may need it later.
    @SuppressWarnings("unused")
    public SQLtable(Context context, String tableName,
                    String where, ArrayList<String> args, String order) {
        StringBuilder sb = new StringBuilder("SQLtable, no parent, tableName = ")
            .append(tableName);
        new MyLog(context, sb.toString());
        m_context = context;
        m_tableName = tableName;
        makeQuery(where, order);
        m_args = new String[]{};
        if (args != null) { m_args = args.toArray(m_args); }
        open();
        m_cursor = null;
        m_position = -1;
        m_parent = null;
    }

    // Constructor, query another table in the same database
    public SQLtable(SQLtable parent, String tableName) {
        StringBuilder sb = new StringBuilder("SQLtable, parent = ")
            .append(parent.m_tableName)
            .append(", tableName = ")
            .append(tableName);
        new MyLog(parent.m_context, sb.toString());
        m_context = parent.m_context;
        m_tableName = tableName;
        m_query = "SELECT * FROM " + m_tableName;
        m_args = new String[]{};
        m_database = m_parent.m_database;
        m_parent = parent;
        m_parent.m_children.add(this);
        m_children = new ArrayList<>();
        m_cursor = null;
        m_position = -1;
    }

    // Constructor, query another table in the same database with an
    // (optional) WHERE clause excluding the WHERE and some (optional) arguments
    // and an optional ORDER BY clause excluding the ORDER BY.
    // Unused optional items are nulls.
    public SQLtable(SQLtable parent, String tableName,
                    String where, String[] args, String order) {
        new MyLog(parent.m_context,
            "SQLtable, parent = " + parent.m_tableName + ", tableName = " + tableName);
        m_context = parent.m_context;
        m_tableName = tableName;
        makeQuery(where, order);
        m_args = args != null ? args : new String[]{};
        m_database = parent.m_database;
        m_parent = parent;
        m_parent.m_children.add(this);
        m_children = new ArrayList<>();
        m_cursor = null;
        m_position = -1;
    }

    // Constructor, query another table in the same database with an
    // (optional) WHERE clause excluding the WHERE and some (optional) arguments
    // and an optional ORDER BY clause excluding the ORDER BY.
    // Unused optional items are nulls.
    // Not used yet but I may need it later.
    @SuppressWarnings("unused")
    public SQLtable(SQLtable parent, String tableName,
                    String where, ArrayList<String> args, String order) {
        StringBuilder sb = new StringBuilder("SQLtable, parent = ")
            .append(parent.m_tableName)
            .append(", tableName = ")
            .append(tableName);
        new MyLog(parent.m_context, sb.toString());
        m_context = parent.m_context;
        m_tableName = tableName;
        makeQuery(where, order);
        m_args = new String[]{};
        if (args != null) { m_args = args.toArray(m_args); }
        m_database = parent.m_database;
        m_parent = parent;
        m_parent.m_children.add(this);
        m_children = new ArrayList<>();
        m_cursor = null;
        m_position = -1;
    }

    public boolean isEmpty() {
        return cursor().getCount() == 0;
    }

    // These methods basically wrap the corresponding methods of Cursor
    // but with some error recovery. I only wrap the ones that I use.
    // Unused return values are for compatibility with the wrapped methods.

    public boolean moveToNext() {
        boolean result = cursor().moveToNext();
        m_position = m_cursor.getPosition();
        new MyLog(m_context,
            " moveToNext() set m_position on table "
            + m_tableName + " to " + m_position);
        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean moveToPosition(int position) {
        boolean result = cursor().moveToPosition(position);
        if (result) {
            m_position = position;
            new MyLog(m_context,
                " moveToPosition() set m_position on table "
                    + m_tableName + " to " + m_position);
        }
        return result;
    }

    private String getString(String columnName, String caller)
    throws NoColumnException {
        for (int i = 10; ; --i) {
            int columnIndex = cursor().getColumnIndex(columnName);
            if (columnIndex < 0) {
                // The named column does not exist
                new MyLog(m_context,
                    m_context.getString(R.string.creatingnew) + m_tableName,
                    m_context.getString(R.string.bigcreatingnew,
                        columnName, m_tableName));
                try {
                    m_database.execSQL("DROP TABLE " + m_tableName);
                    m_database.execSQL(getCreator(m_tableName));
                } catch (SQLiteException e) {
                    handleException(i, e, m_tableName);
                }
                m_cursor = null;
                m_position = -1;
                // Force our caller to break out of its loop.
                throw (new NoColumnException());
            } else {
                try {
                    // It's safe to use m_cursor here because we called cursor() above.
                    return m_cursor.getString(columnIndex);
                } catch (IllegalStateException ignore) {
                    // This is definitely a programming error: we
                    // asked for a column which isn't in the recreated table.
                    String small = m_context.getString(
                        R.string.badcolname);
                    String big = small + m_context.getString(
                        R.string.incall, caller, columnName, m_tableName);
                    new MyLog(m_context, true, small, big);
                } catch (CursorIndexOutOfBoundsException ignore) {
                    // This is definitely a programming error: we called getString
                    // when the cursor was before first or after last.
                    String small = m_context.getString(
                        m_cursor.isBeforeFirst() ? R.string.cursorbeforefirst
                                                 : R.string.cursorafterlast);
                    String big = small + m_context.getString(
                        R.string.incall, caller, columnName, m_tableName);
                    new MyLog(m_context, true, small, big);
                }
            }
        }
    }

    public String getString(String columnName)
    throws NoColumnException {
        return getString(columnName, "getString");
    }

    public long getUnsignedLong(String columnName)
        throws NumberFormatException, NoColumnException
    {
        String s = getString(columnName, "getUnsignedLong");
        if ((s != null) && s.matches("^[0-9]+$")) {
            return Long.parseLong(s);
        }
        else
        {
            throw(new NumberFormatException("Bad number " + s));
        }
    }

    // The Android implementation doesn't guarantee that a cursor on this
    // table will reflect the insertion.
    // So we call invalidate() to destroy all the cursors on this table owned
    // by this database connection. Any cursor which is subsequently referenced
    // will be recreated.
    @SuppressWarnings("UnusedReturnValue")
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
    // We should use bind arguments here, but it doesn't seem to work....
    public int update(ContentValues cv) {
        StringBuilder whereClause = new StringBuilder();
        int columns = cursor().getColumnCount();
        boolean first = true;
        int i;
        for ( i = 0; i < columns; ++i) {
            if (first) { first = false; } else {
                whereClause.append(" AND ");
            }
            // It's safe to use m_cursor here because we called cursor() above.
            whereClause.append(m_cursor.getColumnName(i));
            whereClause.append(" IS ");
            String s = m_cursor.getString(i);
            if (s == null) {
                whereClause.append("NULL");
            }
            else if (s.matches("^-?[0-9]+$"))
            {
                whereClause.append(s);
            }
            else
            {
                whereClause.append("'");
                whereClause.append(s.replace("'", "''"));
                whereClause.append("'");
            }
        }
        for (i = 10; ; --i) {
            try {
                int result = m_database.update(m_tableName, cv,
                    whereClause.toString(), null);
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

    @SuppressWarnings("unused")
    public int update(String columnName, String value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return update(cv);
    }

    @SuppressWarnings("UnusedReturnValue")
    public int update(String columnName, long value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return update(cv);
    }

    // This deletes the row that our cursor is looking at.
    // The Android implementation uses AbstractWindowedCursor, so it doesn't
    // guarantee that the cursor caches all the rows selected.
    // So we invalidate all cursors on this table.
    // SQLite says that when you SELECT from a table, the rows are returned in an
    // undefined order. Here we assume that the order is at least consistent,
    // so that repeated SELECTs return the rows in the same order.
    @SuppressWarnings("UnusedReturnValue")
    public int delete() {
        StringBuilder whereClause = new StringBuilder();
        int columns = cursor().getColumnCount();
        // It's safe to use m_cursor here because we called cursor() above.
        int position = m_cursor.getPosition();
        if ((position == -1) || (position >= m_cursor.getCount())) {
            // This is definitely a programming error: we tried to delete
            // when the cursor is not pointing at a record.
            String big = new StringBuilder("Called delete() when cursor at position ")
                .append(position).append(" where there is no record").toString();
            new MyLog(m_context, true, "delete() on no record", big);
        }
        // Move back to before deleted record so that moveToNext() will
        // get to the record after it.
        m_position = position - 1;
        // We should use bind arguments here, but it doesn't seem to work....
        boolean first = true;
        int i;
        for ( i = 0; i < columns; ++i) {
            if (first) { first = false; } else {
                whereClause.append(" AND ");
            }
            // It's safe to use m_cursor here because we called cursor() above.
            whereClause.append(m_cursor.getColumnName(i));
            whereClause.append(" IS ");
            // If there is no current record we'll fail here
            String s = m_cursor.getString(i);
            if (s == null) {
                whereClause.append("NULL");
            }
            else if (s.matches("^-?[0-9]+$"))
            {
                whereClause.append(s);
            }
            else
            {
                whereClause.append("'");
                whereClause.append(s.replace("'", "''"));
                whereClause.append("'");
            }
        }
        for (i = 10; ; --i) {
            try {
                int result = m_database.delete(
                    m_tableName, whereClause.toString(), null);
                invalidate(m_tableName);
                // Move back to before deleted record so that moveToNext() will
                // get to the record after it.
                m_position = position - 1;
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
    private void tryVacuum()  {
        new MyLog(m_context, "tryVacuum()");
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
                cr.close();
            }
            else
            {
                // No row in VACUUMDATA yet, make one
                new MyLog(m_context,
                    m_context.getString(R.string.norows));
                m_database.execSQL("INSERT INTO VACUUMDATA ( VACUUM_COUNT ) VALUES ( "
                    + (count + 1) + " )");
                cr.close();
                return;
            }
        } catch (SQLiteException e) {
            handleException(1, e, "VACUUMDATA");
            invalidate("VACUUMDATA");
        }
        try {
            m_database.execSQL("UPDATE VACUUMDATA SET VACUUM_COUNT = " + (count + 1));
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
        String q = m_query;
        for (String s : m_args) {
            q = q.replaceFirst("\\?",
                "'" + s.replace("'", "''") + "'");
        }
        sb.append(q);
        sb.append(")");
        return sb.toString();
    }

    public void close() {
        new MyLog(m_context, "close(), m_tableName = " + m_tableName);
        if (m_cursor != null) {
            m_cursor.close();
            m_cursor = null;
            m_position = -1;
        }
        // Arrange to close the database when we have no more tables open on it.
        if (m_children.size() == 0) {
            if (m_parent == null) {
                tryVacuum();
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
            new MyLog(m_context, "closing parent before child");
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
    }
}
