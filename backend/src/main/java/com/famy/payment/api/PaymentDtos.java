package com.famy.payment.api;

import com.famy.payment.application.PaymentService.PayInput;
import com.famy.payment.application.PaymentService.PaymentInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs de entrada del módulo payment (las respuestas están en {@code PaymentViews}). */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentRequest(
            @NotBlank @Size(max = 120) @Schema(example = "Luz") String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) @Schema(example = "850.00")
            BigDecimal amount,
            @Size(min = 3, max = 3) @Schema(description = "ISO 4217; por defecto la moneda de la familia",
                    example = "MXN") String currency,
            @Size(max = 255) @Schema(description = "Repetición RFC 5545 sobre fechas; vacío = pago único",
                    example = "FREQ=MONTHLY") String rrule,
            @NotNull @Schema(description = "Primer vencimiento", example = "2026-11-15") LocalDate firstDueOn,
            @Min(0) @Max(30) @Schema(description = "Días antes para avisar (además del mismo día); 3 por defecto")
            Integer remindDaysBefore,
            @Schema(description = "Adulto responsable; recibe los avisos") UUID responsibleId,
            @Size(max = 60) @Schema(description = "Categoría de presupuesto (fase 6)", example = "Servicios")
            String category,
            @Size(max = 1000) String notes,
            @Schema(description = "Solo al editar: versión que tiene el cliente; si no coincide responde 409")
            Long version) {

        PaymentInput toInput() {
            return new PaymentInput(name, amount, currency, rrule, firstDueOn, remindDaysBefore, responsibleId,
                    category, notes);
        }
    }

    public record PayRequest(
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2)
            @Schema(description = "Monto pagado; por defecto el del pago") BigDecimal amountPaid,
            @Schema(description = "Fecha en que se pagó; hoy por defecto") LocalDate paidOn,
            @Size(max = 500) String note) {

        PayInput toInput() {
            return new PayInput(amountPaid, paidOn, note);
        }
    }
}
