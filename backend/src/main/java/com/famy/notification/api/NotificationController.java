package com.famy.notification.api;

import com.famy.notification.NotificationType;
import com.famy.notification.application.NotificationService;
import com.famy.notification.application.NotificationViews.NotificationPage;
import com.famy.notification.application.NotificationViews.NotificationView;
import com.famy.notification.application.NotificationViews.Preferences;
import com.famy.notification.application.NotificationViews.PushConfig;
import com.famy.notification.application.PushService;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones")
public class NotificationController {

    private final NotificationService notifications;
    private final PushService push;

    public NotificationController(NotificationService notifications, PushService push) {
        this.notifications = notifications;
        this.push = push;
    }

    public record SubscriptionKeys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    /** Igual que {@code PushSubscription.toJSON()} del navegador. */
    public record SubscriptionRequest(@NotBlank @Size(max = 1000) String endpoint,
            @NotNull @Valid SubscriptionKeys keys) {
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = 1000) String endpoint) {
    }

    public record PreferencesRequest(@Size(max = 10) Set<NotificationType> mutedTypes, LocalTime quietFrom,
            LocalTime quietTo) {
    }

    @GetMapping
    @Operation(summary = "Mis notificaciones (más recientes primero) y cuántas sin leer")
    public NotificationPage list(@RequestParam(defaultValue = "30") int limit) {
        return notifications.list(CurrentUser.id(), limit);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marcar como leída")
    public NotificationView read(@PathVariable UUID id) {
        return notifications.markRead(CurrentUser.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar todas como leídas")
    public void readAll() {
        notifications.markAllRead(CurrentUser.id());
    }

    @GetMapping("/push")
    @Operation(summary = "Clave pública VAPID y cuántos navegadores tengo suscritos")
    public PushConfig pushConfig() {
        return push.config(CurrentUser.id());
    }

    @PostMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Registrar este navegador para recibir push (cuerpo = PushSubscription.toJSON())")
    public void subscribe(@Valid @RequestBody SubscriptionRequest req,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        push.subscribe(CurrentUser.id(), req.endpoint(), req.keys().p256dh(), req.keys().auth(), userAgent);
    }

    @DeleteMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dejar de recibir push en este navegador")
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest req) {
        push.unsubscribe(CurrentUser.id(), req.endpoint());
    }

    @PostMapping("/push/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviarme una notificación de prueba")
    public void test() {
        notifications.notify(List.of(CurrentUser.id()), null, NotificationType.SYSTEM, "Notificaciones activadas",
                "Así te avisaremos de eventos, pagos y cumpleaños.", "/configuracion");
    }

    @GetMapping("/preferences")
    @Operation(summary = "Tipos sin push y horario de silencio")
    public Preferences preferences() {
        return push.preferences(CurrentUser.id());
    }

    @PutMapping("/preferences")
    @Operation(summary = "Cambiar preferencias (los tipos silenciados siguen apareciendo en la app)")
    public Preferences updatePreferences(@Valid @RequestBody PreferencesRequest req) {
        return push.updatePreferences(CurrentUser.id(),
                new Preferences(req.mutedTypes(), req.quietFrom(), req.quietTo()));
    }
}
