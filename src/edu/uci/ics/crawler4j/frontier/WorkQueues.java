package edu.uci.ics.crawler4j.frontier;

import com.sleepycat.je.*;
import edu.uci.ics.crawler4j.url.WebURL;
import edu.uci.ics.crawler4j.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yasser Ganjisaffar <lastname at gmail dot com>
 */
public class WorkQueues {
	public short depthCalculator = 1;
	protected Database urlsDB = null;
	protected Environment env;

	protected boolean resumable;

	protected WebURLTupleBinding webURLBinding;

	protected final Object mutex = new Object();

	public WorkQueues(Environment env, String dbName, boolean resumable) throws DatabaseException {
		this.env = env;
		this.resumable = resumable;
		DatabaseConfig dbConfig = new DatabaseConfig();
		dbConfig.setAllowCreate(true);
		dbConfig.setTransactional(resumable);
		dbConfig.setDeferredWrite(!resumable);
		urlsDB = env.openDatabase(null, dbName, dbConfig);
		webURLBinding = new WebURLTupleBinding();
	}

	public List<WebURL> get(int max) throws DatabaseException {
		synchronized (mutex) {
			int matches = 0;
			List<WebURL> results = new ArrayList<WebURL>(max);

			Cursor cursor = null;
			OperationStatus result;
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			Transaction txn;
			if (resumable) {
				txn = env.beginTransaction(null, null);
			} else {
				txn = null;
			}
			try {
				cursor = urlsDB.openCursor(txn, null);
				result = cursor.getFirst(key, value, null);

				while (matches < max && result == OperationStatus.SUCCESS) {
					if (value.getData().length > 0) {
						results.add(webURLBinding.entryToObject(value));
						matches++;
					}
					result = cursor.getNext(key, value, null);
				}
			} catch (DatabaseException e) {
				if (txn != null) {
					txn.abort();
					txn = null;
				}
				throw e;
			} finally {
				if (cursor != null) {
					cursor.close();
				}
				if (txn != null) {
					txn.commit();
				}
			}
			return results;
		}
	}

	public void delete(int count) throws DatabaseException {
		synchronized (mutex) {
			int matches = 0;

			Cursor cursor = null;
			OperationStatus result;
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			Transaction txn;
			if (resumable) {
				txn = env.beginTransaction(null, null);
			} else {
				txn = null;
			}
			try {
				cursor = urlsDB.openCursor(txn, null);
				result = cursor.getFirst(key, value, null);

				while (matches < count && result == OperationStatus.SUCCESS) {
					cursor.delete();
					matches++;
					result = cursor.getNext(key, value, null);
				}
			} catch (DatabaseException e) {
				if (txn != null) {
					txn.abort();
					txn = null;
				}
				throw e;
			} finally {
				if (cursor != null) {
					cursor.close();
				}
				if (txn != null) {
					txn.commit();
				}
			}
		}
	}

	public void put(WebURL url) throws DatabaseException {
		
		byte[] keyData = Util.int2ByteArray(url.getDocid());
		//System.out.println("WorkQues.put = " + url.getDocid());
		DatabaseEntry value = new DatabaseEntry();
		webURLBinding.objectToEntry(url, value);
		Transaction txn;
		txn = null;
		urlsDB.put(txn, new DatabaseEntry(keyData), value);
		
	}

	public long getLength() {
		try {
			return urlsDB.count();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	public void sync() {
		if (resumable) {
			return;
		}
		if (urlsDB == null) {
			return;
		}
		try {
			urlsDB.sync();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			urlsDB.close();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}
}
