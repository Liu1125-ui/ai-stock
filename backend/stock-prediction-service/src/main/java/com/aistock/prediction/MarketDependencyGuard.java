package com.aistock.prediction;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.aistock.common.BusinessException;
import java.util.ArrayList;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MarketDependencyGuard {
    private final String resource;

    @Autowired
    public MarketDependencyGuard(@Value("${aistock.market.circuit.threshold:3}") int threshold,
            @Value("${aistock.market.circuit.window-seconds:5}") int windowSeconds) {
        this("market.lookup", threshold, windowSeconds);
    }

    MarketDependencyGuard(String resource, int threshold, int windowSeconds) {
        if (threshold < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Circuit threshold and window must be positive");
        }
        this.resource = resource;
        var rule = new DegradeRule(resource)
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT)
                // Sentinel compares error count strictly greater than count.
                .setCount(threshold - 0.5).setTimeWindow(windowSeconds)
                .setMinRequestAmount(threshold).setStatIntervalMs(30000);
        var rules = new ArrayList<>(DegradeRuleManager.getRules());
        rules.removeIf(existing -> resource.equals(existing.getResource()));
        rules.add(rule);
        DegradeRuleManager.loadRules(rules);
    }

    public <T> T invoke(Supplier<T> operation) {
        Entry entry = null;
        try {
            entry = SphU.entry(resource, EntryType.OUT);
            try {
                return operation.get();
            } catch (RuntimeException error) {
                // A missing instrument is a valid business response, not an outage.
                if (!(error instanceof feign.FeignException.NotFound)) {
                    Tracer.traceEntry(error, entry);
                }
                throw error;
            }
        } catch (BlockException blocked) {
            throw new BusinessException("DEPENDENCY_CIRCUIT_OPEN", "行情服务正在恢复，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            if (entry != null) entry.exit();
        }
    }
}
