package com.example.ipchat.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:godtest;DB_CLOSE_DELAY=-1", "chat.redis.enabled=false"})
@AutoConfigureMockMvc
class GodControllerTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test void publicEditorCanCreateEditAndDelete() throws Exception {
        mvc.perform(get("/god")).andExpect(status().isFound()).andExpect(header().string("Location","/god.html"));
        String json=mvc.perform(post("/api/god/messages").contentType("application/json").content("{\"content\":\"before\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ref=mapper.readTree(json).get("messageRef").asText();
        mvc.perform(put("/api/god/messages/"+ref).contentType("application/json").content("{\"content\":\"after\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("after"));
        mvc.perform(delete("/api/god/messages/"+ref)).andExpect(status().isNoContent());
        mvc.perform(put("/api/god/messages/"+ref).contentType("application/json").content("{\"content\":\"after\"}"))
            .andExpect(status().isNotFound());
    }
    @Test void blankMessagesAreRejected() throws Exception {
        mvc.perform(post("/api/god/messages").contentType("application/json").content("{\"content\":\" \"}"))
            .andExpect(status().isBadRequest());
    }
}
