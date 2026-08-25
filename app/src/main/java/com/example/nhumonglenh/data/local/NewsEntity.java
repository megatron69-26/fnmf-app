package com.example.nhumonglenh.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "news_table")
public class NewsEntity {

    @PrimaryKey
    @NonNull
    private String newsId;

    private String title;
    private String url;
    private long publishedAt;

    public NewsEntity() {
        this.newsId = "";
    }

    public NewsEntity(@NonNull String newsId, String title, String url, long publishedAt) {
        this.newsId = newsId;
        this.title = title;
        this.url = url;
        this.publishedAt = publishedAt;
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
}
