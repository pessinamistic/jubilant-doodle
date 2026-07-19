package com.dbdeployer.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbdeployer.service.ComposeExportService;
import java.util.List;
import java.util.Optional;
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
    when(composeExportService.exportYaml(List.of())).thenReturn("version: \"3.9\"\nservices: {}\n");

    mockMvc
        .perform(get("/export/docker-compose"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/x-yaml"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"docker-compose.yml\""))
        .andExpect(content().string("version: \"3.9\"\nservices: {}\n"));
  }

  @Test
  void forwards_comma_separated_config_ids_and_keeps_generic_filename_for_multiple_ids()
      throws Exception {
    when(composeExportService.exportYaml(List.of("id-1", "id-2")))
        .thenReturn("version: \"3.9\"\nservices: {}\n");

    mockMvc
        .perform(get("/export/docker-compose").param("configIds", "id-1, id-2"))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"docker-compose.yml\""));
  }

  @Test
  void single_resolved_id_uses_the_instance_name_in_the_download_filename() throws Exception {
    when(composeExportService.exportYaml(List.of("id-1")))
        .thenReturn("version: \"3.9\"\nservices: {}\n");
    when(composeExportService.resolveInstanceName("id-1")).thenReturn(Optional.of("My Cache!"));

    mockMvc
        .perform(get("/export/docker-compose").param("configIds", "id-1"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"my-cache-compose.yml\""));
  }

  @Test
  void single_unresolved_id_falls_back_to_the_generic_filename() throws Exception {
    when(composeExportService.exportYaml(List.of("missing")))
        .thenReturn("version: \"3.9\"\nservices: {}\n");
    when(composeExportService.resolveInstanceName(eq("missing"))).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/export/docker-compose").param("configIds", "missing"))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"docker-compose.yml\""));
  }
}
