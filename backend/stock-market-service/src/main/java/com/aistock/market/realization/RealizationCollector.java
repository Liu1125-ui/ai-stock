package com.aistock.market.realization;
import static com.aistock.market.realization.RealizationRecords.*;
@FunctionalInterface public interface RealizationCollector {Source collect(Request request,Head head);}
