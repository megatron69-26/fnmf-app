package com.example.nhumonglenh.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NewsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNews(NewsEntity news);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAIAnalysis(AiAnalysisEntity analysis);

    @Query("SELECT * FROM news_table ORDER BY publishedAt DESC")
    List<NewsEntity> getAllNews();

    @Query("SELECT * FROM ai_analysis_table WHERE newsId = :newsId LIMIT 1")
    AiAnalysisEntity getCachedAIAnalysis(String newsId);
}
