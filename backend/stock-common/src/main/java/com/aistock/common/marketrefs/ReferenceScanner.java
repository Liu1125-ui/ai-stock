package com.aistock.common.marketrefs;
import javax.sql.DataSource;import java.sql.*;import java.time.Clock;import java.util.*;import java.util.concurrent.Semaphore;
import static com.aistock.common.marketrefs.MarketReferenceRecords.*;
/** Bounded technical scanner: static local schema only; no business content leaves JDBC. */
public final class ReferenceScanner {
 public record Limits(int maxRows,long maxJsonBytes){public Limits{if(maxRows<1||maxRows>1000||maxJsonBytes<1||maxJsonBytes>8L*1024*1024)throw new IllegalArgumentException("Invalid reference limits");}}
 private final DataSource data;private final String owner;private final List<Source> sources;private final Clock clock;private final Limits limits;private final Semaphore permit=new Semaphore(1);
 public ReferenceScanner(DataSource data,String owner,List<Source> sources,Clock clock){this(data,owner,sources,clock,new Limits(1000,8L*1024*1024));}
 public ReferenceScanner(DataSource data,String owner,List<Source> sources,Clock clock,Limits limits){if(!MarketReferenceInput.OWNERS.contains(owner)||sources==null||sources.isEmpty()||sources.size()>32||sources.stream().map(Source::category).distinct().count()!=sources.size())throw new IllegalArgumentException("Invalid owner sources");this.data=Objects.requireNonNull(data);this.owner=owner;this.sources=List.copyOf(sources);this.clock=Objects.requireNonNull(clock);this.limits=Objects.requireNonNull(limits);}
 public OwnerResult scan(String version){MarketReferenceInput.requireVersion(version);var at=clock.instant();if(!permit.tryAcquire())return unknown(version,at,"REFERENCE_QUERY_BUSY");long deadline=System.nanoTime()+3_000_000_000L;
  try(Connection c=data.getConnection()){
   c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
   var groups=new ArrayList<Group>();
   try{for(var source:sources){if(System.nanoTime()>=deadline){groups.add(failure(source,"REFERENCE_QUERY_BUDGET"));continue;}try{groups.add(count(c,source,version,deadline));}catch(SQLException e){groups.add(failure(source,"REFERENCE_QUERY_UNAVAILABLE"));}}}finally{c.rollback();}
   boolean complete=groups.stream().allMatch(g->"COMPLETE".equals(g.state()));return new OwnerResult(owner,version,at,complete?"COMPLETE":"PARTIAL",complete?null:"REFERENCE_PARTIAL",groups);
  }catch(SQLException e){return unknown(version,at,"REFERENCE_QUERY_UNAVAILABLE");}finally{permit.release();}
 }
 private Group count(Connection c,Source s,String version,long deadline)throws SQLException{
  var columns=new ArrayList<>(s.directColumns());columns.addAll(s.jsonColumns());
  String size=s.jsonColumns().isEmpty()?"0":String.join("+",s.jsonColumns().stream().map(v->"coalesce(octet_length("+v+"),0)").toList());
  String invalid=s.jsonColumns().isEmpty()?"0":"case when "+String.join(" or ",s.jsonColumns().stream().map(v->"("+v+" is not null and json_valid("+v+")=0)").toList())+" then 1 else 0 end";
  String meta="select count(*) row_count,coalesce(sum("+size+"),0) json_bytes,coalesce(sum("+invalid+"),0) invalid_json from(select "+String.join(",",columns)+" from "+s.table()+" limit "+(limits.maxRows()+1)+") bounded_refs";
  long rows,bytes,bad;try(var st=c.prepareStatement(meta)){st.setQueryTimeout(1);try(var r=st.executeQuery()){if(!r.next())throw new SQLException("Missing aggregate");rows=r.getLong("row_count");bytes=r.getLong("json_bytes");bad=r.getLong("invalid_json");}}
  if(rows>limits.maxRows())return failure(s,"REFERENCE_ROW_LIMIT");if(bytes>limits.maxJsonBytes())return failure(s,"REFERENCE_JSON_LIMIT");if(bad>0)return failure(s,"REFERENCE_JSON_INVALID");
  if(rows==0)return success(s,0);if(System.nanoTime()>=deadline)return failure(s,"REFERENCE_QUERY_BUDGET");
  var conditions=new ArrayList<String>();s.directColumns().forEach(v->conditions.add(v+"=?"));s.jsonColumns().forEach(v->conditions.add("json_search("+v+",'one',?) is not null"));
  try(var st=c.prepareStatement("select count(*) row_count from "+s.table()+" where "+String.join(" or ",conditions))){st.setQueryTimeout(1);for(int i=1;i<=conditions.size();i++)st.setString(i,version);try(var r=st.executeQuery()){if(!r.next())throw new SQLException("Missing count");long count=r.getLong("row_count");if(count<0||count>rows)throw new SQLException("Inconsistent bounded count");return success(s,count);}}
 }
 private OwnerResult unknown(String v,java.time.Instant at,String reason){return new OwnerResult(owner,v,at,"UNKNOWN",reason,List.of());}
 private Group success(Source s,long rows){return new Group(s.category(),s.label(),"COMPLETE",Long.toString(rows),null);}
 private Group failure(Source s,String reason){return new Group(s.category(),s.label(),"UNKNOWN",null,reason);}
}
