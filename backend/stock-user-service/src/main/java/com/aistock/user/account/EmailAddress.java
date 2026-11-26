package com.aistock.user.account;
import com.aistock.common.BusinessException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class EmailAddress {
    private static final Pattern LOCAL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private EmailAddress() { }
    public static String normalize(String raw) {
        if (raw == null || raw.chars().anyMatch(c -> c > 127 || Character.isISOControl(c))) throw invalid();
        String address = raw.strip().toLowerCase(Locale.ROOT);
        int at = address.indexOf('@');
        if (address.length() > 254 || at < 1 || at != address.lastIndexOf('@')) throw invalid();
        String local = address.substring(0, at);
        if (local.length() > 64 || !LOCAL.matcher(local).matches() || local.startsWith(".") || local.endsWith(".") || local.contains("..")) throw invalid();
        String[] labels = address.substring(at + 1).split("\\.", -1);
        if (labels.length < 2) throw invalid();
        for (String label : labels) if (!LABEL.matcher(label).matches()) throw invalid();
        return address;
    }
    private static BusinessException invalid() { return new BusinessException("VALIDATION_FAILED", "请输入有效的邮箱地址", HttpStatus.BAD_REQUEST); }
}
