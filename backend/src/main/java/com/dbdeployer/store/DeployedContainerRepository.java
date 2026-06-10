package com.dbdeployer.store;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.InstanceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeployedContainerRepository extends JpaRepository<DeployedContainer, String> {
  Optional<DeployedContainer> findByConfigId(
    String configId);

  boolean existsByContainerName(
    String containerName);

  boolean existsByContainerId(
    String containerId);

  Optional<DeployedContainer> findByContainerId(
    String containerId);

  /** All containers not yet REMOVED — used by the status sync scheduler. */
  List<DeployedContainer> findByStatusNot(
    InstanceStatus status);

  /**
   * All containers not in any of the given statuses — used by the status sync
   * scheduler.
   */
  List<DeployedContainer> findByStatusNotIn(
    Collection<InstanceStatus> statuses);

  /** All containers in a specific status — used by DeploymentRecovery. */
  List<DeployedContainer> findByStatus(
    InstanceStatus status);

  List<DeployedContainer> findByConfigIdOrderByCreatedAtDesc(
    String configId);

  Optional<DeployedContainer> findFirstByConfigIdOrderByCreatedAtDesc(
    String configId);

  Optional<DeployedContainer> findFirstByConfigIdAndStatusNotOrderByCreatedAtDesc(
    String configId,
    InstanceStatus status);

  /**
   * Returns true if any non-removed instance is already using this port. REMOVED
   * configs do not block port reuse.
   */
  @Query("SELECT CASE WHEN COUNT(dc) > 0 THEN true ELSE false END FROM DeployedContainer dc "
      + "WHERE dc.hostPort = :hostPort AND " + "(dc.status <> com.dbdeployer.model.InstanceStatus.REMOVED)")
  boolean existsByHostPortAndNotRemoved(
    @Param("hostPort") int hostPort);
}
