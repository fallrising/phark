package com.example.deck.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPostsReturnsSeededData() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(9))));
    }

    @Test
    void getPostsByChannelFiltersResults() throws Exception {
        mockMvc.perform(get("/api/posts").param("channel", "tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].channel").value("tech"));
    }

    @Test
    void createPostReturnsCreatedPost() throws Exception {
        String body = """
                {
                  "author": "Tester",
                  "content": "Hello from tests",
                  "channel": "home"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("Tester"))
                .andExpect(jsonPath("$.content").value("Hello from tests"))
                .andExpect(jsonPath("$.channel").value("home"));
    }

    @Test
    void createPostWithInvalidChannelReturnsBadRequest() throws Exception {
        String body = """
                {
                  "author": "Tester",
                  "content": "Invalid channel",
                  "channel": "news"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPostWithBlankContentReturnsBadRequest() throws Exception {
        String body = """
                {
                  "author": "Tester",
                  "content": "   ",
                  "channel": "home"
                }
                """;

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPostWithLongAuthorReturnsBadRequest() throws Exception {
        String body = """
                {
                  "author": "%s",
                  "content": "Author is too long",
                  "channel": "home"
                }
                """.formatted("A".repeat(81));

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
