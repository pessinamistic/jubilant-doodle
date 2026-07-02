package com.dbdeployer.store;

import com.dbdeployer.model.PulledModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PulledModelRepository extends JpaRepository<PulledModel, String> {

  List<PulledModel> findByRuntimeId(String runtimeId);

  Optional<PulledModel> findByRuntimeIdAndModelName(String runtimeId, String modelName);

  /** Settings lookup by model tag alone — the newest pull wins if several runtimes have it. */
  Optional<PulledModel> findFirstByModelNameOrderByPulledAtDesc(String modelName);
}
