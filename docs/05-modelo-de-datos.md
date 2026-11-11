# 05 · Modelo de datos

PostgreSQL 16 con migraciones Flyway. Ids `UUID` (v7). Todas las tablas de dominio tienen `family_id`, `created_by`, `created_at`, `updated_at`, `version` y, donde aplica, `deleted_at`.

## Diagrama ER (núcleo)

```mermaid
erDiagram
    APP_USER ||--o{ USER_IDENTITY : tiene
    APP_USER ||--o{ REFRESH_TOKEN : tiene
    APP_USER ||--o{ FAMILY_MEMBER : pertenece
    FAMILY ||--o{ FAMILY_MEMBER : agrupa
    FAMILY ||--o{ FAMILY_INVITATION : emite
    FAMILY ||--o{ SHOPPING_LIST : tiene
    SHOPPING_LIST ||--o{ SHOPPING_ITEM : contiene
    FAMILY ||--o{ BUDGET : tiene
    BUDGET ||--o{ BUDGET_CATEGORY : divide
    BUDGET_CATEGORY ||--o{ TRANSACTION : clasifica
    FAMILY ||--o{ RECIPE : tiene
    RECIPE ||--o{ RECIPE_INGREDIENT : usa
    FAMILY ||--o{ EVENT : agenda
    EVENT ||--o{ EVENT_PARTICIPANT : incluye
    FAMILY ||--o{ CHORE : asigna
    CHORE ||--o{ CHORE_COMPLETION : registra
    FAMILY ||--o{ PAYMENT : programa
    PAYMENT ||--o{ PAYMENT_OCCURRENCE : genera
    FAMILY ||--o{ CELEBRATION : recuerda
    FAMILY ||--o{ CHANGE_LOG : emite
```

## Tablas

### Identidad
| Tabla | Columnas clave |
|-------|----------------|
| `app_user` | id, email (único, guardado en minúsculas), email_verified_at, password_hash (nulo si solo Google), display_name, avatar_url, color, birthday, timezone, locale, currency (ISO 4217, default `USD`), status, created_at |
| `user_identity` | id, user_id, provider (`GOOGLE`), provider_subject (único por proveedor), email, linked_at |
| `refresh_token` | id, user_id, token_hash, device_name, family_chain_id, expires_at, revoked_at, replaced_by, created_ip |
| `email_token` | id, user_id, purpose (`VERIFY`, `RESET`), token_hash, expires_at, used_at |
| `device` | id, user_id, platform (`ANDROID`, `IOS`, `WEB`), push_token, last_seen_at |

### Familia
| Tabla | Columnas clave |
|-------|----------------|
| `family` | id, name, avatar_url, currency (ISO 4217, default `USD`), timezone, owner_id, members_can_invite, created_at, version |
| `family_member` | family_id, user_id, role (`OWNER`, `ADMIN`, `MEMBER`, `CHILD`), nickname, joined_at, left_at · PK (family_id, user_id) |
| `family_invitation` | id, family_id, code (8 car., único), token_hash (para enlace), role, email (opcional), max_uses, uses, requires_approval, expires_at, revoked_at, created_by |
| `join_request` | id, family_id, user_id, invitation_id, role, status (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`), decided_by, decided_at |

### Compras
| Tabla | Columnas clave |
|-------|----------------|
| `shopping_list` | id, family_id, name, icon, position, archived_at, deleted_at |
| `shopping_item` | id, list_id, family_id, name, quantity, unit, category, note, checked_at, checked_by, position, deleted_at (los borrados se conservan para las sugerencias) |

### Presupuesto
| Tabla | Columnas clave |
|-------|----------------|
| `budget` | id, family_id, period (`YYYY-MM`), currency · único (family_id, period) |
| `budget_category` | id, budget_id, family_id, name, icon, color, limit_amount |
| `transaction` | id, family_id, category_id, type (`EXPENSE`, `INCOME`), amount NUMERIC(14,2), currency, occurred_on, member_id, note, source (`MANUAL`, `PAYMENT`, `SHOPPING`), source_id |

### Recetas
| Tabla | Columnas clave |
|-------|----------------|
| `recipe` | id, family_id, title, description, photo_url, servings, prep_minutes, cook_minutes, steps (JSONB), tags (text[]) |
| `recipe_ingredient` | id, recipe_id, name, quantity, unit, position |
| `meal_plan_entry` | id, family_id, date, meal (`BREAKFAST`, `LUNCH`, `DINNER`), recipe_id |

### Calendario y horarios
| Tabla | Columnas clave |
|-------|----------------|
| `calendar_event` | id, family_id, title, description, location, starts_at, ends_at, all_day, rrule, color, participant_ids (uuid[]; vacío = toda la familia), reminder_minutes (int[]), excluded_starts (timestamptz[], días cancelados), created_by, deleted_at |

Editar un solo día de una serie (en lugar de cancelarlo) queda para después.

### Tareas del hogar
| Tabla | Columnas clave |
|-------|----------------|
| `chore` | id, family_id, title, description, due_at, rrule, recurrence_start (DTSTART), points, rotation (boolean), rotation_order (uuid[]), current_assignee_id, completed_at (tareas sin repetición), deleted_at |
| `chore_completion` | id, chore_id, family_id, completed_by, recorded_by, completed_at, occurrence_date, points (al momento de hacerla), previous_due_at, previous_assignee_id (para deshacer) |

### Pagos
| Tabla | Columnas clave |
|-------|----------------|
| `payment` | id, family_id, name, amount, currency, rrule, first_due_on, remind_days_before, responsible_id, budget_category_name |
| `payment_occurrence` | id, payment_id, family_id, due_on, paid_on, paid_by, amount_paid, note, receipt_file_id. Solo se guardan los vencimientos pagados; `PENDING` / `OVERDUE` se calculan al expandir la RRULE. El movimiento en el presupuesto llega en la fase 6 |

### Cumpleaños y fechas especiales
| Tabla | Columnas clave |
|-------|----------------|
| `celebration` | id, family_id, type (`BIRTHDAY`, `ANNIVERSARY`, `OTHER`), person_name, user_id (si es miembro: la fecha sale de su perfil), month, day, year (opcional), notes, gift_ideas (JSONB) |

### Salud
| Tabla | Columnas clave |
|-------|----------------|
| `health_condition` | id, family_id, user_id, type (`CONDITION`, `ALLERGY`), name_enc, notes_enc, visibility (`SELF`, `ADULTS`, `FAMILY`) |
| `medication` | id, family_id, user_id, name_enc, dose, unit, quantity_per_intake, instructions_enc, schedule_type (`FIXED_TIMES`, `INTERVAL`, `WEEKDAYS`, `AS_NEEDED`), times (time[]), interval_hours, weekdays (smallint[]), starts_on, ends_on, stock, low_stock_threshold, remind_repeat_min, escalate_after_min, escalate_to_user_id, visibility, active |
| `medication_intake` | id, medication_id, family_id, scheduled_at, status (`PENDING`, `TAKEN`, `SNOOZED`, `SKIPPED`, `MISSED`), taken_at, confirmed_by |

Los campos `*_enc` se cifran con AES-GCM en la aplicación (clave fuera de la base). Las tomas futuras se generan a partir de `medication` y se avisan con el mismo job de recordatorios.

### Transversales
| Tabla | Columnas clave |
|-------|----------------|
| `change_log` | id (bigserial = cursor), family_id, audience (`FAMILY`, `ADULTS`), entity, entity_id, op (`C`,`U`,`D`), version, actor_id, occurred_at, payload (JSONB) |
| `change_log_watermark` | id (= 1), purged_up_to (cursor hasta el que se purgó; un cliente con un cursor menor recarga todo) |
| `notification` | id, user_id, family_id, type, title, body, link, read_at, created_at |
| `notification_preference` | user_id, muted_types (text[]), quiet_from, quiet_to |
| `push_subscription` | id, user_id, endpoint, p256dh, auth, user_agent, last_success_at |
| `reminder_log` | reminder_key (p. ej. `event:<id>:<inicio>:<min>`), fire_at, sent_at: evita enviar dos veces |
| `reminder_checkpoint` | id (= 1), checked_up_to: hasta dónde revisó el job |
| `file_object` | id, family_id, storage_key, content_type, size_bytes, sha256, uploaded_by |
| `audit_log` | id, user_id, family_id, action, ip, user_agent, details (JSONB), created_at |
| `webhook_endpoint` (post-MVP) | id, family_id, url, secret_hash, events (text[]), active |
| `webhook_delivery` (post-MVP) | id, endpoint_id, event_id, status, attempts, last_response_code, next_retry_at |

## Índices y reglas
- Índice `(family_id, updated_at)` en todas las tablas de dominio para la sincronización.
- `change_log (family_id, id)` para recuperar cambios desde un cursor; se purga a los 30 días. Antes de insertar se toma un advisory lock por familia para que los cursores de una familia queden en orden de commit.
- Recurrencias guardadas como RRULE (RFC 5545) y expandidas en el backend con `ical4j` o `lib-recur` para el rango pedido.
- Los recordatorios no se guardan por adelantado: un job cada minuto pregunta a cada módulo qué debe sonar entre el último punto revisado y ahora, y registra cada envío en `reminder_log` con una llave estable. Editar o borrar no requiere reprogramar nada.
