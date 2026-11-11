package com.famy.payment.api;

import com.famy.payment.api.PaymentDtos.PayRequest;
import com.famy.payment.api.PaymentDtos.PaymentRequest;
import com.famy.payment.application.PaymentService;
import com.famy.payment.application.PaymentService.PayInput;
import com.famy.payment.application.PaymentViews.OccurrenceRange;
import com.famy.payment.application.PaymentViews.OccurrenceView;
import com.famy.payment.application.PaymentViews.PaymentView;
import com.famy.shared.files.FileStorage.StoredFile;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/families/{familyId}/payments")
@Tag(name = "Pagos")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    @Operation(summary = "Pagos de la familia con su próximo vencimiento (adultos)")
    public List<PaymentView> list(@PathVariable UUID familyId) {
        return payments.list(familyId, CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear pago (OWNER / ADMIN)")
    public PaymentView create(@PathVariable UUID familyId, @Valid @RequestBody PaymentRequest req) {
        return payments.create(familyId, CurrentUser.id(), req.toInput());
    }

    @GetMapping("/occurrences")
    @Operation(summary = "Vencimientos en un rango de fechas (máx. 400 días) con su estado",
            description = "PENDING, PAID u OVERDUE (sin pagar y con fecha anterior a hoy en la zona de la familia).")
    public OccurrenceRange occurrences(@PathVariable UUID familyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return payments.occurrences(familyId, CurrentUser.id(), from, to);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Detalle de un pago")
    public PaymentView get(@PathVariable UUID familyId, @PathVariable UUID paymentId) {
        return payments.get(familyId, CurrentUser.id(), paymentId);
    }

    @PutMapping("/{paymentId}")
    @Operation(summary = "Editar pago (reemplazo completo, OWNER / ADMIN)")
    public PaymentView update(@PathVariable UUID familyId, @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentRequest req) {
        return payments.update(familyId, CurrentUser.id(), paymentId, req.toInput(), req.version());
    }

    @DeleteMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar pago (OWNER / ADMIN)")
    public void delete(@PathVariable UUID familyId, @PathVariable UUID paymentId) {
        payments.delete(familyId, CurrentUser.id(), paymentId);
    }

    @PostMapping("/{paymentId}/occurrences/{dueOn}/pay")
    @Operation(summary = "Marcar un vencimiento como pagado")
    public OccurrenceView pay(@PathVariable UUID familyId, @PathVariable UUID paymentId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueOn,
            @Valid @RequestBody(required = false) PayRequest req) {
        return payments.pay(familyId, CurrentUser.id(), paymentId, dueOn,
                req == null ? new PayInput(null, null, null) : req.toInput());
    }

    @DeleteMapping("/{paymentId}/occurrences/{dueOn}/pay")
    @Operation(summary = "Deshacer «pagado» (quien lo marcó u OWNER / ADMIN); borra el comprobante")
    public OccurrenceView unpay(@PathVariable UUID familyId, @PathVariable UUID paymentId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueOn) {
        return payments.unpay(familyId, CurrentUser.id(), paymentId, dueOn);
    }

    @PostMapping(path = "/{paymentId}/occurrences/{dueOn}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Adjuntar comprobante (imagen o PDF, máx. 10 MB) a un vencimiento pagado")
    public OccurrenceView attachReceipt(@PathVariable UUID familyId, @PathVariable UUID paymentId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueOn,
            @RequestPart("file") MultipartFile file) throws IOException {
        return payments.attachReceipt(familyId, CurrentUser.id(), paymentId, dueOn, file.getOriginalFilename(),
                file.getBytes());
    }

    @GetMapping("/{paymentId}/occurrences/{dueOn}/receipt")
    @Operation(summary = "Descargar el comprobante")
    public ResponseEntity<Resource> receipt(@PathVariable UUID familyId, @PathVariable UUID paymentId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueOn) {
        StoredFile file = payments.receipt(familyId, CurrentUser.id(), paymentId, dueOn);
        String name = file.filename() == null ? "comprobante" : file.filename();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(name, java.nio.charset.StandardCharsets.UTF_8).build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(new FileSystemResource(payments.receiptPath(file)));
    }
}
