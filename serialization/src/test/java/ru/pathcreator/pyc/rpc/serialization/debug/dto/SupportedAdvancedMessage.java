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
public record SupportedAdvancedMessage(
        long snapshotId,
        UUID requestId,
        Instant createdAt,
        Optional<LocalDate> businessDate,
        Header header,
        List<Entry> entries,
        Set<String> symbols,
        Deque<ZoneOffset> offsets,
        List<byte[]> attachments,
        List<BigDecimal> prices,
        double[] riskCurve,
        int[] levels,
        Map<UUID, FixedAccount> accountsById,
        Map<LocalDate, YearMonth> bucketByDate,
        @RpcFixedLength(16) String source,
        byte[] payload
) {
    @SbeSerializable
    public record Header(
            long accountId,
            int partition,
            @RpcFixedLength(12) String venue,
            @RpcFixedLength(8) byte[] token
    ) {
    }

    @SbeSerializable
    public record Entry(
            long entryId,
            int quantity,
            double price,
            SupportedSide side,
            LocalDate deliveryDate,
            String comment,
            byte[] rawPayload,
            PriceMeta meta
    ) {
    }

    @SbeSerializable
    public record PriceMeta(
            long sequence,
            @RpcFixedLength(10) String venueCode
    ) {
    }

    @SbeSerializable
    public record FixedAccount(
            long accountId,
            @RpcFixedLength(12) String desk
    ) {
    }
}