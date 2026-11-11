package com.famy.shopping.api;

import com.famy.shopping.domain.ShoppingItem.ItemData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** DTOs de entrada del módulo shopping (las respuestas están en {@code ShoppingViews}). */
public final class ShoppingDtos {

    private ShoppingDtos() {
    }

    public record CreateListRequest(@NotBlank @Size(max = 60) String name,
            @Size(max = 32) @Schema(example = "cart") String icon) {
    }

    public record UpdateListRequest(@Size(min = 1, max = 60) String name,
            @Size(max = 32) @Schema(description = "Cadena vacía para quitarlo") String icon,
            Boolean archived,
            @Schema(description = "Versión que tiene el cliente; si no coincide responde 409") Long version) {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 120) @Schema(example = "Leche") String name,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3)
            @Schema(example = "2") BigDecimal quantity,
            @Size(max = 20) @Schema(example = "L") String unit,
            @Size(max = 40) @Schema(description = "Pasillo o categoría", example = "Lácteos") String category,
            @Size(max = 500) String note) {

        ItemData toData() {
            return new ItemData(name, quantity, unit, category, note);
        }
    }

    public record EditItemRequest(
            @NotBlank @Size(max = 120) String name,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 3) BigDecimal quantity,
            @Size(max = 20) String unit,
            @Size(max = 40) String category,
            @Size(max = 500) String note,
            @Schema(description = "Para mover el ítem a otra lista") UUID listId,
            @Schema(description = "Versión que tiene el cliente; si no coincide responde 409") Long version) {

        ItemData toData() {
            return new ItemData(name, quantity, unit, category, note);
        }
    }

    public record ReorderRequest(@NotNull @Size(max = 500) List<@NotNull UUID> ids) {
    }

    public record ClearedResponse(int removed) {
    }
}
