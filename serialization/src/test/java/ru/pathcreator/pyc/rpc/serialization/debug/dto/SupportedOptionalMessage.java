package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@SbeSerializable
public record SupportedOptionalMessage(
        long id,
        Optional<String> name,
        Optional<UUID> externalId,
        Optional<LocalDate> tradeDate,
        Optional<BigDecimal> amount
) {
}