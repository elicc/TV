package com.fongmi.android.tv.api.parser;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Reads and formats the times of a programme without a {@link DateTimeFormatter}: parsing and
 * formatting five times per programme took seconds for the programmes of a day on a TV.
 */
final class EpgTime {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    /** Reads {@code yyyyMMddHHmmss +0800}, the form XMLTV files use. Returns {@code null} for any other form. */
    static OffsetDateTime parse(String source) {
        String s = source.trim();
        if (s.length() != 20 || s.charAt(14) != ' ' || (s.charAt(15) != '+' && s.charAt(15) != '-')) return null;
        for (int i = 0; i < 20; i++) if (i != 14 && i != 15 && (s.charAt(i) < '0' || s.charAt(i) > '9')) return null;
        try {
            LocalDateTime time = LocalDateTime.of(number(s, 0, 4), number(s, 4, 2), number(s, 6, 2), number(s, 8, 2), number(s, 10, 2), number(s, 12, 2));
            int seconds = number(s, 16, 2) * 3600 + number(s, 18, 2) * 60;
            return OffsetDateTime.of(time, ZoneOffset.ofTotalSeconds(s.charAt(15) == '-' ? -seconds : seconds));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** {@code yyyy-MM-dd} of the instant in the zone. */
    static String date(OffsetDateTime time, ZoneId zone) {
        ZonedDateTime zoned = time.atZoneSameInstant(zone);
        int year = zoned.getYear();
        if (year < 1000 || year > 9999) return DATE.format(zoned);
        return new StringBuilder(10).append(year).append('-').append(two(zoned.getMonthValue())).append('-').append(two(zoned.getDayOfMonth())).toString();
    }

    /** {@code HH:mm} of the instant in the zone. */
    static String time(OffsetDateTime time, ZoneId zone) {
        ZonedDateTime zoned = time.atZoneSameInstant(zone);
        return new StringBuilder(5).append(two(zoned.getHour())).append(':').append(two(zoned.getMinute())).toString();
    }

    private static int number(String s, int from, int length) {
        int value = 0;
        for (int i = from; i < from + length; i++) value = value * 10 + (s.charAt(i) - '0');
        return value;
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
