package com.dbdeployer.store;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.InstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeployedContainerRepository extends JpaRepository<DeployedContainer, String> {
    Optional<DeployedContainer> findByConfigId(String configId);
    boolean existsByContainerId(String containerId);
    Optional<DeployedContainer> findByContainerId(String containerId);
    /** All containers not yet REMOVED — used by the status sync scheduler. */
    List<DeployedContainer> findByStatusNot(InstanceStatus status);
    /** All containers in a specific status — used by DeploymentRecovery. */
    List<DeployedContainer> findByStatus(InstanceStatus status);
}
