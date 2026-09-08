package com.example.nhumonglenh.data.local;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "watchlist_table")
public class WatchlistItem {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String symbol;
    private Double price;
    private Double change24h;
    private String userEmail = "";

    public WatchlistItem() {
    }

    @Ignore
    public WatchlistItem(String symbol, Double price, Double change24h) {
        this(symbol, price, change24h, "");
    }

    @Ignore
    public WatchlistItem(String symbol, Double price, Double change24h, String userEmail) {
        this.symbol = symbol;
        this.price = price;
        this.change24h = change24h;
        this.userEmail = userEmail != null ? userEmail : "";
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getChange24h() {
        return change24h;
    }

    public void setChange24h(Double change24h) {
        this.change24h = change24h;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
