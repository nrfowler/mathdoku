package net.mathdoku.plus.storage.database;

import java.util.ArrayList;

import net.mathdoku.plus.config.Config;
import net.mathdoku.plus.config.Config.AppMode;
import net.mathdoku.plus.grid.Grid;
import net.mathdoku.plus.storage.GridStorage;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

/**
 * The database adapter for the solving attempt table. For each grid one or more
 * solving attempt records can exists in the database. For grid created with
 * version 2 of this app, a statistics record will exist. For grids created with
 * an older version, statistics data is not available.
 */
public class SolvingAttemptDatabaseAdapter extends DatabaseAdapter {
	private static final String TAG = "MathDoku.SolvingAttemptDatabaseAdapter";

	// Remove "&& false" in following line to show the SQL-statements in the
	// debug information
	@SuppressWarnings("PointlessBooleanExpression")
	private static final boolean DEBUG_SQL = (Config.mAppMode == AppMode.DEVELOPMENT) && false;

	// Columns for table
	static final String TABLE = "solving_attempt";
	static final String KEY_ROWID = "_id";
	static final String KEY_GRID_ID = "grid_id";
	private static final String KEY_DATE_CREATED = "date_created";
	private static final String KEY_DATE_UPDATED = "date_updated";
	private static final String KEY_SAVED_WITH_REVISION = "revision";
	private static final String KEY_DATA = "data";
	static final String KEY_STATUS = "status";

	// Status of solving attempt
	private static final int STATUS_UNDETERMINED = -1;
	public static final int STATUS_NOT_STARTED = 0;
	public static final int STATUS_UNFINISHED = 50;
	public static final int STATUS_FINISHED_SOLVED = 100;
	public static final int STATUS_REVEALED_SOLUTION = 101;

	private static final String[] dataColumns = { KEY_ROWID, KEY_GRID_ID,
			KEY_DATE_CREATED, KEY_DATE_UPDATED, KEY_SAVED_WITH_REVISION,
			KEY_DATA, KEY_STATUS };

	// Delimiters used in the data field to separate objects, fields and values
	// in a field which can hold multiple values.
	public static final String EOL_DELIMITER = "\n"; // Separate objects
	public static final String FIELD_DELIMITER_LEVEL1 = ":"; // Separate fields
	public static final String FIELD_DELIMITER_LEVEL2 = ","; // Separate values

	// in fields

	/**
	 * Get the table name.
	 * 
	 * @return The table name;
	 */
	@Override
	protected String getTableName() {
		return TABLE;
	}

	/**
	 * Builds the SQL create statement for this table.
	 * 
	 * @return The SQL create statement for this table.
	 */
	private static String buildCreateSQL() {
		return createTable(
				TABLE,
				createColumn(KEY_ROWID, "integer", "primary key autoincrement"),
				createColumn(KEY_GRID_ID, "integer", " not null"),
				createColumn(KEY_DATE_CREATED, "datetime", "not null"),
				createColumn(KEY_DATE_UPDATED, "datetime", "not null"),
				createColumn(KEY_SAVED_WITH_REVISION, "integer", " not null"),
				createColumn(KEY_DATA, "string", "not null"),
				createColumn(KEY_STATUS, "integer", "not null default "
						+ STATUS_UNDETERMINED),
				createForeignKey(KEY_GRID_ID, GridDatabaseAdapter.TABLE,
						GridDatabaseAdapter.KEY_ROWID));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.mathdoku.plus.storage.database.DatabaseAdapter#getCreateSQL ()
	 */
	@Override
	protected String getCreateSQL() {
		return buildCreateSQL();
	}

	/**
	 * Creates the table.
	 * 
	 * @param db
	 *            The database in which the table has to be created.
	 */
	static void create(SQLiteDatabase db) {
		String sql = buildCreateSQL();
		if (Config.mAppMode == AppMode.DEVELOPMENT) {
			Log.i(TAG, sql);
		}

		// Execute create statement
		db.execSQL(sql);
	}

	/**
	 * Upgrades the table to an other version.
	 * 
	 * @param db
	 *            The database in which the table has to be updated.
	 * @param oldVersion
	 *            The old version of the database. Use the app revision number
	 *            to identify the database version.
	 * @param newVersion
	 *            The new version of the database. Use the app revision number
	 *            to identify the database version.
	 */
	static void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 433 && newVersion >= 433) {
			// In development revisions the table is simply dropped and
			// recreated.
			try {
				String sql = "DROP TABLE " + TABLE;
				if (DEBUG_SQL) {
					Log.i(TAG, sql);
				}
				db.execSQL(sql);
			} catch (SQLiteException e) {
				if (Config.mAppMode == AppMode.DEVELOPMENT) {
					e.printStackTrace();
				}
			}
			create(db);
		}
	}

	/**
	 * Inserts a new solving attempt record for a grid into the database.
	 * 
	 * @param grid
	 *            The grid for which a new solving attempt record has to be
	 *            inserted.
	 * @param revision
	 *            The app revision used to store the data.
	 * @return The row id of the row created. -1 in case of an error.
	 */
	public int insert(Grid grid, int revision) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_GRID_ID, grid.getRowId());
		initialValues.put(KEY_DATE_CREATED,
				toSQLiteTimestamp(grid.getDateCreated()));
		initialValues.put(KEY_DATE_UPDATED,
				toSQLiteTimestamp(grid.getDateSaved()));
		initialValues.put(KEY_SAVED_WITH_REVISION, revision);

		GridStorage gridStorage = new GridStorage();
		initialValues.put(KEY_DATA, gridStorage.toStorageString(grid));

		// Status is derived from grid. It is stored as derived data for easy
		// filtering on solving attempts for the archive
		initialValues.put(KEY_STATUS, getDerivedStatus(grid));

		long id = -1;
		try {
			id = mSqliteDatabase.insertOrThrow(TABLE, null, initialValues);
		} catch (SQLiteException e) {
			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				e.printStackTrace();
			}
		}

		if (id < 0) {
			throw new DatabaseException(
					"Insert of new puzzle failed when inserting the solving attempt into the database.");
		}

		return (int) id;
	}

	/**
	 * Gets the solving attempt for the given solving attempt id.
	 * 
	 * @param solvingAttemptId
	 *            The solving attempt id for which the data has to be retrieved.
	 * @return The data of the solving attempt.
	 */
	public SolvingAttempt getData(int solvingAttemptId) {
		SolvingAttempt solvingAttempt = null;
		Cursor cursor = null;
		try {
			cursor = mSqliteDatabase.query(true, TABLE, dataColumns, KEY_ROWID
					+ "=" + solvingAttemptId, null, null, null, null, null);

			if (cursor == null || !cursor.moveToFirst()) {
				// No record found for this grid.
				return null;
			}

			// Convert cursor record to a SolvingAttempt row
			solvingAttempt = new SolvingAttempt();
			solvingAttempt.mId = cursor.getInt(cursor
					.getColumnIndexOrThrow(KEY_ROWID));
			solvingAttempt.mGridId = cursor.getInt(cursor
					.getColumnIndexOrThrow(KEY_GRID_ID));
			solvingAttempt.mDateCreated = valueOfSQLiteTimestamp(cursor
					.getString(cursor.getColumnIndexOrThrow(KEY_DATE_CREATED)));
			solvingAttempt.mDateUpdated = valueOfSQLiteTimestamp(cursor
					.getString(cursor.getColumnIndexOrThrow(KEY_DATE_UPDATED)));
			solvingAttempt.mSavedWithRevision = cursor.getInt(cursor
					.getColumnIndexOrThrow(KEY_SAVED_WITH_REVISION));
			solvingAttempt.mData = new SolvingAttemptData(
					cursor.getString(cursor.getColumnIndexOrThrow(KEY_DATA)));
		} catch (SQLiteException e) {
			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return solvingAttempt;
	}

	/**
	 * Get the most recently played solving attempt
	 * 
	 * @return The id of the solving attempt which was last played. -1 in case
	 *         of an error.
	 */
	public int getMostRecentPlayedId() {
		int id = -1;
		Cursor cursor = null;
		try {
			cursor = mSqliteDatabase.query(true, TABLE,
					new String[] { KEY_ROWID }, null, null, null, null,
					KEY_DATE_UPDATED + " DESC", "1");

			if (cursor == null || !cursor.moveToFirst()) {
				// No record found
				return -1;
			}

			// Convert cursor record to a SolvingAttempt row
			id = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ROWID));
		} catch (SQLiteException e) {
			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return -1;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return id;
	}

	/**
	 * Update the data of a solving attempt with given data. Also the last
	 * update timestamp is set. It is required that the record already exists.
	 * The id should never be changed.
	 * 
	 * @param id
	 *            The id of the solving attempt to be updated.
	 * @param grid
	 *            The grid to be stored in the solving attempt.
	 * @return True in case the statistics have been updated. False otherwise.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean update(int id, Grid grid) {
		ContentValues newValues = new ContentValues();
		newValues.put(KEY_DATE_UPDATED,
				toSQLiteTimestamp(new java.util.Date().getTime()));
		return update(id, grid, newValues);
	}

	/**
	 * Update the data of a solving attempt with given data. The last update
	 * timestamp is not updated. It is required that the record already exists.
	 * The id should never be changed.
	 * 
	 * @param id
	 *            The id of the solving attempt to be updated.
	 * @param grid
	 *            The grid to be stored in the solving attempt.
	 * @return True in case the statistics have been updated. False otherwise.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean updateOnAppUpgrade(int id, Grid grid) {
		return update(id, grid, new ContentValues());
	}

	/**
	 * Update the solving attempt. The given content values will be updated with
	 * information from the grid.
	 * 
	 * @param id
	 *            The id of the solving attempt to be updated.
	 * @param grid
	 *            The grid to be stored in the solving attempt.
	 * @param contentValues
	 *            The content values to be use as base for updating.
	 * @return True in case the statistics have been updated. False otherwise.
	 */
	private boolean update(int id, Grid grid, ContentValues contentValues) {
		GridStorage gridStorage = new GridStorage();
		contentValues.put(KEY_DATA, gridStorage.toStorageString(grid));

		// Status is derived from grid. It is stored as derived data for easy
		// filtering on solving attempts for the archive
		contentValues.put(KEY_STATUS, getDerivedStatus(grid));

		return (mSqliteDatabase.update(TABLE, contentValues, KEY_ROWID + " = "
				+ id, null) == 1);
	}

	/**
	 * Gets a list of id's for all solving attempts which need to be converted.
	 * 
	 * @return The list of id's for all solving attempts which need to be
	 *         converted.
	 */
	public ArrayList<Integer> getAllToBeConverted() {
		ArrayList<Integer> idArrayList = null;
		Cursor cursor = null;
		String[] columns = { KEY_ROWID };
		try {
			// Currently all solving attempts are returned. In future this can
			// be restricted to games which are not solved.
			cursor = mSqliteDatabase.query(true, TABLE, columns, null, null,
					null, null, null, null);

			if (cursor == null || !cursor.moveToFirst()) {
				// No record found for this grid.
				return null;
			}

			// Convert cursor records to an array list of id's.
			idArrayList = new ArrayList<Integer>();
			do {
				idArrayList.add(cursor.getInt(cursor
						.getColumnIndexOrThrow(KEY_ROWID)));
			} while (cursor.moveToNext());
		} catch (SQLiteException e) {
			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return idArrayList;
	}

	/**
	 * Get the status of this solving attempt.
	 * 
	 * @param grid
	 *            The grid to which the solving attempt applies.
	 * @return The status of the solving attempt.
	 */
	private int getDerivedStatus(Grid grid) {
		// Check if the game was finished by revealing the solution
		if (grid.isSolutionRevealed()) {
			return STATUS_REVEALED_SOLUTION;
		}

		// Check if the game has been solved manually
		if (grid.isActive() == false) {
			return STATUS_FINISHED_SOLVED;
		}

		// Check if the grid is empty
		if (grid.isEmpty()) {
			return STATUS_NOT_STARTED;
		}

		return STATUS_UNFINISHED;
	}

	/**
	 * Prefix the given column name with the table name.
	 * 
	 * @param column
	 *            The column name which has to be prefixed.
	 * @return The prefixed column name.
	 */
	public static String getPrefixedColumnName(String column) {
		return stringBetweenBackTicks(TABLE) + "."
				+ stringBetweenBackTicks(column);
	}

	/**
	 * Count the number of solving attempt for the given grid id.
	 * 
	 * @param gridId
	 *            The grid id for which the number of solving attempts has to be
	 *            determined.
	 * @return The number of solving attempt for the given grid id.
	 */
	public int countSolvingAttemptForGrid(int gridId) {
		int count = 0;
		Cursor cursor = null;
		try {
			cursor = mSqliteDatabase.query(true, TABLE,
					new String[] { "COUNT(1)" }, KEY_GRID_ID + "=" + gridId,
					null, null, null, null, null);

			if (cursor == null || !cursor.moveToFirst()) {
				// No record found for this grid.
				return 0;
			}

			// Convert cursor record to a SolvingAttempt row
			count = cursor.getInt(0);
		} catch (SQLiteException e) {
			if (Config.mAppMode == AppMode.DEVELOPMENT) {
				e.printStackTrace();
			}
			return 0;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return count;
	}
}
