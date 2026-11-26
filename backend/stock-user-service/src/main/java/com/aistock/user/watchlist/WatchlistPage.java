package com.aistock.user.watchlist;
import java.util.List;
public record WatchlistPage(List<WatchlistItem> items,int page,int size,long total){}
