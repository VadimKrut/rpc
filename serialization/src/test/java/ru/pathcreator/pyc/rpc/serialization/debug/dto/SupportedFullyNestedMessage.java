package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.annotation.RpcFixedLength;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

@SbeSerializable
public record SupportedFullyNestedMessage(
        long snapshotId,
        UUID requestId,
        Envelope envelope,
        List<Batch> batches,
        Map<UUID, AccountBook> booksById,
        Optional<Instant> expiresAt,
        @RpcFixedLength(12) String sourceSystem,
        byte[] payload
) {
    @SbeSerializable
    public record Envelope(
            String comment,
            Map<SupportedSide, String> labelsBySide,
            List<AuditEvent> auditTrail,
            Deque<ZoneOffset> offsets
    ) {
    }

    @SbeSerializable
    public record AuditEvent(
            long sequence,
            Instant createdAt,
            String message
    ) {
    }

    @SbeSerializable
    public record Batch(
            Header header,
            List<Line> lines,
            Map<LocalDate, PriceBucket> bucketsByDate,
            Set<String> tags
    ) {
    }

    @SbeSerializable
    public record Header(
            long batchId,
            UUID ownerId,
            Optional<LocalDate> businessDate,
            @RpcFixedLength(8) String venue
    ) {
    }

    @SbeSerializable
    public record Line(
            long lineId,
            String symbol,
            BigDecimal amount,
            List<Charge> charges,
            Map<YearMonth, String> notesByMonth
    ) {
    }

    @SbeSerializable
    public record Charge(
            String code,
            BigDecimal value
    ) {
    }

    @SbeSerializable
    public record PriceBucket(
            String bucketName,
            List<Charge> charges,
            Map<SupportedSide, BigDecimal> sidePrices
    ) {
    }

    @SbeSerializable
    public record AccountBook(
            String bookName,
            List<AuditEvent> changes,
            Map<LocalDate, PriceBucket> bucketsByDate
    ) {
    }
}