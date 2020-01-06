/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

// This exception is thrown when we try to access a nonexistent column in one of our tables.
// This could be a programming error, or someone could haves tampered with our table,
// or we might be running a new version in which the structure of the table has changed.
// The exception is needed because we have thrown away the table and created a new empty one,
// and if the caller is in a while (table.moveToNext()) loop, it needs to break out of it.
// If at some time in the future we modify the structure of a table which needs to persist
// across installing a new version of CalendarTrigger, the best solution is probably to
// change its name. Then if the new table doesn't exist we can look whether the old one
// exists and if so copy the data from that. This is similar to the handling of the change
// of name of the database file name from version 3.2 to version 3.3.
public class NoColumnException extends Exception {
}
