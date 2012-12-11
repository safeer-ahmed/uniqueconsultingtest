/*
 * Copyright (c) 2012, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.store;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sqlcipher.database.SQLiteDatabase;

import android.util.Log;

import com.salesforce.androidsdk.store.SmartStore.SmartStoreException;

/**
 * SmartSqlHelper "smart" sql Helper
 * 
 * Singleton class that provides helpful methods for converting/running "smart" sql
 */
public enum SmartSqlHelper  {
	
	INSTANCE;

	/**
	 * Convert "smart" sql query to actual sql
	 * A "smart" sql query is a query where columns are of the form {soupName:path} and tables are of the form {soupName}
	 * NB: only select's are allowed
	 *     only indexed path can be referenced (alternatively you can do {soupName:_soupEntryId} or {soupName:_soupLastModifiedDate}
	 *     to get an entire soup element back, do {soupName:}
	 *
	 * @param db
	 * @param smartSql
	 * @return actual sql     
	 */
	public String convertSmartSql(SQLiteDatabase db, String smartSql) {
		Log.i("SmartSqlHelper.convertSmartSql", "smart sql = " + smartSql);
		
		// Select's only
		String smartSqlLowerCase = smartSql.toLowerCase(Locale.getDefault());
		if (smartSqlLowerCase.contains("insert") || smartSqlLowerCase.contains("update") || smartSqlLowerCase.contains("delete")) {
			throw new SmartSqlException("Only SELECT are supported");
		}
		
		// Replacing {soupName} and {soupName:path}
		Pattern pattern  = Pattern.compile("\\{([^}]+)\\}");
		StringBuffer sql = new StringBuffer();
		Matcher matcher = pattern.matcher(smartSql);
		while(matcher.find()) {
			String fullMatch = matcher.group();
			String match = matcher.group(1);
			int position = matcher.start();

			// {soupName:path}
			if (match.contains(":")) {
				String[] parts = match.split(":");
				if (parts.length > 2) {
					reportSmartSqlError("Invalid soup/path reference " + fullMatch, position);
				}
				String soupName = parts[0];
				String path = parts[1];
				if (path.equals("")) {
					matcher.appendReplacement(sql, SmartStore.SOUP_COL);
				}
				else if (path.equals(SmartStore.SOUP_ENTRY_ID) || path.equals(SmartStore.SOUP_LAST_MODIFIED_DATE)) {
					matcher.appendReplacement(sql, path);
				}
				else {
					/* String soupTableName = */ getSoupTableNameForSmartSql(db, soupName, position); // for validation
					String columnName = getColumnNameForPathForSmartSql(db, soupName, path, position);
					matcher.appendReplacement(sql, columnName);
				}
			}
			// {soupName}
			else {
				String soupTableName = getSoupTableNameForSmartSql(db, match, position);
				matcher.appendReplacement(sql, soupTableName);
			}
		}
		matcher.appendTail(sql);
		
		// Done
		Log.i("SmartSqlHelper.convertSmartSql", "sql = " + sql);
		
		return sql.toString();
	}
	
	private String getColumnNameForPathForSmartSql(SQLiteDatabase db, String soupName, String path, int position) {
		String columnName = null;
		try {
			columnName = DBHelper.INSTANCE.getColumnNameForPath(db, soupName, path);
		}
		catch (SmartStoreException e) {
			reportSmartSqlError(e.getMessage(), position);
		}
		return columnName;
	}

	private String getSoupTableNameForSmartSql(SQLiteDatabase db, String soupName, int position) {
		String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
		if (soupTableName == null) {
			reportSmartSqlError("Unknown soup " + soupName, position);
		}
		return soupTableName;
	}
	
	private void reportSmartSqlError(String message, int position) {
		throw new SmartSqlException(message + " at character " + position);
	}
		

    
    /**
     * Exception thrown when smart sql failed to be parsed
     */
    public static class SmartSqlException extends RuntimeException {

		private static final long serialVersionUID = -525130153073212701L;

		public SmartSqlException(String message) {
    		super(message);
    	}
    }
	
}
