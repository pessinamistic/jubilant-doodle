package com.dbdeployer.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbdeployer.service.ComposeExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ComposeExportControllerTest {

  @Mock private ComposeExportService composeExportService;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ComposeExportController(composeExportService)).build();
  }

  @Test
  void returns_yaml_as_an_attachment_download() throws Exception {
    when(composeExportService.exportYaml()).thenReturn("version: \"3.9\"\nservices: {}\n");

    mockMvc
        .perform(get("/export/docker-compose"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/x-yaml"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"docker-compose.yml\""))
        .andExpect(content().string("version: \"3.9\"\nservices: {}\n"));
  }
}
