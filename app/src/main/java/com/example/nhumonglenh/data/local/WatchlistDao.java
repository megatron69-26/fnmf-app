package com.example.nhumonglenh.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WatchlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertItem(WatchlistItem item);

    @Delete
    void deleteItem(WatchlistItem item);

    @Query("SELECT * FROM watchlist_table ORDER BY id DESC")
    List<WatchlistItem> getAllWatchlist();

    @Query("SELECT * FROM watchlist_table WHERE userEmail = :userEmail ORDER BY id DESC")
    List<WatchlistItem> getWatchlistByUser(String userEmail);

    @Query("DELETE FROM watchlist_table WHERE userEmail = :userEmail AND symbol = :symbol")
    void deleteByUserAndSymbol(String userEmail, String symbol);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<WatchlistItem> items);

    @Query("DELETE FROM watchlist_table WHERE userEmail = :userEmail")
    void clearByUser(String userEmail);

    @androidx.room.Transaction
    default void clearAndInsertAll(String userEmail, List<WatchlistItem> items) {
        clearByUser(userEmail);
        if (items != null && !items.isEmpty()) {
            insertAll(items);
        }
    }
}
