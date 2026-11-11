package com.famy.celebration.application;

import java.time.LocalDate;
import java.time.Year;

/** Fechas que se repiten cada año. El 29 de febrero se festeja el 28 en años no bisiestos. */
final class AnnualDates {

    private AnnualDates() {
    }

    /** La fecha de ese mes y día en {@code year}. */
    static LocalDate in(int year, int month, int day) {
        if (month == 2 && day == 29 && !Year.isLeap(year)) {
            return LocalDate.of(year, 2, 28);
        }
        return LocalDate.of(year, month, day);
    }

    /** Próxima vez (hoy incluido). */
    static LocalDate next(int month, int day, LocalDate today) {
        LocalDate d = in(today.getYear(), month, day);
        return d.isBefore(today) ? in(today.getYear() + 1, month, day) : d;
    }

    static boolean valid(int month, int day) {
        return month >= 1 && month <= 12 && day >= 1 && day <= LocalDate.of(2024, month, 1).lengthOfMonth();
    }
}
