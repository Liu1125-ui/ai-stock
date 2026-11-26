package com.aistock.common.adminrefresh;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import static com.aistock.common.adminrefresh.AdminMarketRefreshRecords.*;

/** Shared strict boundary validation; never performs I/O or authorizes a request. */
public final class AdminMarketRefreshInput {
    public static final String BASE = "/api/v1/admin/market-data";
    public static final String STOCK_PATTERN = "(SH|SZ)[0-9]{6}";
    public static final String UUID_PATTERN = "[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}";
    public static final int MAX_BODY_BYTES = 4096;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_CURSOR_LENGTH = 768;
    public static final Set<String> REASONS = Set.of("RECHECK_DATA", "RECOVER_UPDATE");
    private static final Set<String> COMMAND_FIELDS = Set.of(
        "operationId", "baseVersionId", "expectedHeadRevision", "targetDate", "reasonCode");
    private static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private AdminMarketRefreshInput() {}

    public static Command parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BODY_BYTES) throw invalid();
        try {
            JsonNode root = MAPPER.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(bytes);
            if (root == null || !root.isObject() || root.size() != COMMAND_FIELDS.size()) throw invalid();
            var fields = root.fieldNames();
            while (fields.hasNext()) if (!COMMAND_FIELDS.contains(fields.next())) throw invalid();
            UUID operation = id(text(root, "operationId"));
            UUID version = id(text(root, "baseVersionId"));
            String revision = text(root, "expectedHeadRevision");
            revision(revision);
            String rawDate = text(root, "targetDate");
            if (!rawDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
            LocalDate date = LocalDate.parse(rawDate);
            String reason = text(root, "reasonCode");
            if (!REASONS.contains(reason)) throw invalid();
            return new Command(operation, version, revision, date, reason);
        } catch (IOException | DateTimeParseException e) {
            throw invalid();
        }
    }

    public static UUID id(String value) {
        if (value == null || !value.matches(UUID_PATTERN)) throw invalid();
        return UUID.fromString(value);
    }

    public static long revision(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { throw invalid(); }
    }

    public static String instrument(String value) {
        if (value == null || !value.matches(STOCK_PATTERN)) throw invalid();
        return value;
    }

    public static HistoryQuery history(MultiValueMap<String, String> query) {
        if (query == null || query.size() > 2) throw invalid();
        query.forEach((name, values) -> {
            if (!Set.of("cursor", "size").contains(name) || values == null
                || values.size() != 1 || values.get(0) == null) throw invalid();
        });
        int size = DEFAULT_SIZE;
        String rawSize = query.getFirst("size");
        if (rawSize != null) {
            if (!rawSize.matches("[1-9][0-9]?")) throw invalid();
            size = Integer.parseInt(rawSize);
            if (size > MAX_SIZE) throw invalid();
        }
        String cursor = query.getFirst("cursor");
        if (cursor != null && (cursor.length() > MAX_CURSOR_LENGTH
            || !cursor.matches("[A-Za-z0-9_-]+"))) throw invalid();
        return new HistoryQuery(cursor, size);
    }

    public static boolean writePath(String path) {
        return path != null && path.matches(BASE + "/" + STOCK_PATTERN + "/refresh-operations");
    }

    public static boolean readPath(String path) {
        return path != null && (writePath(path)
            || path.matches(BASE + "/" + STOCK_PATTERN + "/refresh-options")
            || path.matches(BASE + "/refresh-operations/" + UUID_PATTERN));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }

    public static BusinessException invalid() {
        return new BusinessException("ADMIN_REFRESH_REQUEST_INVALID",
            "行情重取条件不正确，请核对后重试", HttpStatus.BAD_REQUEST);
    }
}