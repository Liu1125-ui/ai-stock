package com.aistock.user.watchlist;
import java.time.Instant;
public record WatchlistItem(String instrumentId,Instant createdAt){}
