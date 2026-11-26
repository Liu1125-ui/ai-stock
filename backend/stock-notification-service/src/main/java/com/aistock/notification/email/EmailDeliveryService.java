package com.aistock.notification.email;

import com.aistock.common.BusinessException;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class EmailDeliveryService {
    private final EmailDeliveryRepository repository;
    private final MailPayloadCodec codec;
    private final MailTransport transport;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public EmailDeliveryService(JdbcTemplate jdbc, MailPayloadCodec codec, MailTransport transport, Clock clock) {
        repository = new EmailDeliveryRepository(jdbc); this.codec = codec; this.transport = transport; this.clock = clock;
        transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public DeliveryView accept(EmailCommand supplied) {
        EmailCommand command = validate(supplied);
        String digest = codec.digest(command);
        var existing = repository.find(command.requestId());
        if (existing.isPresent()) return compare(existing.get(), digest);
        if (!transport.available()) throw new BusinessException("EMAIL_SERVICE_UNAVAILABLE", "邮件服务尚未配置", HttpStatus.SERVICE_UNAVAILABLE);
        if (!command.expiresAt().isAfter(clock.instant())) throw invalid();
        return transaction.execute(status -> {
            repository.insert(command, digest, codec.encrypt(command), clock.instant());
            return compare(repository.find(command.requestId()).orElseThrow(), digest);
        });
    }
    public DeliveryView find(UUID id) {
        return repository.find(id).orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "发送记录不存在", HttpStatus.NOT_FOUND)).view();
    }
    private DeliveryView compare(EmailDeliveryRepository.Row row, String digest) {
        if (!java.security.MessageDigest.isEqual(row.digest().getBytes(java.nio.charset.StandardCharsets.US_ASCII), digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new BusinessException("REQUEST_CONFLICT", "请求标识已用于其他内容", HttpStatus.CONFLICT);
        return row.view();
    }
    private EmailCommand validate(EmailCommand command) {
        if (command == null || command.requestId() == null || command.challengeId() == null || command.expiresAt() == null
                || command.email() == null || command.email().length() > 254 || command.email().chars().anyMatch(c -> c > 127 || Character.isISOControl(c))
                || command.code() == null || !command.code().matches("[0-9]{6}")) throw invalid();
        String email = command.email().strip().toLowerCase(Locale.ROOT);
        try { new jakarta.mail.internet.InternetAddress(email, true).validate(); }
        catch (jakarta.mail.internet.AddressException error) { throw invalid(); }
        if (!email.contains("@") || email.endsWith("@") || !email.substring(email.indexOf('@') + 1).contains(".")) throw invalid();
        return new EmailCommand(command.requestId(), command.challengeId(), email, command.code(), command.expiresAt());
    }
    private BusinessException invalid() { return new BusinessException("VALIDATION_FAILED", "邮件请求参数无效", HttpStatus.BAD_REQUEST); }
}
