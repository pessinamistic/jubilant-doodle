package com.dbdeployer.store;

import com.dbdeployer.model.DbInstance;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.InstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DbInstanceRepository extends JpaRepository<DbInstance, String> {
    List<DbInstance> findByDbType(DbType dbType);
    List<DbInstance> findByStatus(InstanceStatus status);
    boolean existsByName(String name);
    boolean existsByHostPort(int hostPort);
    boolean existsByContainerId(String containerId);
}
