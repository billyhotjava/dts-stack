package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentityType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JdbcCanonicalModelIdentityReadAdapter implements CanonicalModelIdentityReadPort {

    private static final int MAX_BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final String serverTenantId;

    public JdbcCanonicalModelIdentityReadAdapter(
        JdbcTemplate jdbcTemplate,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.serverTenantId = required(serverTenantId, "server tenant id");
    }

    @Override
    public Optional<ModelIdentity> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        List<ModelIdentity> implementation = jdbcTemplate.query(
            """
            select i.id as implementation_id,
                   i.model_spec_id,
                   s.name as model_name,
                   i.model_revision,
                   i.dbt_unique_id
              from modeling_model_implementation i
              join modeling_model_spec s
                on s.tenant_id = i.tenant_id
               and s.id = i.model_spec_id
             where i.tenant_id = ?
               and i.id = ?
            """,
            (resultSet, rowNumber) -> mapDbtModel(resultSet),
            serverTenantId,
            id
        );
        if (!implementation.isEmpty()) {
            return Optional.of(implementation.getFirst());
        }
        return unique(
            jdbcTemplate.query(
                """
                select s.id as model_spec_id,
                       s.name as model_name,
                       s.revision as model_revision
                  from modeling_model_spec s
                 where s.tenant_id = ?
                   and s.id = ?
                """,
                (resultSet, rowNumber) -> mapSemanticModel(resultSet),
                serverTenantId,
                id
            )
        );
    }

    @Override
    public Optional<ModelIdentity> findDbtModel(String reference) {
        String normalized = normalized(reference);
        if (normalized == null) {
            return Optional.empty();
        }
        List<RankedIdentity> matches = jdbcTemplate.query(
            """
            select i.id as implementation_id,
                   i.model_spec_id,
                   s.name as model_name,
                   i.model_revision,
                   i.dbt_unique_id,
                   case
                     when lower(i.dbt_unique_id) = ? then 1
                     when lower(regexp_replace(i.dbt_unique_id, '^.*\\.', '')) = ? then 2
                     else 3
                   end as match_priority
              from modeling_model_implementation i
              join modeling_model_spec s
                on s.tenant_id = i.tenant_id
               and s.id = i.model_spec_id
             where i.tenant_id = ?
               and (
                    lower(i.dbt_unique_id) = ?
                    or lower(regexp_replace(i.dbt_unique_id, '^.*\\.', '')) = ?
                    or lower(s.name) = ?
               )
             order by match_priority, i.id
             fetch first 3 rows only
            """,
            (resultSet, rowNumber) -> new RankedIdentity(resultSet.getInt("match_priority"), mapDbtModel(resultSet)),
            normalized,
            normalized,
            serverTenantId,
            normalized,
            normalized,
            normalized
        );
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        int bestPriority = matches.getFirst().priority();
        List<ModelIdentity> best = matches.stream().filter(item -> item.priority() == bestPriority).map(RankedIdentity::identity).toList();
        return unique(best);
    }

    @Override
    public Optional<ModelIdentity> findSemanticModel(String reference) {
        String normalized = normalized(reference);
        if (normalized == null) {
            return Optional.empty();
        }
        UUID id = parseUuid(normalized);
        if (id != null) {
            Optional<ModelIdentity> byId = unique(
                jdbcTemplate.query(
                    """
                    select s.id as model_spec_id,
                           s.name as model_name,
                           s.revision as model_revision
                      from modeling_model_spec s
                     where s.tenant_id = ?
                       and s.id = ?
                    """,
                    (resultSet, rowNumber) -> mapSemanticModel(resultSet),
                    serverTenantId,
                    id
                )
            );
            if (byId.isPresent()) {
                return byId;
            }
        }
        List<ModelIdentity> matches = jdbcTemplate.query(
            """
            select s.id as model_spec_id,
                   s.name as model_name,
                   s.revision as model_revision
              from modeling_model_spec s
             where s.tenant_id = ?
               and lower(s.name) = ?
             order by s.id
             fetch first 2 rows only
            """,
            (resultSet, rowNumber) -> mapSemanticModel(resultSet),
            serverTenantId,
            normalized
        );
        return unique(matches);
    }

    @Override
    public Map<String, ModelIdentity> findDbtModelsByResourceNames(Set<String> resourceNames) {
        List<String> names = normalizedValues(resourceNames);
        if (names.isEmpty()) {
            return Map.of();
        }
        if (names.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("at most 500 dbt model references may be resolved at once");
        }
        List<Object> arguments = new ArrayList<>(names.size() + 2);
        arguments.addAll(names);
        arguments.add(serverTenantId);
        arguments.add(names.size() * 2 + 1);
        String sql =
            """
            with requested(resource_name) as (
                values """ +
            String.join(",", Collections.nCopies(names.size(), "(cast(? as text))")) +
            """
            ), matches as (
                select requested.resource_name,
                       i.id as implementation_id,
                       i.model_spec_id,
                       s.name as model_name,
                       i.model_revision,
                       i.dbt_unique_id,
                       count(*) over (partition by requested.resource_name) as match_count,
                       row_number() over (partition by requested.resource_name order by i.id) as match_rank
                  from requested
                  join modeling_model_implementation i
                    on i.tenant_id = ?
                   and (
                        lower(regexp_replace(i.dbt_unique_id, '^.*\\.', '')) = requested.resource_name
                        or lower(i.dbt_unique_id) = requested.resource_name
                   )
                  join modeling_model_spec s
                    on s.tenant_id = i.tenant_id
                   and s.id = i.model_spec_id
            )
            select resource_name,
                   implementation_id,
                   model_spec_id,
                   model_name,
                   model_revision,
                   dbt_unique_id
              from matches
             where match_count = 1
               and match_rank = 1
             order by resource_name
             fetch first ? rows only
            """;
        List<NamedIdentity> matches = jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) -> new NamedIdentity(resultSet.getString("resource_name"), mapDbtModel(resultSet)),
            arguments.toArray()
        );
        Map<String, ModelIdentity> result = new LinkedHashMap<>();
        matches.forEach(match -> result.put(match.resourceName(), match.identity()));
        return Collections.unmodifiableMap(result);
    }

    private ModelIdentity mapDbtModel(ResultSet resultSet) throws SQLException {
        UUID implementationId = uuid(resultSet, "implementation_id");
        return new ModelIdentity(
            ModelIdentityType.DBT_MODEL,
            implementationId,
            uuid(resultSet, "model_spec_id"),
            implementationId,
            resultSet.getString("model_name"),
            resultSet.getInt("model_revision"),
            resultSet.getString("dbt_unique_id")
        );
    }

    private ModelIdentity mapSemanticModel(ResultSet resultSet) throws SQLException {
        UUID modelSpecId = uuid(resultSet, "model_spec_id");
        return new ModelIdentity(
            ModelIdentityType.SEMANTIC_MODEL,
            modelSpecId,
            modelSpecId,
            null,
            resultSet.getString("model_name"),
            resultSet.getInt("model_revision"),
            null
        );
    }

    private static <T> Optional<T> unique(List<T> matches) {
        return matches != null && matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> normalizedValues(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(JdbcCanonicalModelIdentityReadAdapter::normalized).filter(StringUtils::hasText).distinct().toList();
    }

    private static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private record RankedIdentity(int priority, ModelIdentity identity) {}

    private record NamedIdentity(String resourceName, ModelIdentity identity) {}
}
