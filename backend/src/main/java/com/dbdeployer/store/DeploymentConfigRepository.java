package com.dbdeployer.store;

import com.dbdeployer.model.DeploymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentConfigRepository extends JpaRepository<DeploymentConfig, String> {
    boolean existsByName(String name);
    boolean existsByHostPort(int hostPort);
}
