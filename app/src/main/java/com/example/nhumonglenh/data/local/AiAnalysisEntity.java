package com.example.nhumonglenh.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "ai_analysis_table",
    foreignKeys = @ForeignKey(
        entity = NewsEntity.class,
        parentColumns = "newsId",
        childColumns = "newsId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "newsId", unique = true)}
)
public class AiAnalysisEntity {

    @PrimaryKey
    @NonNull
    private String newsId;

    private String summary;
    private String sentiment;
    private int confidenceScore;
    private String reason;

    public AiAnalysisEntity() {
        this.newsId = "";
    }

    @Ignore
    public AiAnalysisEntity(@NonNull String newsId, String summary, String sentiment, int confidenceScore, String reason) {
        this.newsId = newsId != null ? newsId : "";
        this.summary = summary;
        this.sentiment = sentiment;
        this.confidenceScore = confidenceScore;
        this.reason = reason;
    }

    @Ignore
    public AiAnalysisEntity(int unusedAnalysisId, @NonNull String newsId, String summary, String sentiment, int confidenceScore, String reason) {
        this(newsId, summary, sentiment, confidenceScore, reason);
    }

    @NonNull
    public String getNewsId() {
        return newsId;
    }

    public void setNewsId(@NonNull String newsId) {
        this.newsId = newsId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public int getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(int confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
