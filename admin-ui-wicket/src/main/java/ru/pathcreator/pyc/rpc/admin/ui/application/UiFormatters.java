package ru.pathcreator.pyc.rpc.admin.ui.application;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class UiFormatters {

    private static final DecimalFormat DECIMAL =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU")));
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                    .withLocale(Locale.forLanguageTag("ru-RU"))
                    .withZone(ZoneId.systemDefault());

    private UiFormatters() {
    }

    public static String bytes(final long value) {
        if (value < 1024L) {
            return value + " Б";
        }
        final String[] units = {"КБ", "МБ", "ГБ", "ТБ"};
        double scaled = value;
        int index = -1;
        while (scaled >= 1024D && index < units.length - 1) {
            scaled /= 1024D;
            index++;
        }
        return DECIMAL.format(scaled) + " " + units[index];
    }

    public static String percent(final double value) {
        if (value < 0D) {
            return "Н/Д";
        }
        return DECIMAL.format(value) + "%";
    }

    public static String latency(final long nanos) {
        if (nanos <= 0L) {
            return "—";
        }
        if (nanos < 1_000L) {
            return nanos + " нс";
        }
        if (nanos < 1_000_000L) {
            return DECIMAL.format(nanos / 1_000D) + " мкс";
        }
        if (nanos < 1_000_000_000L) {
            return DECIMAL.format(nanos / 1_000_000D) + " мс";
        }
        return DECIMAL.format(nanos / 1_000_000_000D) + " с";
    }

    public static String instant(final long epochMs) {
        if (epochMs <= 0L) {
            return "Н/Д";
        }
        return DATE_TIME.format(Instant.ofEpochMilli(epochMs));
    }

    public static String duration(final Duration duration) {
        final long seconds = duration.getSeconds();
        final long hours = seconds / 3600L;
        final long minutes = (seconds % 3600L) / 60L;
        final long tailSeconds = seconds % 60L;
        if (hours > 0L) {
            return hours + " ч " + minutes + " мин";
        }
        if (minutes > 0L) {
            return minutes + " мин " + tailSeconds + " сек";
        }
        return tailSeconds + " сек";
    }

    public static String integer(final long value) {
        return DECIMAL.format(value);
    }

    public static String yesNo(final boolean value) {
        return value ? "Да" : "Нет";
    }
}