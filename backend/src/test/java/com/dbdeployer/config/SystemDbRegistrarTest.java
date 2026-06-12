package com.dbdeployer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SystemDbRegistrarTest {

  @Mock
  private DeploymentConfigRepository configRepo;

  @Mock
  private DeployedContainerRepository containerRepo;

  @Mock
  private JdbcTemplate jdbc;

  @Test
  void run_createsSystemRowsOnce_andEnrichesContainerId() {
    when(configRepo.findById(SystemDbRegistrar.SYSTEM_CONFIG_ID)).thenReturn(Optional.empty());
    when(containerRepo.findByConfigId(SystemDbRegistrar.SYSTEM_CONFIG_ID)).thenReturn(Optional.empty());
    when(jdbc.queryForObject("SELECT version()", String.class))
        .thenReturn("PostgreSQL 16.3 on aarch64-unknown-linux-musl");

    SystemDbRegistrar registrar = new SystemDbRegistrar(jdbc, configRepo, containerRepo);
    ReflectionTestUtils.setField(registrar, "systemDbHostPort", 5499);
    ReflectionTestUtils.setField(registrar, "systemDbContainerName", "dbdeployer-system-db");
    ReflectionTestUtils.setField(registrar, "autoProvision", true);
    ReflectionTestUtils.setField(registrar, "runtimeContainerId", "abcdef1234567890");
    ReflectionTestUtils.setField(registrar, "runtimeContainerName", "dbdeployer-system-db");

    registrar.run(null);

    ArgumentCaptor<DeploymentConfig> cfgCaptor = ArgumentCaptor.forClass(DeploymentConfig.class);
    verify(configRepo).save(cfgCaptor.capture());
    DeploymentConfig savedConfig = cfgCaptor.getValue();
    assertThat(savedConfig.getId()).isEqualTo("system");
    assertThat(savedConfig.isSystem()).isTrue();
    assertThat(savedConfig.getDeployMethod()).isEqualTo(DeployMethod.DOCKER);
    assertThat(savedConfig.getVersion()).isEqualTo("16.3");

    ArgumentCaptor<DeployedContainer> containerCaptor = ArgumentCaptor.forClass(DeployedContainer.class);
    verify(containerRepo).save(containerCaptor.capture());
    DeployedContainer savedContainer = containerCaptor.getValue();
    assertThat(savedContainer.getContainerId()).isEqualTo("abcdef1234567890");
    assertThat(savedContainer.getContainerName()).isEqualTo("dbdeployer-system-db");
    assertThat(savedContainer.getStatus()).isEqualTo(InstanceStatus.RUNNING);
    assertThat(savedContainer.getStartedAt()).isNotNull();
  }

  @Test
  void run_doesNotRewrite_whenSystemRowsAlreadyPresentAndComplete() {
    DeploymentConfig existingConfig = new DeploymentConfig();
    existingConfig.setId("system");
    existingConfig.setSystem(true);
    existingConfig.setDeployMethod(DeployMethod.DOCKER);

    DeployedContainer existingContainer = new DeployedContainer();
    existingContainer.setId("c1");
    existingContainer.setConfig(existingConfig);
    existingContainer.setContainerName("dbdeployer-system-db");
    existingContainer.setContainerId("abcdef1234567890");
    existingContainer.setStatus(InstanceStatus.RUNNING);
    existingContainer.setStartedAt(Instant.now());

    when(configRepo.findById(SystemDbRegistrar.SYSTEM_CONFIG_ID)).thenReturn(Optional.of(existingConfig));
    when(containerRepo.findByConfigId(SystemDbRegistrar.SYSTEM_CONFIG_ID)).thenReturn(Optional.of(existingContainer));

    SystemDbRegistrar registrar = new SystemDbRegistrar(jdbc, configRepo, containerRepo);
    ReflectionTestUtils.setField(registrar, "systemDbHostPort", 5499);
    ReflectionTestUtils.setField(registrar, "systemDbContainerName", "dbdeployer-system-db");
    ReflectionTestUtils.setField(registrar, "autoProvision", true);
    ReflectionTestUtils.setField(registrar, "runtimeContainerId", "abcdef1234567890");
    ReflectionTestUtils.setField(registrar, "runtimeContainerName", "dbdeployer-system-db");

    registrar.run(null);

    verify(configRepo, never()).save(any(DeploymentConfig.class));
    verify(containerRepo, never()).save(any(DeployedContainer.class));
  }
}
