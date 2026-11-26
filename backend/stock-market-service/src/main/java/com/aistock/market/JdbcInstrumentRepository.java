package com.aistock.market;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInstrumentRepository implements InstrumentRepository {
    private final JdbcTemplate jdbc;

    public JdbcInstrumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<InstrumentView> findById(String instrumentId) {
        var rows = jdbc.query("SELECT instrument_id, ticker, display_name, demo_only FROM instrument WHERE instrument_id = ?",
                (rs, row) -> new InstrumentView(rs.getString("instrument_id"), rs.getString("ticker"),
                        rs.getString("display_name"), rs.getBoolean("demo_only")), instrumentId);
        return rows.stream().findFirst();
    }
}
