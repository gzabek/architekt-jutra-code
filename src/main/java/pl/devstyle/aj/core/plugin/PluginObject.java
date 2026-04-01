package pl.devstyle.aj.core.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.devstyle.aj.core.BaseEntity;

import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "plugin_objects", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plugin_objects_plugin_type_id",
                columnNames = {"plugin_id", "object_type", "object_id"})
})
@SequenceGenerator(name = "base_seq", sequenceName = "plugin_object_seq", allocationSize = 1)
@Getter
@Setter
@NoArgsConstructor
public class PluginObject extends BaseEntity {

    @Column(name = "plugin_id", nullable = false, length = 255)
    private String pluginId;

    @Column(name = "object_type", nullable = false, length = 255)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 255)
    private String objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 50)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PluginObject other)) return false;
        return Objects.equals(pluginId, other.pluginId)
                && Objects.equals(objectType, other.objectType)
                && Objects.equals(objectId, other.objectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginId, objectType, objectId);
    }
}
