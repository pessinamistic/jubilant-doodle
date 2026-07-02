package com.dbdeployer.store;

import com.dbdeployer.model.ModelRuntimeEntity;
import com.dbdeployer.runtime.ModelRuntime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelRuntimeRepository extends JpaRepository<ModelRuntimeEntity, String> {

  Optional<ModelRuntimeEntity> findByConfigId(String configId);

  Optional<ModelRuntimeEntity> findFirstByBaseUrl(String baseUrl);

  List<ModelRuntimeEntity> findByRuntimeType(ModelRuntime runtimeType);

  void deleteByConfigId(String configId);
}
