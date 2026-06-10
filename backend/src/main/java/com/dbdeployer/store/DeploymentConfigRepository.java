package com.dbdeployer.store;

import com.dbdeployer.model.DeploymentConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentConfigRepository extends JpaRepository<DeploymentConfig, String> {
    boolean existsByName(String name);

    boolean existsByHostPort(int hostPort);

    //    /**
    //     * Returns true if any non-removed instance is already using this port. REMOVED
    //     * configs do not block port reuse.
    //     */
    //    @Query("SELECT CASE WHEN COUNT(dc) > 0 THEN true ELSE false END FROM DeploymentConfig dc "
    //            + "WHERE dc.hostPort = :hostPort AND "
    //            + "(dc.container IS NULL OR dc.container.status <> com.dbdeployer.model.InstanceStatus.REMOVED)")
    //    boolean existsByHostPortAndNotRemoved(@Param("hostPort") int hostPort);

    // ── Instance queries (is_template = false) ────────────────────────────────

    List<DeploymentConfig> findAllByIsTemplateFalse();

    Optional<DeploymentConfig> findByIdAndIsTemplateFalse(String id);

    // ── Template queries (is_template = true) ─────────────────────────────────

    List<DeploymentConfig> findAllByIsTemplateTrueOrderByCreatedAtDesc();

    Optional<DeploymentConfig> findByIdAndIsTemplateTrue(String id);

    boolean existsByNameAndIsTemplateTrue(String name);

    boolean existsByNameAndIsTemplateTrueAndIdNot(String name, String id);
}
