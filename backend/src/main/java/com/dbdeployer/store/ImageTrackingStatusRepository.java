package com.dbdeployer.store;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.ImageTrackingStatus;

@Repository
public interface ImageTrackingStatusRepository extends JpaRepository<ImageTrackingStatus, String> {
    Optional<ImageTrackingStatus> findByDbTypeAndImageNameAndImageTag(DbType dbType, String imageName, String imageTag);
    List<ImageTrackingStatus> findAllByOrderByDbTypeAscImageNameAscImageTagAsc();
    List<ImageTrackingStatus> findByDbTypeOrderByImageNameAscImageTagAsc(DbType dbType);
}
