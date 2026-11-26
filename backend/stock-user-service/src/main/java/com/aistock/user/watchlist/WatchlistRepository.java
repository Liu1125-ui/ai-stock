package com.aistock.user.watchlist;
import java.time.Instant;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository public class WatchlistRepository {
 private final JdbcTemplate jdbc;public WatchlistRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public WatchlistItem add(UUID userId,String instrumentId,Instant now){jdbc.update("INSERT INTO watchlist(user_id,instrument_id,created_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE instrument_id=instrument_id",userId.toString(),instrumentId,java.sql.Timestamp.from(now));return jdbc.queryForObject("SELECT instrument_id,created_at FROM watchlist WHERE user_id=? AND instrument_id=?",(rs,n)->new WatchlistItem(rs.getString(1),rs.getTimestamp(2).toInstant()),userId.toString(),instrumentId);}
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public WatchlistItem addAndQueue(UUID userId,String instrumentId,Instant now){
  var item=add(userId,instrumentId,now);
  new WatchlistMarketQueue(jdbc).enqueue(instrumentId,now);
  return item;
 }
 public void delete(UUID userId,String instrumentId){jdbc.update("DELETE FROM watchlist WHERE user_id=? AND instrument_id=?",userId.toString(),instrumentId);}
 public long count(UUID userId){return jdbc.queryForObject("SELECT COUNT(*) FROM watchlist WHERE user_id=?",Long.class,userId.toString());}
 public WatchlistPage list(UUID userId,int page,int size){var items=jdbc.query("SELECT instrument_id,created_at FROM watchlist WHERE user_id=? ORDER BY created_at DESC,instrument_id LIMIT ? OFFSET ?",(rs,n)->new WatchlistItem(rs.getString(1),rs.getTimestamp(2).toInstant()),userId.toString(),size,(page-1)*size);return new WatchlistPage(items,page,size,count(userId));}
}
