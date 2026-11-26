package com.aistock.market;

import java.util.Optional;

public interface InstrumentRepository {
    Optional<InstrumentView> findById(String instrumentId);
}
