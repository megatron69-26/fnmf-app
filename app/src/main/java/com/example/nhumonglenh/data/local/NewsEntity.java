package com.example.nhumonglenh.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "news_table")
public class NewsEntity {

    @PrimaryKey
    @NonNull
    private String newsId;

    private String title;
    private String url;
    private long publishedAt;
    private String source = "";
    private String author = "";
    private String publishedAtRaw = "";
    private String imageUrl = "";
    private String summary = "";
    private String sentiment = "neutral";
    private int confidence = 0;
    private String bulletPoints = "";

    private String originalTitle = "";
    private String originalSummary = "";
    private String displayTitleVi = "";
    private String displaySummaryVi = "";
    private String bulletPointsVi = "";
    private String publisher = "";

    public NewsEntity() {
        this.newsId = "";
    }

    @Ignore
    public NewsEntity(@NonNull String newsId, String title, String url, long publishedAt) {
        this.newsId = newsId;
        this.title = title;
        this.url = url;
        this.publishedAt = publishedAt;
    }

    @Ignore
    public NewsEntity(
            @NonNull String newsId,
            String title,
            String url,
            long publishedAt,
            String source,
            String author,
            String publishedAtRaw,
            String imageUrl,
            String summary,
            String sentiment,
            int confidence,
            String bulletPoints
    ) {
        this(newsId, title, url, publishedAt, source, author, publishedAtRaw, imageUrl, summary, sentiment, confidence, bulletPoints, "", "", "", "", "", "");
    }

    @Ignore
    public NewsEntity(
            @NonNull String newsId,
            String title,
            String url,
            long publishedAt,
            String source,
            String author,
            String publishedAtRaw,
            String imageUrl,
            String summary,
            String sentiment,
            int confidence,
            String bulletPoints,
            String originalTitle,
            String originalSummary,
            String displayTitleVi,
            String displaySummaryVi,
            String bulletPointsVi,
            String publisher
    ) {
        this.newsId = newsId;
        this.title = title != null ? title : "";
        this.url = url != null ? url : "";
        this.publishedAt = publishedAt;
        this.source = source != null ? source : "";
        this.author = author != null ? author : "";
        this.publishedAtRaw = publishedAtRaw != null ? publishedAtRaw : "";
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.summary = summary != null ? summary : "";
        this.sentiment = sentiment != null ? sentiment : "neutral";
        this.confidence = confidence;
        this.bulletPoints = bulletPoints != null ? bulletPoints : "";
        this.originalTitle = originalTitle != null ? originalTitle : "";
        this.originalSummary = originalSummary != null ? originalSummary : "";
        this.displayTitleVi = displayTitleVi != null ? displayTitleVi : "";
        this.displaySummaryVi = displaySummaryVi != null ? displaySummaryVi : "";
        this.bulletPointsVi = bulletPointsVi != null ? bulletPointsVi : "";
        this.publisher = publisher != null ? publisher : "";
    }

    @NonNull
    public String getNewsId() {
        return newsId;
    }

    public void setNewsId(@NonNull String newsId) {
        this.newsId = newsId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(long publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source != null ? source : "";
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author != null ? author : "";
    }

    public String getPublishedAtRaw() {
        return publishedAtRaw;
    }

    public void setPublishedAtRaw(String publishedAtRaw) {
        this.publishedAtRaw = publishedAtRaw != null ? publishedAtRaw : "";
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl != null ? imageUrl : "";
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary != null ? summary : "";
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment != null ? sentiment : "neutral";
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public String getBulletPoints() {
        return bulletPoints;
    }

    public void setBulletPoints(String bulletPoints) {
        this.bulletPoints = bulletPoints != null ? bulletPoints : "";
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle != null ? originalTitle : "";
    }

    public String getOriginalSummary() {
        return originalSummary;
    }

    public void setOriginalSummary(String originalSummary) {
        this.originalSummary = originalSummary != null ? originalSummary : "";
    }

    public String getDisplayTitleVi() {
        return displayTitleVi;
    }

    public void setDisplayTitleVi(String displayTitleVi) {
        this.displayTitleVi = displayTitleVi != null ? displayTitleVi : "";
    }

    public String getDisplaySummaryVi() {
        return displaySummaryVi;
    }

    public void setDisplaySummaryVi(String displaySummaryVi) {
        this.displaySummaryVi = displaySummaryVi != null ? displaySummaryVi : "";
    }

    public String getBulletPointsVi() {
        return bulletPointsVi;
    }

    public void setBulletPointsVi(String bulletPointsVi) {
        this.bulletPointsVi = bulletPointsVi != null ? bulletPointsVi : "";
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher != null ? publisher : "";
    }
}
