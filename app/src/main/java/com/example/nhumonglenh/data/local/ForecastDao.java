package com.example.nhumonglenh.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ForecastDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertForecast(ForecastEntity forecast);

    @Query("SELECT * FROM forecast_table WHERE symbol = :symbol LIMIT 1")
    ForecastEntity getForecast(String symbol);

    @Query("DELETE FROM forecast_table WHERE symbol = :symbol")
    void deleteForecast(String symbol);

    @Query("DELETE FROM forecast_table")
    void deleteAll();
}
