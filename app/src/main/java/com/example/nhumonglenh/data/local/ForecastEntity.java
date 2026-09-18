package com.example.nhumonglenh.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "forecast_table")
public class ForecastEntity {

    @PrimaryKey
    @NonNull
    private String symbol;

    private String recommendation;
    private Integer confidenceScore;
    private Double currentPrice;
    private Double supportLevel;
    private Double resistanceLevel;
    private String keyDriversJson;
    private String trendPrediction;
    private String technicalOutlook;
    private String fundamentalOutlook;
    private String analysisSource;
    private String createdAt;
    private String timeframe;
    private Boolean stale;
    private String aiShard;
    private Integer candleCount;
    private long cachedAt;

    public ForecastEntity() {
        this.symbol = "MARKET";
    }

    @Ignore
    public ForecastEntity(
            @NonNull String symbol,
            String recommendation,
            Integer confidenceScore,
            Double currentPrice,
            Double supportLevel,
            Double resistanceLevel,
            String keyDriversJson,
            String trendPrediction,
            String technicalOutlook,
            String fundamentalOutlook,
            String analysisSource,
            String createdAt,
            String timeframe,
            Boolean stale,
            String aiShard,
            Integer candleCount,
            long cachedAt
    ) {
        this.symbol = symbol;
        this.recommendation = recommendation;
        this.confidenceScore = confidenceScore;
        this.currentPrice = currentPrice;
        this.supportLevel = supportLevel;
        this.resistanceLevel = resistanceLevel;
        this.keyDriversJson = keyDriversJson;
        this.trendPrediction = trendPrediction;
        this.technicalOutlook = technicalOutlook;
        this.fundamentalOutlook = fundamentalOutlook;
        this.analysisSource = analysisSource;
        this.createdAt = createdAt;
        this.timeframe = timeframe;
        this.stale = stale;
        this.aiShard = aiShard;
        this.candleCount = candleCount;
        this.cachedAt = cachedAt;
    }

    @NonNull
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(@NonNull String symbol) {
        this.symbol = symbol;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public Integer getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Integer confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(Double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public Double getSupportLevel() {
        return supportLevel;
    }

    public void setSupportLevel(Double supportLevel) {
        this.supportLevel = supportLevel;
    }

    public Double getResistanceLevel() {
        return resistanceLevel;
    }

    public void setResistanceLevel(Double resistanceLevel) {
        this.resistanceLevel = resistanceLevel;
    }

    public String getKeyDriversJson() {
        return keyDriversJson;
    }

    public void setKeyDriversJson(String keyDriversJson) {
        this.keyDriversJson = keyDriversJson;
    }

    public String getTrendPrediction() {
        return trendPrediction;
    }

    public void setTrendPrediction(String trendPrediction) {
        this.trendPrediction = trendPrediction;
    }

    public String getTechnicalOutlook() {
        return technicalOutlook;
    }

    public void setTechnicalOutlook(String technicalOutlook) {
        this.technicalOutlook = technicalOutlook;
    }

    public String getFundamentalOutlook() {
        return fundamentalOutlook;
    }

    public void setFundamentalOutlook(String fundamentalOutlook) {
        this.fundamentalOutlook = fundamentalOutlook;
    }

    public String getAnalysisSource() {
        return analysisSource;
    }

    public void setAnalysisSource(String analysisSource) {
        this.analysisSource = analysisSource;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public Boolean getStale() {
        return stale;
    }

    public void setStale(Boolean stale) {
        this.stale = stale;
    }

    public String getAiShard() {
        return aiShard;
    }

    public void setAiShard(String aiShard) {
        this.aiShard = aiShard;
    }

    public Integer getCandleCount() {
        return candleCount;
    }

    public void setCandleCount(Integer candleCount) {
        this.candleCount = candleCount;
    }

    public long getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(long cachedAt) {
        this.cachedAt = cachedAt;
    }
}
