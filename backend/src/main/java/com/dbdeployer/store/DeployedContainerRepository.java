package com.dbdeployer.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.InstanceStatus;

@Repository
public interface DeployedContainerRepository extends JpaRepository<DeployedContainer, String> {
    Optional<DeployedContainer> findByConfigId(String configId);
    boolean existsByContainerId(String containerId);
    Optional<DeployedContainer> findByContainerId(String containerId);
    /** All containers not yet REMOVED — used by the status sync scheduler. */
    List<DeployedContainer> findByStatusNot(InstanceStatus status);
    /** All containers not in any of the given statuses — used by the status sync scheduler. */
    List<DeployedContainer> findByStatusNotIn(Collection<InstanceStatus> statuses);
    /** All containers in a specific status — used by DeploymentRecovery. */
    List<DeployedContainer> findByStatus(InstanceStatus status);
}
