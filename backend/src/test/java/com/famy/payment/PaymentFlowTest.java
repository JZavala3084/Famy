package com.famy.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.famy.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class PaymentFlowTest extends IntegrationTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    private String base(String familyId) {
        return "/api/v1/families/" + familyId + "/payments";
    }

    private MvcResult upload(String url, String name, byte[] content, String token) throws Exception {
        return mvc.perform(multipart(url).file(new MockMultipartFile("file", name, "application/pdf", content))
                .header("Authorization", "Bearer " + token).with(randomIp())).andReturn();
    }

    @Test
    void monthlyPaymentHasStatusesCanBePaidAndChildrenCannotSeeIt() throws Exception {
        String owner = verifiedUser("Jose");
        String familyId = createFamilyAs(owner, "Familia Pagos");
        String ana = joinAs(owner, familyId, "Ana", "MEMBER");
        String sofi = joinAs(owner, familyId, "Sofi", "CHILD");
        String anaId = meId(ana);
        long sofiCursor = ((Number) json(getAuth("/api/v1/families/" + familyId + "/changes/cursor", sofi),
                "$.cursor")).longValue();

        LocalDate today = LocalDate.now(MX);
        LocalDate first = today.minusMonths(1);
        MvcResult created = postJson(base(familyId), """
                {"name":"Luz","amount":850,"rrule":"FREQ=MONTHLY","firstDueOn":"%s","remindDaysBefore":3,
                 "responsibleId":"%s","category":"Servicios"}""".formatted(first, anaId), owner);
        assertThat(status(created)).as(body(created)).isEqualTo(201);
        String paymentId = json(created, "$.id");
        assertThat((String) json(created, "$.currency")).isEqualTo("USD");
        assertThat((Double) json(created, "$.amount")).isEqualTo(850.0);
        assertThat((String) json(created, "$.nextDueOn")).isEqualTo(first.toString());
        assertThat((Integer) json(created, "$.overdueCount")).isEqualTo(1);

        // Un MEMBER no crea pagos; un CHILD no los ve.
        assertThat(status(postJson(base(familyId), """
                {"name":"Agua","amount":100,"firstDueOn":"%s"}""".formatted(today), ana))).isEqualTo(403);
        assertThat(status(getAuth(base(familyId), sofi))).isEqualTo(403);

        String range = "/occurrences?from=%s&to=%s".formatted(first.minusDays(1), today.plusMonths(1));
        MvcResult occ = getAuth(base(familyId) + range, ana);
        assertThat(status(occ)).as(body(occ)).isEqualTo(200);
        assertThat((List<String>) json(occ, "$.occurrences[*].status")).containsExactly("OVERDUE", "PENDING",
                "PENDING");

        // Ana marca pagado el vencido, con otro monto.
        String pay = base(familyId) + "/" + paymentId + "/occurrences/" + first + "/pay";
        MvcResult paid = postJson(pay, "{\"amountPaid\":861.5,\"note\":\"con recargo\"}", ana);
        assertThat(status(paid)).as(body(paid)).isEqualTo(200);
        assertThat((String) json(paid, "$.status")).isEqualTo("PAID");
        assertThat((Double) json(paid, "$.amountPaid")).isEqualTo(861.5);
        assertThat(status(postJson(pay, "", ana))).isEqualTo(409);
        assertThat(status(postJson(base(familyId) + "/" + paymentId + "/occurrences/" + first.plusDays(1) + "/pay",
                "", ana))).isEqualTo(404);
        MvcResult detail = getAuth(base(familyId) + "/" + paymentId, owner);
        assertThat((String) json(detail, "$.nextDueOn")).isEqualTo(today.toString());
        assertThat((Integer) json(detail, "$.overdueCount")).isEqualTo(0);

        // Comprobante: se acepta un PDF, no un HTML.
        String receipt = base(familyId) + "/" + paymentId + "/occurrences/" + first + "/receipt";
        MvcResult upload = upload(receipt, "recibo.pdf", "%PDF-1.4 recibo".getBytes(), ana);
        assertThat(status(upload)).as(body(upload)).isEqualTo(200);
        assertThat((String) json(upload, "$.receiptFileId")).isNotNull();
        assertThat(status(upload(receipt, "x.pdf", "<html>".getBytes(), ana))).isEqualTo(400);
        MvcResult download = getAuth(receipt, owner);
        assertThat(status(download)).isEqualTo(200);
        assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(download.getResponse().getContentAsString()).isEqualTo("%PDF-1.4 recibo");
        assertThat(status(getAuth(receipt, sofi))).isEqualTo(403);

        // Deshacer: vuelve a vencido y el comprobante desaparece.
        MvcResult undone = deleteAs(pay, ana);
        assertThat(status(undone)).as(body(undone)).isEqualTo(200);
        assertThat((String) json(undone, "$.status")).isEqualTo("OVERDUE");
        assertThat(status(getAuth(receipt, owner))).isEqualTo(404);

        // Los cambios de pagos no llegan a un CHILD.
        MvcResult childChanges = getAuth("/api/v1/families/" + familyId + "/changes?since=" + sofiCursor, sofi);
        assertThat((List<String>) json(childChanges, "$.changes[*].entity")).doesNotContain("payment",
                "payment.occurrence");
        MvcResult adultChanges = getAuth("/api/v1/families/" + familyId + "/changes?since=" + sofiCursor, ana);
        assertThat((List<String>) json(adultChanges, "$.changes[*].entity")).contains("payment",
                "payment.occurrence");

        assertThat(status(deleteAs(base(familyId) + "/" + paymentId, ana))).isEqualTo(403);
        assertThat(status(deleteAs(base(familyId) + "/" + paymentId, owner))).isEqualTo(204);
        assertThat((List<?>) json(getAuth(base(familyId), owner), "$")).isEmpty();
    }

    @Test
    void oneTimePaymentAndValidation() throws Exception {
        String owner = verifiedUser("Jose");
        String familyId = createFamilyAs(owner, "Familia Pago Único");
        LocalDate today = LocalDate.now(MX);
        MvcResult created = postJson(base(familyId), """
                {"name":"Inscripción","amount":1200.456,"currency":"mxn","firstDueOn":"%s"}"""
                .formatted(today.plusDays(10)), owner);
        assertThat(status(created)).as(body(created)).isEqualTo(400);
        created = postJson(base(familyId), """
                {"name":"Inscripción","amount":1200.45,"currency":"mxn","firstDueOn":"%s"}"""
                .formatted(today.plusDays(10)), owner);
        assertThat(status(created)).as(body(created)).isEqualTo(201);
        assertThat((String) json(created, "$.currency")).isEqualTo("MXN");
        MvcResult occ = getAuth(base(familyId) + "/occurrences?from=%s&to=%s".formatted(today,
                today.plusDays(30)), owner);
        assertThat((List<?>) json(occ, "$.occurrences")).hasSize(1);
        assertThat(status(postJson(base(familyId), """
                {"name":"X","amount":1,"currency":"ZZZ","firstDueOn":"%s"}""".formatted(today), owner)))
                .isEqualTo(400);
        assertThat(status(postJson(base(familyId), """
                {"name":"X","amount":1,"rrule":"FREQ=HOURLY","firstDueOn":"%s"}""".formatted(today), owner)))
                .isEqualTo(400);
    }
}
