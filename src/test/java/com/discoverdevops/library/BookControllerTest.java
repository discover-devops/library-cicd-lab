package com.discoverdevops.library;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsTheCatalog() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(3)));
    }

    @Test
    void getReturnsOneBook() throws Exception {
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("The Phoenix Project"));
    }

    @Test
    void getUnknownBookReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/books/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturnsCreatedBook() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Accelerate\",\"author\":\"Nicole Forsgren\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Accelerate"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void createWithoutTitleIsRejected() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Nobody\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithBlankTitleIsRejected() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"author\":\"Nobody\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutAuthorIsRejected() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Untitled\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borrowOnceSucceedsAndTwiceConflicts() throws Exception {
        String body = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Release It\",\"author\":\"Michael Nygard\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");

        mockMvc.perform(post("/api/books/" + id + "/borrow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(post("/api/books/" + id + "/borrow"))
                .andExpect(status().isConflict());
    }

    @Test
    void borrowUnknownBookReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/books/9999/borrow"))
                .andExpect(status().isNotFound());
    }
}
