package com.famy.shopping.api;

import com.famy.shared.security.CurrentUser;
import com.famy.shopping.api.ShoppingDtos.ClearedResponse;
import com.famy.shopping.api.ShoppingDtos.CreateListRequest;
import com.famy.shopping.api.ShoppingDtos.EditItemRequest;
import com.famy.shopping.api.ShoppingDtos.ItemRequest;
import com.famy.shopping.api.ShoppingDtos.ReorderRequest;
import com.famy.shopping.api.ShoppingDtos.UpdateListRequest;
import com.famy.shopping.application.ShoppingService;
import com.famy.shopping.application.ShoppingService.ListChanges;
import com.famy.shopping.application.ShoppingViews.ItemView;
import com.famy.shopping.application.ShoppingViews.ListView;
import com.famy.shopping.application.ShoppingViews.Suggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/families/{familyId}/shopping")
@Tag(name = "Compras")
public class ShoppingController {

    private final ShoppingService shopping;

    public ShoppingController(ShoppingService shopping) {
        this.shopping = shopping;
    }

    @GetMapping("/lists")
    @Operation(summary = "Listas de la familia con contadores de ítems")
    public List<ListView> lists(@PathVariable UUID familyId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return shopping.lists(familyId, CurrentUser.id(), includeArchived);
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear lista (Supermercado, Farmacia...)")
    public ListView createList(@PathVariable UUID familyId, @Valid @RequestBody CreateListRequest req) {
        return shopping.createList(familyId, CurrentUser.id(), req.name(), req.icon());
    }

    @PatchMapping("/lists/{listId}")
    @Operation(summary = "Renombrar, cambiar ícono o archivar")
    public ListView updateList(@PathVariable UUID familyId, @PathVariable UUID listId,
            @Valid @RequestBody UpdateListRequest req) {
        return shopping.updateList(familyId, CurrentUser.id(), listId,
                new ListChanges(req.name(), req.icon(), req.archived(), req.version()));
    }

    @DeleteMapping("/lists/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar lista (adultos)")
    public void deleteList(@PathVariable UUID familyId, @PathVariable UUID listId) {
        shopping.deleteList(familyId, CurrentUser.id(), listId);
    }

    @PostMapping("/lists/reorder")
    @Operation(summary = "Reordenar listas (todas las listas, incluidas las archivadas, en el orden nuevo)")
    public List<ListView> reorderLists(@PathVariable UUID familyId, @Valid @RequestBody ReorderRequest req) {
        return shopping.reorderLists(familyId, CurrentUser.id(), req.ids());
    }

    @GetMapping("/lists/{listId}/items")
    @Operation(summary = "Ítems: pendientes por posición y comprados al final")
    public List<ItemView> items(@PathVariable UUID familyId, @PathVariable UUID listId) {
        return shopping.items(familyId, CurrentUser.id(), listId);
    }

    @PostMapping("/lists/{listId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar ítem")
    public ItemView addItem(@PathVariable UUID familyId, @PathVariable UUID listId,
            @Valid @RequestBody ItemRequest req) {
        return shopping.addItem(familyId, CurrentUser.id(), listId, req.toData());
    }

    @PutMapping("/lists/{listId}/items/{itemId}")
    @Operation(summary = "Editar ítem (reemplazo completo) o moverlo a otra lista")
    public ItemView editItem(@PathVariable UUID familyId, @PathVariable UUID listId, @PathVariable UUID itemId,
            @Valid @RequestBody EditItemRequest req) {
        return shopping.editItem(familyId, CurrentUser.id(), listId, itemId, req.toData(), req.listId(),
                req.version());
    }

    @PostMapping("/lists/{listId}/items/{itemId}/check")
    @Operation(summary = "Marcar como comprado")
    public ItemView check(@PathVariable UUID familyId, @PathVariable UUID listId, @PathVariable UUID itemId) {
        return shopping.setChecked(familyId, CurrentUser.id(), listId, itemId, true);
    }

    @PostMapping("/lists/{listId}/items/{itemId}/uncheck")
    @Operation(summary = "Desmarcar")
    public ItemView uncheck(@PathVariable UUID familyId, @PathVariable UUID listId, @PathVariable UUID itemId) {
        return shopping.setChecked(familyId, CurrentUser.id(), listId, itemId, false);
    }

    @DeleteMapping("/lists/{listId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar ítem")
    public void deleteItem(@PathVariable UUID familyId, @PathVariable UUID listId, @PathVariable UUID itemId) {
        shopping.deleteItem(familyId, CurrentUser.id(), listId, itemId);
    }

    @PostMapping("/lists/{listId}/items/clear-checked")
    @Operation(summary = "Limpiar los comprados")
    public ClearedResponse clearChecked(@PathVariable UUID familyId, @PathVariable UUID listId) {
        return new ClearedResponse(shopping.clearChecked(familyId, CurrentUser.id(), listId));
    }

    @PostMapping("/lists/{listId}/items/reorder")
    @Operation(summary = "Reordenar ítems (todos los ítems de la lista en el orden nuevo)")
    public List<ItemView> reorderItems(@PathVariable UUID familyId, @PathVariable UUID listId,
            @Valid @RequestBody ReorderRequest req) {
        return shopping.reorderItems(familyId, CurrentUser.id(), listId, req.ids());
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Sugerencias: los ítems más frecuentes de la familia",
            description = "Con `q` filtra por prefijo; con `listId` excluye lo que ya está pendiente en esa lista.")
    public List<Suggestion> suggestions(@PathVariable UUID familyId, @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID listId, @RequestParam(defaultValue = "10") int limit) {
        return shopping.suggestions(familyId, CurrentUser.id(), q, listId, limit);
    }
}
