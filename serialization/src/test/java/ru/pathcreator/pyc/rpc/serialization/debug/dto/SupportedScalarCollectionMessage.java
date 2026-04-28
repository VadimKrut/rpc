package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

@SbeSerializable
public record SupportedScalarCollectionMessage(
        long batchId,
        List<Integer> quantities,
        Set<UUID> ids,
        SortedSet<LocalDate> tradeDates,
        NavigableSet<YearMonth> deliveryMonths,
        Queue<SupportedSide> sides,
        Deque<BigDecimal> prices,
        ArrayList<BigInteger> bigIntegers,
        LinkedHashSet<String> names,
        ArrayDeque<ZoneOffset> offsets,
        List<byte[]> payloads,
        List<Instant> instants,
        boolean[] flags,
        char[] grades,
        short[] shortCodes,
        int[] intCodes,
        long[] longCodes,
        float[] floatPrices,
        double[] doublePrices,
        Integer[] quantityArray,
        String[] nameArray,
        byte[][] payloadArray
) {
}