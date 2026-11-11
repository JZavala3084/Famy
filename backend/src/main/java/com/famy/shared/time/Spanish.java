package com.famy.shared.time;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Textos de fechas en español para notificaciones («hoy a las 16:00», «mañana», «viernes 20 de noviembre»). */
public final class Spanish {

    public static final Locale LOCALE = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", LOCALE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", LOCALE);

    private Spanish() {
    }

    /** «hoy», «mañana» o «viernes 20 de noviembre» respecto a {@code today}. */
    public static String day(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "hoy";
        }
        if (date.equals(today.plusDays(1))) {
            return "mañana";
        }
        return DAY.format(date);
    }

    /** «hoy a las 16:00». */
    public static String dayAndTime(ZonedDateTime when, LocalDate today) {
        return day(when.toLocalDate(), today) + " a las " + TIME.format(when);
    }

    public static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
