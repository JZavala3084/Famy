package com.famy.shared.time;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;

/** Montos para mostrar en notificaciones («$1,250.00», «MX$850.00»). */
public final class Money {

    private Money() {
    }

    public static String format(BigDecimal amount, String currency) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Spanish.LOCALE);
        try {
            f.setCurrency(Currency.getInstance(currency));
        } catch (IllegalArgumentException e) {
            return amount.toPlainString() + " " + currency;
        }
        return f.format(amount);
    }
}
