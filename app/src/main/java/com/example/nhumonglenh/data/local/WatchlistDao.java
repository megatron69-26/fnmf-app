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
}
