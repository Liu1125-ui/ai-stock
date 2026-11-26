package com.aistock.market.adminquery;

import static com.aistock.market.adminquery.AdminMarketRecords.*;
import com.aistock.common.adminmarket.AdminMarketQuery;
import com.aistock.common.adminmarket.AdminMarketQuery.DetailFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class CoverageDiagnostics {
    public static final int DATE_SAMPLE_LIMIT=20;
    public Diagnostics calculate(DetailFilter window,VersionBounds bounds,List<DiagnosticBar> bars,List<CalendarDay> calendar) {
        if(window==null||window.from()==null||window.to()==null||window.to().isBefore(window.from())||ChronoUnit.DAYS.between(window.from(),window.to())>=AdminMarketQuery.MAX_DAYS||bounds==null||bounds.from()==null||bounds.to()==null||bounds.to().isBefore(bounds.from())||bars==null||calendar==null)throw new IllegalArgumentException("Uninterpretable version bounds or evidence");
        long requested=ChronoUnit.DAYS.between(window.from(),window.to())+1;
        LocalDate from=window.from().isAfter(bounds.from())?window.from():bounds.from();
        LocalDate to=window.to().isBefore(bounds.to())?window.to():bounds.to();
        if(from.isAfter(to))return new Diagnostics("OUTSIDE_COVERAGE","VERSION_ATTACHED",null,null,n(requested),"0","0","0","0","0",finding(Set.of()),finding(Set.of()),finding(Set.of()));
        var rows=new HashMap<LocalDate,DiagnosticBar>();
        for(var bar:bars) {
            if(bar==null||bar.date()==null)throw new IllegalArgumentException("Undated price evidence");
            if(within(bar.date(),from,to)&&rows.putIfAbsent(bar.date(),bar)!=null)throw new IllegalArgumentException("Duplicate price evidence");
        }
        var days=new HashMap<LocalDate,Boolean>();
        for(var day:calendar) {
            if(day==null||day.date()==null)throw new IllegalArgumentException("Undated calendar evidence");
            if(within(day.date(),from,to)&&days.putIfAbsent(day.date(),day.open())!=null)throw new IllegalArgumentException("Duplicate calendar evidence");
        }
        var missing=new TreeSet<LocalDate>();var closedRows=new TreeSet<LocalDate>();var invalid=new TreeSet<LocalDate>();
        long traded=0,suspended=0,open=0,unknown=0;
        for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)) {
            Boolean opened=days.get(d);var bar=rows.get(d);
            if(opened==null)unknown++;else if(opened) {open++;if(bar==null)missing.add(d);}else if(bar!=null)closedRows.add(d);
            if(bar!=null) {if(bar.tradeStatus()==1)traded++;else if(bar.tradeStatus()==0)suspended++;if(!valid(bar))invalid.add(d);}
        }
        String state=!invalid.isEmpty()||!closedRows.isEmpty()?"ISSUES":!missing.isEmpty()?"NEEDS_REVIEW":unknown>0?"INCOMPLETE_EVIDENCE":"NO_RECORDED_ISSUES";
        return new Diagnostics(state,"VERSION_ATTACHED",from,to,n(requested-ChronoUnit.DAYS.between(from,to)-1),n(rows.size()),n(traded),n(suspended),n(open),n(unknown),finding(missing),finding(closedRows),finding(invalid));
    }
    private static boolean valid(DiagnosticBar b) {
        if(!positive(b.open())||!positive(b.high())||!positive(b.low())||!positive(b.close())||b.high().compareTo(b.open().max(b.close()).max(b.low()))<0||b.low().compareTo(b.open().min(b.close()).min(b.high()))>0||b.tradeStatus()!=0&&b.tradeStatus()!=1)return false;
        if(b.volumeShares()!=null&&b.volumeShares().signum()<0||b.amountYuan()!=null&&b.amountYuan().signum()<0)return false;
        return b.tradeStatus()!=1||positive(b.volumeShares())&&positive(b.amountYuan());
    }
    private static boolean positive(BigDecimal v) {return v!=null&&v.signum()>0;}
    private static boolean within(LocalDate d,LocalDate from,LocalDate to) {return !d.isBefore(from)&&!d.isAfter(to);}
    private static String n(long n) {return Long.toString(n);}
    private static DateFinding finding(Set<LocalDate> dates) {return new DateFinding(n(dates.size()),dates.stream().sorted().limit(DATE_SAMPLE_LIMIT).toList(),dates.size()>DATE_SAMPLE_LIMIT);}
}
