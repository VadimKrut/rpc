package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.math.BigDecimal;
import java.math.BigInteger;

@SbeSerializable
public record SupportedBigNumberMessage(
        long id,
        BigInteger sequence,
        BigDecimal price
) {
}