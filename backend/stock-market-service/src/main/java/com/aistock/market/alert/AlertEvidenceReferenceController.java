package com.aistock.market.alert;
import com.aistock.common.*;import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.http.HttpStatus;
/** Admission metadata only: no price, calendar, body, derivation, or publication. */
@RestController public final class AlertEvidenceReferenceController {
 private final JdbcTemplate jdbc;public AlertEvidenceReferenceController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/internal/v1/market/alert-evidence-references/{id}") public ApiResponse<?> reference(@PathVariable UUID id,@RequestHeader(value="X-Trace-Id",required=false)String trace){var rows=jdbc.queryForList("select evidence_id,instrument_id,raw_version_id from alert_evidence_version where evidence_id=?",id.toString());if(rows.size()!=1)throw new BusinessException("RESOURCE_NOT_FOUND","预警引用不存在",HttpStatus.NOT_FOUND);var row=rows.get(0);return ApiResponse.success(Map.of("evidenceId",row.get("evidence_id"),"instrumentId",row.get("instrument_id"),"rawVersionId",row.get("raw_version_id")),TraceIds.normalize(trace));}
}
