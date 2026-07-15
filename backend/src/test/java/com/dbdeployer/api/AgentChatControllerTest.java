package com.dbdeployer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbdeployer.ai.AgentChatService;
import com.dbdeployer.ai.AgentEvent;
import com.dbdeployer.ai.ModelSelection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AgentChatControllerTest {

  @Mock private AgentChatService agentChatService;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AgentChatController(agentChatService)).build();
  }

  private static Flux<ServerSentEvent<AgentEvent>> sampleStream() {
    return Flux.just(
        ServerSentEvent.<AgentEvent>builder(AgentEvent.text("hello")).event("token").build(),
        ServerSentEvent.<AgentEvent>builder(AgentEvent.text("")).event("done").build());
  }

  @Test
  void streams_events_and_defaults_approve_to_false() throws Exception {
    when(agentChatService.stream(eq("hi"), any(ModelSelection.class), eq(false)))
        .thenReturn(sampleStream());

    MvcResult result =
        mockMvc
            .perform(get("/chat/agent/stream").param("message", "hi"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("token")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("done")));

    verify(agentChatService).stream(eq("hi"), any(ModelSelection.class), eq(false));
  }

  @Test
  void passes_approve_true_through_for_confirmation() throws Exception {
    when(agentChatService.stream(eq("delete redis"), any(ModelSelection.class), eq(true)))
        .thenReturn(
            Flux.just(
                ServerSentEvent.<AgentEvent>builder(AgentEvent.text("")).event("done").build()));

    MvcResult result =
        mockMvc
            .perform(
                get("/chat/agent/stream").param("message", "delete redis").param("approve", "true"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

    verify(agentChatService).stream(eq("delete redis"), any(ModelSelection.class), eq(true));
  }
}
