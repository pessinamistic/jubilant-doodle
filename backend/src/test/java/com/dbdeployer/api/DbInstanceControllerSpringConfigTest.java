package com.dbdeployer.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.service.ConfigTemplateService;
import com.dbdeployer.service.DbInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Verifies the Phase 1 "Copy Spring config" endpoint on the instance controller. */
@ExtendWith(MockitoExtension.class)
class DbInstanceControllerSpringConfigTest {

  @Mock private DbInstanceService service;
  @Mock private ConnectionStringBuilder connBuilder;
  @Mock private InstanceResponseAssembler responseAssembler;
  @Mock private ConfigTemplateService configTemplateService;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    var controller =
        new DbInstanceController(service, connBuilder, responseAssembler, configTemplateService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void returns_spring_config_block_for_instance() throws Exception {
    var config = new DeploymentConfig();
    config.setId("abc");
    when(configTemplateService.getById("abc", true)).thenReturn(config);
    when(connBuilder.springBootProperties(config))
        .thenReturn("spring.datasource.url=jdbc:postgresql://localhost:5432/mydb");

    mockMvc
        .perform(get("/instances/abc/spring-config"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.springConfig")
                .value("spring.datasource.url=jdbc:postgresql://localhost:5432/mydb"));
  }
}
