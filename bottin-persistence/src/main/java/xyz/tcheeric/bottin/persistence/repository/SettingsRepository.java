package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.SettingsEntity;

/**
 * Spring Data repository for SettingsEntity.
 *
 * <p>No query methods: the table holds one row, and looking it up by its
 * identifier is the only access pattern there is.
 */
@Repository
public interface SettingsRepository extends JpaRepository<SettingsEntity, Long> {
}
