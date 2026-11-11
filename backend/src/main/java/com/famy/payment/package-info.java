/**
 * Módulo payment: calendario de pagos (RF-8.1 a RF-8.4). Pagos únicos o recurrentes (RRULE sobre fechas), estados
 * pendiente / pagado / vencido, comprobantes y recordatorios N días antes y el día del vencimiento. Solo los adultos
 * los ven (decisión P-4): los cambios en vivo {@code payment.*} van al destino de adultos.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payment")
package com.famy.payment;
