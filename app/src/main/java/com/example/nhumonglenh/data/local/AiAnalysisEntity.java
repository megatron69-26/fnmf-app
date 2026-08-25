package com.example.nhumonglenh.data.local;

import androidx.room.Entity;
import androidx.room.ForeignKey;
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
    indices = {@Index("newsId")}
)
public class AiAnalysisEntity {

    @PrimaryKey(autoGenerate = true)
    private int analysisId;

    private String newsId;
    private String summary;
    private String sentiment;
    private int confidenceScore;
    private String reason;

    public AiAnalysisEntity() {
    }

    public AiAnalysisEntity(int analysisId, String newsId, String summary, String sentiment, int confidenceScore, String reason) {
        this.analysisId = analysisId;
        this.newsId = newsId;
        this.summary = summary;
        this.sentiment = sentiment;
        this.confidenceScore = confidenceScore;
        this.reason = reason;
    }

    public int getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(int analysisId) {
        this.analysisId = analysisId;
    }

    public String getNewsId() {
        return newsId;
    }

    public void setNewsId(String newsId) {
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
