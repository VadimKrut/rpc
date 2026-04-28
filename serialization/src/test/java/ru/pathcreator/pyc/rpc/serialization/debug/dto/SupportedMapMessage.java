package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

@SbeSerializable
public record SupportedMapMessage(
        long batchId,
        Map<String, Integer> quantitiesBySymbol,
        LinkedHashMap<SupportedSide, String> labelsBySide,
        SortedMap<UUID, BigDecimal> priceById,
        NavigableMap<LocalDate, YearMonth> deliveryByDate,
        TreeMap<YearMonth, ZoneOffset> offsetsByMonth
) {
}