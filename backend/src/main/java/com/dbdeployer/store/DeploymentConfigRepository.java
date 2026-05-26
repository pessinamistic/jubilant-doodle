package com.dbdeployer.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dbdeployer.model.DeploymentConfig;

@Repository
public interface DeploymentConfigRepository extends JpaRepository<DeploymentConfig, String> {
    boolean existsByName(String name);
    boolean existsByHostPort(int hostPort);

    /**
     * Returns true if any non-removed instance is already using this port.
     * REMOVED configs do not block port reuse.
     */
    @Query("SELECT CASE WHEN COUNT(dc) > 0 THEN true ELSE false END FROM DeploymentConfig dc " +
           "WHERE dc.hostPort = :hostPort AND " +
           "(dc.container IS NULL OR dc.container.status <> com.dbdeployer.model.InstanceStatus.REMOVED)")
    boolean existsByHostPortAndNotRemoved(@Param("hostPort") int hostPort);
}
