package com.aistock.market.version;
import com.aistock.common.BusinessException;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository public class RevisionEventRepository {
 private final JdbcTemplate jdbc;public RevisionEventRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Page events(long after,int limit){if(after<0||limit<1||limit>50)throw new BusinessException("REVISION_REQUEST_INVALID","行情修订查询参数无效",HttpStatus.BAD_REQUEST);return new Page(List.copyOf(jdbc.query("select event_id,instrument_id,raw_version_id from market_revision_event where event_id>? order by event_id limit ?",(r,n)->new Event(r.getLong(1),r.getString(2),r.getString(3)),after,limit)));}
 public record Event(long eventId,String instrumentId,String rawVersionId){}public record Page(List<Event> items){}
}
