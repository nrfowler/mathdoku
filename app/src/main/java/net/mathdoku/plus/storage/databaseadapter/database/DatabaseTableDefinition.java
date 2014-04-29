package net.mathdoku.plus.storage.databaseadapter.database;

import net.mathdoku.plus.util.ParameterValidator;
import net.mathdoku.plus.util.Util;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import static net.mathdoku.plus.storage.databaseadapter.database.DatabaseUtil.stringBetweenBackTicks;

class DatabaseTableDefinition {
	private final String tableName;
	private final List<DatabaseColumnDefinition> databaseColumnDefinitions;
	private DatabaseForeignKeyDefinition foreignKey;
	private boolean isComposed;
	private String[] columnNames;

	public DatabaseTableDefinition(String tableName) {
		ParameterValidator.validateNotNullOrEmpty(tableName);
		this.tableName = tableName;
		databaseColumnDefinitions = new ArrayList<DatabaseColumnDefinition>();
	}

	public void addColumn(DatabaseColumnDefinition databaseColumnDefinition) {
		if (isComposed) {
			throwAlterDatabaseTableNotAllowedAfterCompose();
		}
		databaseColumnDefinitions.add(databaseColumnDefinition);
	}

	private void throwAlterDatabaseTableNotAllowedAfterCompose() {
		throw new DatabaseException("Cannot alter database table after it is composed.");
	}

	public void setForeignKey(DatabaseForeignKeyDefinition foreignKey) {
		if (isComposed) {
			throwAlterDatabaseTableNotAllowedAfterCompose();
		}
		this.foreignKey = foreignKey;
	}

	public void build() {
		if (Util.isListNullOrEmpty(databaseColumnDefinitions)) {
			throw new InvalidParameterException(
					"At least one column has to be specified.");
		}
		setColumnNames();
	}

	private String[] setColumnNames() {
		String[] columnNames = new String[databaseColumnDefinitions.size()];
		int index = 0;
		for (DatabaseColumnDefinition databaseColumnDefinition : databaseColumnDefinitions) {
			columnNames[index++] = databaseColumnDefinition.getName();
		}
		return columnNames;
	}

	public String getTableName() {
		return tableName;
	}

	public String[] getColumnNames() {
		if (!isComposed) {
			new IllegalStateException("Cannot be called until database table has been composed.");
		}
		return columnNames;
	}

	public String getCreateTableSQL() {
		StringBuilder query = new StringBuilder();
		query.append("CREATE TABLE ");
		query.append(stringBetweenBackTicks(tableName));
		query.append(" (");
		int remainingColumns = databaseColumnDefinitions.size();
		for (DatabaseColumnDefinition databaseColumnDefinition : databaseColumnDefinitions) {
			query.append(databaseColumnDefinition.getColumnClause());
			if (--remainingColumns > 0) {
				query.append(", ");
			}
		}
		if (foreignKey != null) {
			query.append(", ");
			query.append(foreignKey.getForeignKeyClause());
		}
		query.append(")");
		return query.toString();
	}

}
