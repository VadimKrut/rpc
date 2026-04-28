package ru.pathcreator.pyc.rpc.serialization.debug.support;

import ru.pathcreator.pyc.rpc.serialization.debug.dto.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class DebugSampleData {

    private DebugSampleData() {
    }

    public static List<DebugRoundTripCase<?>> supportedCases() {
        return List.of(
                new DebugRoundTripCase<>(SupportedPrimitiveScalarsMessage.class, primitiveScalars()),
                new DebugRoundTripCase<>(SupportedBoxedScalarsMessage.class, boxedScalars()),
                new DebugRoundTripCase<>(SupportedFixedLengthMessage.class, fixedLength()),
                new DebugRoundTripCase<>(SupportedVariableLengthMessage.class, variableLength()),
                new DebugRoundTripCase<>(SupportedNestedFixedMessage.class, nestedFixed()),
                new DebugRoundTripCase<>(SupportedRepeatingGroupMessage.class, repeatingGroup()),
                new DebugRoundTripCase<>(SupportedBigNumberMessage.class, bigNumber()),
                new DebugRoundTripCase<>(SupportedOptionalMessage.class, optionalMessage()),
                new DebugRoundTripCase<>(SupportedTemporalMessage.class, temporalMessage()),
                new DebugRoundTripCase<>(SupportedCollectionMessage.class, collectionMessage()),
                new DebugRoundTripCase<>(SupportedScalarCollectionMessage.class, scalarCollectionMessage()),
                new DebugRoundTripCase<>(SupportedMapMessage.class, mapMessage()),
                new DebugRoundTripCase<>(SupportedAdvancedMessage.class, advancedMessage()),
                new DebugRoundTripCase<>(SupportedFullyNestedMessage.class, fullyNestedMessage())
        );
    }

    private static SupportedPrimitiveScalarsMessage primitiveScalars() {
        return new SupportedPrimitiveScalarsMessage(
                (byte) 7,
                (short) 1024,
                42_4242,
                987_654_321L,
                17.25f,
                12345.6789d,
                true,
                'A'
        );
    }

    private static SupportedBoxedScalarsMessage boxedScalars() {
        return new SupportedBoxedScalarsMessage(
                (byte) 3,
                (short) 2048,
                73,
                123_456_789L,
                19.75f,
                999.001d,
                false,
                'Z',
                SupportedSide.SELL
        );
    }

    private static SupportedFixedLengthMessage fixedLength() {
        return new SupportedFixedLengthMessage(
                1001L,
                "EURUSD-FWD-1",
                fixedBytes("fixed-payload-sample", 32)
        );
    }

    private static SupportedVariableLengthMessage variableLength() {
        return new SupportedVariableLengthMessage(
                1002L,
                "variable-message-name",
                bytes("variable-payload-content")
        );
    }

    private static SupportedNestedFixedMessage nestedFixed() {
        return new SupportedNestedFixedMessage(
                1003L,
                new SupportedNestedFixedMessage.Header(11, 1_714_242_400_000L, SupportedSide.BUY),
                new SupportedNestedFixedMessage.Body("XNAS-01", bytes("body-token"), 1500)
        );
    }

    private static SupportedRepeatingGroupMessage repeatingGroup() {
        return new SupportedRepeatingGroupMessage(
                1004L,
                List.of(
                        new SupportedRepeatingGroupMessage.Leg(1L, 10, SupportedSide.BUY, "XNAS"),
                        new SupportedRepeatingGroupMessage.Leg(2L, 15, SupportedSide.SELL, "ARCX")
                )
        );
    }

    private static SupportedBigNumberMessage bigNumber() {
        return new SupportedBigNumberMessage(
                1005L,
                new BigInteger("987654321012345678909876543210"),
                new BigDecimal("1234567890.123456789")
        );
    }

    private static SupportedOptionalMessage optionalMessage() {
        return new SupportedOptionalMessage(
                1006L,
                Optional.of("optional-name"),
                Optional.of(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
                Optional.of(LocalDate.of(2026, 4, 28)),
                Optional.of(new BigDecimal("77.1234"))
        );
    }

    private static SupportedTemporalMessage temporalMessage() {
        return new SupportedTemporalMessage(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174001"),
                Instant.parse("2026-04-28T10:15:30.123456789Z"),
                LocalDate.of(2026, 4, 28),
                LocalTime.of(13, 45, 59, 111_222_333),
                LocalDateTime.of(2026, 4, 28, 14, 30, 1, 222_333_444),
                OffsetDateTime.of(2026, 4, 29, 9, 15, 45, 555_666_777, ZoneOffset.ofHours(3)),
                OffsetTime.of(16, 5, 4, 888_999_000, ZoneOffset.ofHoursMinutes(5, 30)),
                Duration.ofMinutes(95).plusNanos(123_456),
                Period.of(1, 2, 3),
                Year.of(2030),
                YearMonth.of(2031, 9),
                MonthDay.of(12, 31),
                ZoneOffset.ofHours(-4)
        );
    }

    private static SupportedCollectionMessage collectionMessage() {
        final SupportedCollectionMessage.Entry first = collectionEntry(11L, 100, LocalDate.of(2026, 4, 1), SupportedSide.BUY, "XNAS-ALPHA");
        final SupportedCollectionMessage.Entry second = collectionEntry(12L, 200, LocalDate.of(2026, 4, 2), SupportedSide.SELL, "ARCX-BETA");
        final SupportedCollectionMessage.Entry third = collectionEntry(13L, 300, LocalDate.of(2026, 4, 3), SupportedSide.BUY, "BATS-GAMMA");
        return new SupportedCollectionMessage(
                1007L,
                new ArrayList<>(List.of(first, second)),
                List.of(first, second, third),
                new LinkedHashSet<>(List.of(first, second)),
                new ArrayDeque<>(List.of(second, third)),
                new ArrayDeque<>(List.of(first, third)),
                new ArrayList<>(List.of(first, second, third)),
                new LinkedHashSet<>(List.of(third, first)),
                new ArrayDeque<>(List.of(third, second, first)),
                new SupportedCollectionMessage.Entry[]{first, second, third}
        );
    }

    private static SupportedCollectionMessage.Entry collectionEntry(
            final long id,
            final int quantity,
            final LocalDate tradeDate,
            final SupportedSide side,
            final String venue
    ) {
        return new SupportedCollectionMessage.Entry(id, quantity, tradeDate, side, venue);
    }

    private static SupportedScalarCollectionMessage scalarCollectionMessage() {
        final NavigableSet<YearMonth> deliveryMonths = new TreeSet<>();
        deliveryMonths.add(YearMonth.of(2026, 5));
        deliveryMonths.add(YearMonth.of(2026, 6));
        final SortedSet<LocalDate> tradeDates = new TreeSet<>();
        tradeDates.add(LocalDate.of(2026, 4, 28));
        tradeDates.add(LocalDate.of(2026, 4, 29));
        return new SupportedScalarCollectionMessage(
                1008L,
                List.of(10, 20, 30),
                new LinkedHashSet<>(List.of(
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174002"),
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174003")
                )),
                tradeDates,
                deliveryMonths,
                new ArrayDeque<>(List.of(SupportedSide.BUY, SupportedSide.SELL)),
                new ArrayDeque<>(List.of(new BigDecimal("1.25"), new BigDecimal("2.50"))),
                new ArrayList<>(List.of(new BigInteger("1000"), new BigInteger("2000"))),
                new LinkedHashSet<>(List.of("alpha", "beta")),
                new ArrayDeque<>(List.of(ZoneOffset.UTC, ZoneOffset.ofHours(3))),
                List.of(bytes("payload-a"), bytes("payload-b")),
                List.of(
                        Instant.parse("2026-04-28T10:15:30Z"),
                        Instant.parse("2026-04-28T10:16:30Z")
                ),
                new boolean[]{true, false, true},
                new char[]{'A', 'B', 'C'},
                new short[]{1, 2, 3},
                new int[]{100, 200, 300},
                new long[]{1000L, 2000L, 3000L},
                new float[]{1.5f, 2.5f, 3.5f},
                new double[]{10.125d, 20.25d, 30.5d},
                new Integer[]{7, 8, 9},
                new String[]{"foo", "bar", "baz"},
                new byte[][]{bytes("px-1"), bytes("px-2")}
        );
    }

    private static SupportedMapMessage mapMessage() {
        final LinkedHashMap<SupportedSide, String> labelsBySide = new LinkedHashMap<>();
        labelsBySide.put(SupportedSide.BUY, "bid");
        labelsBySide.put(SupportedSide.SELL, "ask");
        final SortedMap<UUID, BigDecimal> priceById = new TreeMap<>();
        priceById.put(UUID.fromString("123e4567-e89b-12d3-a456-426614174004"), new BigDecimal("10.50"));
        priceById.put(UUID.fromString("123e4567-e89b-12d3-a456-426614174005"), new BigDecimal("11.75"));
        final NavigableMap<LocalDate, YearMonth> deliveryByDate = new TreeMap<>();
        deliveryByDate.put(LocalDate.of(2026, 5, 1), YearMonth.of(2026, 5));
        deliveryByDate.put(LocalDate.of(2026, 6, 1), YearMonth.of(2026, 6));
        final TreeMap<YearMonth, ZoneOffset> offsetsByMonth = new TreeMap<>();
        offsetsByMonth.put(YearMonth.of(2026, 7), ZoneOffset.UTC);
        offsetsByMonth.put(YearMonth.of(2026, 8), ZoneOffset.ofHours(2));
        return new SupportedMapMessage(
                1009L,
                new LinkedHashMap<>(Map.of("EURUSD", 12, "USDJPY", 18)),
                labelsBySide,
                priceById,
                deliveryByDate,
                offsetsByMonth
        );
    }

    private static SupportedAdvancedMessage advancedMessage() {
        final LinkedHashMap<UUID, SupportedAdvancedMessage.FixedAccount> accountsById = new LinkedHashMap<>();
        accountsById.put(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174006"),
                new SupportedAdvancedMessage.FixedAccount(9001L, "DESK-ALPHA")
        );
        accountsById.put(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174007"),
                new SupportedAdvancedMessage.FixedAccount(9002L, "DESK-BETA")
        );
        final LinkedHashMap<LocalDate, YearMonth> bucketByDate = new LinkedHashMap<>();
        bucketByDate.put(LocalDate.of(2026, 5, 1), YearMonth.of(2026, 5));
        bucketByDate.put(LocalDate.of(2026, 6, 1), YearMonth.of(2026, 6));
        return new SupportedAdvancedMessage(
                1010L,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174008"),
                Instant.parse("2026-04-28T11:12:13.123456Z"),
                Optional.of(LocalDate.of(2026, 4, 30)),
                new SupportedAdvancedMessage.Header(7001L, 14, "XNAS-CASH", bytes("token-1")),
                List.of(
                        new SupportedAdvancedMessage.Entry(
                                1L,
                                100,
                                10.25d,
                                SupportedSide.BUY,
                                LocalDate.of(2026, 5, 15),
                                "entry-one",
                                bytes("entry-raw-1"),
                                new SupportedAdvancedMessage.PriceMeta(77L, "VENUE-01")
                        ),
                        new SupportedAdvancedMessage.Entry(
                                2L,
                                200,
                                20.50d,
                                SupportedSide.SELL,
                                LocalDate.of(2026, 6, 15),
                                "entry-two",
                                bytes("entry-raw-2"),
                                new SupportedAdvancedMessage.PriceMeta(88L, "VENUE-02")
                        )
                ),
                new LinkedHashSet<>(List.of("AAPL", "MSFT", "NVDA")),
                new ArrayDeque<>(List.of(ZoneOffset.UTC, ZoneOffset.ofHours(3))),
                List.of(bytes("attach-1"), bytes("attach-2")),
                List.of(new BigDecimal("100.125"), new BigDecimal("200.250")),
                new double[]{0.10d, 0.25d, 0.50d},
                new int[]{1, 5, 9},
                accountsById,
                bucketByDate,
                "SRC-PRIMARY",
                bytes("advanced-payload")
        );
    }

    private static SupportedFullyNestedMessage fullyNestedMessage() {
        final SupportedFullyNestedMessage.AuditEvent auditOne = new SupportedFullyNestedMessage.AuditEvent(
                1L,
                Instant.parse("2026-04-28T12:00:00Z"),
                "created"
        );
        final SupportedFullyNestedMessage.AuditEvent auditTwo = new SupportedFullyNestedMessage.AuditEvent(
                2L,
                Instant.parse("2026-04-28T12:05:00Z"),
                "validated"
        );
        final SupportedFullyNestedMessage.Charge fee = new SupportedFullyNestedMessage.Charge("FEE", new BigDecimal("1.25"));
        final SupportedFullyNestedMessage.Charge tax = new SupportedFullyNestedMessage.Charge("TAX", new BigDecimal("0.45"));
        final LinkedHashMap<SupportedSide, String> labelsBySide = new LinkedHashMap<>();
        labelsBySide.put(SupportedSide.BUY, "positive");
        labelsBySide.put(SupportedSide.SELL, "negative");
        final LinkedHashMap<YearMonth, String> notesByMonth = new LinkedHashMap<>();
        notesByMonth.put(YearMonth.of(2026, 5), "seasonal");
        notesByMonth.put(YearMonth.of(2026, 6), "promo");
        final LinkedHashMap<SupportedSide, BigDecimal> sidePrices = new LinkedHashMap<>();
        sidePrices.put(SupportedSide.BUY, new BigDecimal("10.5"));
        sidePrices.put(SupportedSide.SELL, new BigDecimal("11.0"));
        final SupportedFullyNestedMessage.PriceBucket priceBucket = new SupportedFullyNestedMessage.PriceBucket(
                "front-month",
                List.of(fee, tax),
                sidePrices
        );
        final LinkedHashMap<LocalDate, SupportedFullyNestedMessage.PriceBucket> bucketsByDate = new LinkedHashMap<>();
        bucketsByDate.put(LocalDate.of(2026, 5, 1), priceBucket);
        final SupportedFullyNestedMessage.Line line = new SupportedFullyNestedMessage.Line(
                700L,
                "AAPL",
                new BigDecimal("101.75"),
                List.of(fee, tax),
                notesByMonth
        );
        final SupportedFullyNestedMessage.Batch batch = new SupportedFullyNestedMessage.Batch(
                new SupportedFullyNestedMessage.Header(
                        501L,
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174009"),
                        Optional.of(LocalDate.of(2026, 5, 2)),
                        "XNAS"
                ),
                List.of(line),
                bucketsByDate,
                new LinkedHashSet<>(List.of("urgent", "hedge"))
        );
        final SupportedFullyNestedMessage.AccountBook accountBook = new SupportedFullyNestedMessage.AccountBook(
                "book-alpha",
                List.of(auditOne, auditTwo),
                bucketsByDate
        );
        final LinkedHashMap<UUID, SupportedFullyNestedMessage.AccountBook> booksById = new LinkedHashMap<>();
        booksById.put(UUID.fromString("123e4567-e89b-12d3-a456-426614174010"), accountBook);
        return new SupportedFullyNestedMessage(
                1011L,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174011"),
                new SupportedFullyNestedMessage.Envelope(
                        "outer-envelope",
                        labelsBySide,
                        List.of(auditOne, auditTwo),
                        new ArrayDeque<>(List.of(ZoneOffset.UTC, ZoneOffset.ofHours(3)))
                ),
                List.of(batch),
                booksById,
                Optional.of(Instant.parse("2026-05-01T00:00:00Z")),
                "OMS-ALPHA",
                bytes("fully-nested-payload")
        );
    }

    private static byte[] bytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fixedBytes(final String value, final int size) {
        final byte[] bytes = bytes(value);
        final byte[] fixed = new byte[size];
        System.arraycopy(bytes, 0, fixed, 0, Math.min(bytes.length, fixed.length));
        return fixed;
    }
}