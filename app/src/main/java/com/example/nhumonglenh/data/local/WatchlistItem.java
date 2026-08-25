package com.example.nhumonglenh.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "watchlist_table")
public class WatchlistItem {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String symbol;
    private double price;
    private double change24h;

    public WatchlistItem() {
    }

    public WatchlistItem(String symbol, double price, double change24h) {
        this.symbol = symbol;
        this.price = price;
        this.change24h = change24h;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getChange24h() {
        return change24h;
    }

    public void setChange24h(double change24h) {
        this.change24h = change24h;
    }
}
