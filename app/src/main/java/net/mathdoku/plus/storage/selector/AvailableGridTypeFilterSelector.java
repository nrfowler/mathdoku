package net.mathdoku.plus.storage.selector;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import net.mathdoku.plus.config.Config;
import net.mathdoku.plus.enums.GridType;
import net.mathdoku.plus.enums.GridTypeFilter;
import net.mathdoku.plus.enums.StatusFilter;
import net.mathdoku.plus.storage.databaseadapter.DatabaseAdapterException;
import net.mathdoku.plus.storage.databaseadapter.GridDatabaseAdapter;
import net.mathdoku.plus.storage.databaseadapter.database.DatabaseProjection;

import java.util.ArrayList;
import java.util.List;

/**
 * This class selects all GridTypeFilters which are applicable when grids with a given StatusFilter are selected.
 */
public class AvailableGridTypeFilterSelector extends SolvingAttemptSelector {
    @SuppressWarnings("unused")
    private static final String TAG = AvailableStatusFiltersSelector.class.getName();

    // Replace Config.disabledAlways() on following line with Config.enabledInDevelopmentModeOnly()
    // to show debug information when running in development mode.
    private static final boolean DEBUG = Config.disabledAlways();

    private static final String KEY_PROJECTION_GRID_SIZE = "projection_grid_size";
    private final List<GridTypeFilter> gridTypeFilterList;

    public AvailableGridTypeFilterSelector(StatusFilter statusFilter) {
        super(statusFilter, GridTypeFilter.ALL);
        setEnableLogging(DEBUG);
        setOrderByString(KEY_PROJECTION_GRID_SIZE);
        setGroupByString(KEY_PROJECTION_GRID_SIZE);
        gridTypeFilterList = retrieveFromDatabase();
    }

    public List<GridTypeFilter> retrieveFromDatabase() {
        List<GridTypeFilter> gridTypeFilters = new ArrayList<GridTypeFilter>();
        gridTypeFilters.add(GridTypeFilter.ALL);
        Cursor cursor = null;
        try {
            cursor = getCursor();
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    gridTypeFilters.add(GridType.fromInteger(getProjectionGridTypeFilterFromCursor(cursor))
                                                .getAttachedToGridTypeFilter());
                } while (cursor.moveToNext());
            }
        } catch (SQLiteException e) {
            throw new DatabaseAdapterException(String.format(
                    "Cannot retrieve used sizes of latest solving attempt per grid from the " + "database (status " +
                            "filter = %s).",
                    statusFilter.toString()), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return gridTypeFilters;
    }

    private int getProjectionGridTypeFilterFromCursor(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(KEY_PROJECTION_GRID_SIZE));
    }

    @Override
    protected DatabaseProjection getDatabaseProjection() {
        DatabaseProjection databaseProjection = new DatabaseProjection();
        databaseProjection.put(KEY_PROJECTION_GRID_SIZE, GridDatabaseAdapter.TABLE_NAME,
                               GridDatabaseAdapter.KEY_GRID_SIZE);
        return databaseProjection;
    }

    public List<GridTypeFilter> getAvailableGridTypeFilters() {
        return gridTypeFilterList;
    }
}
