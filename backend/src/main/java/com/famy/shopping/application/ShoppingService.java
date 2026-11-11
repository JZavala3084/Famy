package com.famy.shopping.application;

import com.famy.family.FamilyMembership;
import com.famy.family.FamilyRole;
import com.famy.shared.error.ApiException;
import com.famy.shared.events.ChangePublisher;
import com.famy.shopping.application.ShoppingViews.ItemView;
import com.famy.shopping.application.ShoppingViews.ListView;
import com.famy.shopping.application.ShoppingViews.Suggestion;
import com.famy.shopping.domain.ShoppingItem;
import com.famy.shopping.domain.ShoppingItem.ItemData;
import com.famy.shopping.domain.ShoppingList;
import com.famy.shopping.infrastructure.ShoppingItemRepository;
import com.famy.shopping.infrastructure.ShoppingItemRepository.ListCount;
import com.famy.shopping.infrastructure.ShoppingListRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de compras. Cualquier miembro (incluido CHILD) puede agregar, editar y marcar ítems y crear listas;
 * borrar una lista entera requiere un adulto (ver docs/06-familias.md#roles-y-permisos).
 */
@Service
public class ShoppingService {

    static final String LIST = "shopping.list";
    static final String ITEM = "shopping.item";
    static final int MAX_LISTS = 50;
    static final int MAX_ITEMS_PER_LIST = 500;

    private final ShoppingListRepository lists;
    private final ShoppingItemRepository items;
    private final FamilyMembership membership;
    private final ChangePublisher changes;
    private final JdbcClient jdbc;

    public ShoppingService(ShoppingListRepository lists, ShoppingItemRepository items, FamilyMembership membership,
            ChangePublisher changes, JdbcClient jdbc) {
        this.lists = lists;
        this.items = items;
        this.membership = membership;
        this.changes = changes;
        this.jdbc = jdbc;
    }

    public record ListChanges(String name, String icon, Boolean archived, Long version) {
    }

    // ---------------------------------------------------------------- listas

    @Transactional(readOnly = true)
    public List<ListView> lists(UUID familyId, UUID userId, boolean includeArchived) {
        membership.requireMember(familyId, userId);
        Map<UUID, ListCount> counts = items.countByList(familyId).stream()
                .collect(Collectors.toMap(ListCount::getListId, Function.identity()));
        return lists.findByFamily(familyId, includeArchived).stream().map(l -> {
            ListCount c = counts.get(l.getId());
            return ListView.of(l, c == null ? 0 : c.getTotal(), c == null ? 0 : c.getPending());
        }).toList();
    }

    @Transactional
    public ListView createList(UUID familyId, UUID userId, String name, String icon) {
        membership.requireMember(familyId, userId);
        if (lists.findByFamily(familyId, true).size() >= MAX_LISTS) {
            throw ApiException.conflict("too_many_lists", "La familia ya tiene el máximo de listas.");
        }
        ShoppingList list = lists.saveAndFlush(
                new ShoppingList(familyId, name, icon, lists.maxPosition(familyId) + 1, userId));
        ListView view = ListView.of(list, 0, 0);
        changes.created(familyId, LIST, list.getId(), list.getVersion(), view);
        return view;
    }

    @Transactional
    public ListView updateList(UUID familyId, UUID userId, UUID listId, ListChanges cmd) {
        membership.requireMember(familyId, userId);
        ShoppingList list = requireList(familyId, listId);
        checkVersion(cmd.version(), list.getVersion());
        if (cmd.name() != null) {
            list.rename(cmd.name());
        }
        if (cmd.icon() != null) {
            list.changeIcon(cmd.icon());
        }
        if (cmd.archived() != null) {
            list.archive(cmd.archived());
        }
        return publishList(lists.saveAndFlush(list));
    }

    @Transactional
    public void deleteList(UUID familyId, UUID userId, UUID listId) {
        FamilyRole role = membership.requireMember(familyId, userId);
        if (role == FamilyRole.CHILD) {
            throw ApiException.forbidden("forbidden", "Pide a un adulto que borre la lista.");
        }
        ShoppingList list = requireList(familyId, listId);
        list.delete();
        lists.saveAndFlush(list);
        changes.deleted(familyId, LIST, listId, list.getVersion());
    }

    @Transactional
    public List<ListView> reorderLists(UUID familyId, UUID userId, List<UUID> ids) {
        membership.requireMember(familyId, userId);
        List<ShoppingList> current = lists.findByFamily(familyId, true);
        Map<UUID, ShoppingList> byId = current.stream().collect(Collectors.toMap(ShoppingList::getId, l -> l));
        requireSameIds(ids, byId.keySet());
        for (int i = 0; i < ids.size(); i++) {
            ShoppingList l = byId.get(ids.get(i));
            if (l.getPosition() != i) {
                l.moveTo(i);
                publishList(lists.saveAndFlush(l));
            }
        }
        return lists(familyId, userId, true);
    }

    // ---------------------------------------------------------------- ítems

    @Transactional(readOnly = true)
    public List<ItemView> items(UUID familyId, UUID userId, UUID listId) {
        membership.requireMember(familyId, userId);
        requireList(familyId, listId);
        return items.findByList(listId).stream().map(ItemView::of).toList();
    }

    @Transactional
    public ItemView addItem(UUID familyId, UUID userId, UUID listId, ItemData data) {
        membership.requireMember(familyId, userId);
        ShoppingList list = requireList(familyId, listId);
        if (items.findByList(listId).size() >= MAX_ITEMS_PER_LIST) {
            throw ApiException.conflict("too_many_items", "La lista ya tiene el máximo de ítems.");
        }
        ShoppingItem item = items.saveAndFlush(new ShoppingItem(familyId, list.getId(), data, items.maxPosition(listId) + 1, userId));
        ItemView view = ItemView.of(item);
        changes.created(familyId, ITEM, item.getId(), item.getVersion(), view);
        listCountsChanged(list);
        return view;
    }

    /** Reemplaza los datos del ítem; si {@code targetListId} es otra lista, lo mueve al final de esa lista. */
    @Transactional
    public ItemView editItem(UUID familyId, UUID userId, UUID listId, UUID itemId, ItemData data, UUID targetListId,
            Long version) {
        membership.requireMember(familyId, userId);
        ShoppingItem item = requireItem(familyId, listId, itemId);
        checkVersion(version, item.getVersion());
        item.edit(data);
        ShoppingList moveTo = null;
        if (targetListId != null && !targetListId.equals(listId)) {
            moveTo = requireList(familyId, targetListId);
            item.moveToList(targetListId, items.maxPosition(targetListId) + 1);
        }
        ItemView view = publishItem(items.saveAndFlush(item));
        if (moveTo != null) {
            listCountsChanged(requireList(familyId, listId));
            listCountsChanged(moveTo);
        }
        return view;
    }

    @Transactional
    public ItemView setChecked(UUID familyId, UUID userId, UUID listId, UUID itemId, boolean checked) {
        membership.requireMember(familyId, userId);
        ShoppingItem item = requireItem(familyId, listId, itemId);
        if (item.isChecked() == checked) {
            return ItemView.of(item);
        }
        if (checked) {
            item.check(userId);
        } else {
            item.uncheck();
        }
        ItemView view = publishItem(items.saveAndFlush(item));
        listCountsChanged(requireList(familyId, listId));
        return view;
    }

    @Transactional
    public void deleteItem(UUID familyId, UUID userId, UUID listId, UUID itemId) {
        membership.requireMember(familyId, userId);
        ShoppingItem item = requireItem(familyId, listId, itemId);
        item.delete();
        items.saveAndFlush(item);
        changes.deleted(familyId, ITEM, itemId, item.getVersion());
        listCountsChanged(requireList(familyId, listId));
    }

    /** RF-3.3: limpiar los ítems comprados. Devuelve cuántos se quitaron. */
    @Transactional
    public int clearChecked(UUID familyId, UUID userId, UUID listId) {
        membership.requireMember(familyId, userId);
        ShoppingList list = requireList(familyId, listId);
        List<ShoppingItem> checked = items.findChecked(listId);
        for (ShoppingItem item : checked) {
            item.delete();
            items.saveAndFlush(item);
            changes.deleted(familyId, ITEM, item.getId(), item.getVersion());
        }
        if (!checked.isEmpty()) {
            listCountsChanged(list);
        }
        return checked.size();
    }

    @Transactional
    public List<ItemView> reorderItems(UUID familyId, UUID userId, UUID listId, List<UUID> ids) {
        membership.requireMember(familyId, userId);
        requireList(familyId, listId);
        Map<UUID, ShoppingItem> byId = items.findByList(listId).stream()
                .collect(Collectors.toMap(ShoppingItem::getId, i -> i));
        requireSameIds(ids, byId.keySet());
        for (int i = 0; i < ids.size(); i++) {
            ShoppingItem item = byId.get(ids.get(i));
            if (item.getPosition() != i) {
                item.moveTo(i);
                publishItem(items.saveAndFlush(item));
            }
        }
        return items.findByList(listId).stream().map(ItemView::of).toList();
    }

    /**
     * RF-3.4: ítems más frecuentes de la familia (incluye el historial de ítems ya comprados y limpiados). Con
     * {@code listId} se excluyen los que ya están pendientes en esa lista.
     */
    @Transactional(readOnly = true)
    public List<Suggestion> suggestions(UUID familyId, UUID userId, String query, UUID listId, int limit) {
        membership.requireMember(familyId, userId);
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String pattern = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("""
                SELECT s.name, s.unit, s.category, r.uses
                FROM (
                    SELECT lower(name) AS key, count(*) AS uses, max(created_at) AS last_used
                    FROM shopping_item
                    WHERE family_id = :familyId AND (:q = '' OR lower(name) LIKE :pattern)
                    GROUP BY lower(name)
                ) r
                JOIN LATERAL (
                    SELECT i.name, i.unit, i.category FROM shopping_item i
                    WHERE i.family_id = :familyId AND lower(i.name) = r.key
                    ORDER BY i.created_at DESC LIMIT 1
                ) s ON true
                WHERE CAST(:listId AS uuid) IS NULL OR NOT EXISTS (
                    SELECT 1 FROM shopping_item p
                    WHERE p.list_id = CAST(:listId AS uuid) AND p.deleted_at IS NULL AND p.checked_at IS NULL
                      AND lower(p.name) = r.key)
                ORDER BY r.uses DESC, r.last_used DESC
                LIMIT :limit
                """)
                .param("familyId", familyId)
                .param("q", q)
                .param("pattern", pattern)
                .param("listId", listId)
                .param("limit", Math.clamp(limit, 1, 50))
                .query((rs, n) -> new Suggestion(rs.getString("name"), rs.getString("unit"),
                        rs.getString("category"), rs.getLong("uses")))
                .list();
    }

    // ---------------------------------------------------------------- auxiliares

    private ShoppingList requireList(UUID familyId, UUID listId) {
        return lists.findActive(familyId, listId)
                .orElseThrow(() -> ApiException.notFound("list_not_found", "Lista no encontrada."));
    }

    private ShoppingItem requireItem(UUID familyId, UUID listId, UUID itemId) {
        return items.findActive(familyId, listId, itemId)
                .orElseThrow(() -> ApiException.notFound("item_not_found", "Ítem no encontrado."));
    }

    static void checkVersion(Long expected, long actual) {
        if (expected != null && expected != actual) {
            throw ApiException.conflict("version_conflict",
                    "Otro miembro modificó este dato al mismo tiempo. Vuelve a intentarlo.");
        }
    }

    private static void requireSameIds(List<UUID> ids, java.util.Set<UUID> existing) {
        if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
            throw ApiException.badRequest("invalid_order", "El orden debe incluir exactamente todos los elementos.");
        }
    }

    private ListView publishList(ShoppingList list) {
        ListView view = viewWithCounts(list);
        changes.updated(list.getFamilyId(), LIST, list.getId(), list.getVersion(), view);
        return view;
    }

    private ItemView publishItem(ShoppingItem item) {
        ItemView view = ItemView.of(item);
        changes.updated(item.getFamilyId(), ITEM, item.getId(), item.getVersion(), view);
        return view;
    }

    /** Los contadores de la lista cambiaron: se emite la lista sin cambiar su versión. */
    private void listCountsChanged(ShoppingList list) {
        ListView view = viewWithCounts(list);
        changes.updated(list.getFamilyId(), LIST, list.getId(), list.getVersion(), view);
    }

    private ListView viewWithCounts(ShoppingList list) {
        List<ShoppingItem> current = items.findByList(list.getId());
        long pending = current.stream().filter(i -> !i.isChecked()).count();
        return ListView.of(list, current.size(), pending);
    }
}
