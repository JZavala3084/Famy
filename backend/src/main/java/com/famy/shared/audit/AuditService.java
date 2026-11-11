package com.famy.shared.audit;

import com.famy.shared.Ids;
import com.famy.shared.web.ClientInfo;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Registro de auditoría (tabla {@code audit_log}): login, cambios de contraseña, roles, expulsiones...
 * Nunca se guardan contraseñas ni tokens en {@code details}.
 */
@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(String action, UUID userId, UUID familyId, Map<String, ?> details) {
        ClientInfo client = ClientInfo.current();
        jdbc.sql("""
                INSERT INTO audit_log (id, user_id, family_id, action, ip, user_agent, details, created_at)
                VALUES (:id, :userId, :familyId, :action, :ip, :ua, CAST(:details AS jsonb), :createdAt)
                """)
                .param("id", Ids.newId())
                .param("userId", userId)
                .param("familyId", familyId)
                .param("action", action)
                .param("ip", client.ip())
                .param("ua", client.userAgent())
                .param("details", mapper.writeValueAsString(details == null ? Map.of() : details))
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    public void record(String action, UUID userId) {
        record(action, userId, null, null);
    }
}
