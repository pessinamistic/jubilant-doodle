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
    /** All containers that are not yet REMOVED — used by the status sync scheduler. */
    List<DeployedContainer> findByStatusNot(InstanceStatus status);
}
