package net.mathdoku.plus.storage.databaseadapter;

import android.text.TextUtils;

import net.mathdoku.plus.storage.databaseadapter.database.DatabaseException;
import net.mathdoku.plus.util.ParameterValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class to drop a column from an existing table. This class should not be removed even in case it is currently
 * only used in the unit tests.
 */
public class DatabaseColumnDropper {
    @SuppressWarnings("unused")
    private static final String TAG = DatabaseColumnDropper.class.getName();

    private final DatabaseAdapter databaseAdapter;
    private List<String> currentColumnList;
    private String[] columnsToBeDropped;
    private List<String> newColumnList;

    public DatabaseColumnDropper(DatabaseAdapter databaseAdapter) {
        this.databaseAdapter = databaseAdapter;
        currentColumnList = databaseAdapter.getActualTableColumns();
    }

    public boolean dropColumns(String[] columnsToBeDropped) {
        ParameterValidator.validateNotNullOrEmpty(columnsToBeDropped);
        this.columnsToBeDropped = columnsToBeDropped.clone();
        validateColumnsToBeDropped();

        newColumnList = getColumnsRemaining();
        validateColumnsRemaining();

        try {
            databaseAdapter.beginTransaction();

            databaseAdapter.execAndLogSQL(getRenameTableToTemporaryTableSQL());
            databaseAdapter.createTable();
            databaseAdapter.execAndLogSQL(getInsertDataSQL());
            databaseAdapter.execAndLogSQL(getDropTemporaryTableSQL());

            databaseAdapter.setTransactionSuccessful();
        } catch (Exception e) {
            throw new DatabaseException(
                    String.format("Error while dropping column(s) from table %s.", databaseAdapter.getTableName()), e);
        } finally {
            databaseAdapter.endTransaction();
        }
        return true;
    }

    private void validateColumnsToBeDropped() {
        if (columnsToBeDropped == null || columnsToBeDropped.length == 0) {
            throw new DatabaseException(String.format("Parameter columnsToBeDropped has an invalid value '%s'.",
                                                      Arrays.toString(columnsToBeDropped)));
        }
        for (String columnToBeDropped : columnsToBeDropped) {
            validateColumnToBeDropped(columnToBeDropped);
        }
    }

    private void validateColumnToBeDropped(String columnToBeDropped) {
        if (columnToBeDropped == null || columnToBeDropped.trim()
                .isEmpty()) {
            throw new DatabaseException(
                    String.format("Parameter columnToBeDropped has an invalid value '%s'.", columnToBeDropped));
        }
        if (!currentColumnList.contains(columnToBeDropped)) {
            throw new DatabaseException(String.format("Column '%s' is not an existing column.", columnToBeDropped));
        }
    }

    private List<String> getColumnsRemaining() {
        List<String> remainingColumns = new ArrayList<String>(currentColumnList);
        remainingColumns.removeAll(Arrays.asList(columnsToBeDropped));
        return remainingColumns;
    }

    private void validateColumnsRemaining() {
        if (newColumnList == null || newColumnList.isEmpty() || newColumnList.equals(currentColumnList)) {
            throw new DatabaseException(
                    String.format("Remaining list of columns which are not deleted is not valid:" + " %s.",
                                  newColumnList));
        }
    }

    private String getRenameTableToTemporaryTableSQL() {
        return String.format("ALTER TABLE %s RENAME TO %s;", databaseAdapter.getTableName(), getTempTableName());
    }

    private String getTempTableName() {
        return databaseAdapter.getTableName() + "_old";
    }

    private String getInsertDataSQL() {
        String newColumnsString = TextUtils.join(",", newColumnList);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("INSERT INTO ");
        stringBuilder.append(databaseAdapter.getTableName());
        stringBuilder.append("(");
        stringBuilder.append(newColumnsString);
        stringBuilder.append(") SELECT ");
        stringBuilder.append(newColumnsString);
        stringBuilder.append(" FROM ");
        stringBuilder.append(getTempTableName());
        stringBuilder.append(";");

        return stringBuilder.toString();
    }

    public String getDropTemporaryTableSQL() {
        return "DROP TABLE " + getTempTableName();
    }
}
