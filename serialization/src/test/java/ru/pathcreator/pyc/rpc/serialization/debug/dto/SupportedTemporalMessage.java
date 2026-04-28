package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.time.*;
import java.util.UUID;

@SbeSerializable
public record SupportedTemporalMessage(
        UUID id,
        Instant createdAt,
        LocalDate tradeDate,
        LocalTime tradeTime,
        LocalDateTime bookedAt,
        OffsetDateTime settledAt,
        OffsetTime venueTime,
        Duration ttl,
        Period rollPeriod,
        Year expiryYear,
        YearMonth contractMonth,
        MonthDay couponDay,
        ZoneOffset offset
) {
}