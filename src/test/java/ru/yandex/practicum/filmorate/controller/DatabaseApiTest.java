package ru.yandex.practicum.filmorate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DatabaseApiTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void referenceEndpointsReturnNamesAnd404ForMissingIds() throws Exception {
        mvc.perform(get("/mpa")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].id").value(1)).andExpect(jsonPath("$[4].name").value("NC-17"));
        mvc.perform(get("/mpa/3")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("PG-13"));
        mvc.perform(get("/genres")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/genres/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Комедия"));
        for (String path : new String[]{"/mpa/999", "/mpa/0", "/genres/999", "/genres/-1"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void filmsAcceptReferenceIdsAndReturnCanonicalSortedGenres() throws Exception {
        JsonNode film = response(mvc.perform(post("/films").contentType(APPLICATION_JSON).content("""
                {"name":"Film", "description":"Description", "releaseDate":"2000-01-01", "duration":120,
                 "mpa":{"id":3}, "genres":[{"id":3},{"id":1},{"id":3,"name":"ignored"}]}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.mpa.name").value("PG-13"))
                .andExpect(jsonPath("$.genres", hasSize(2))).andExpect(jsonPath("$.genres[0].id").value(1))
                .andReturn());
        long id = film.get("id").asLong();
        mvc.perform(get("/films/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.genres[1].id").value(3));
        mvc.perform(put("/films").contentType(APPLICATION_JSON).content("""
                {"id":%d,"name":"Changed", "releaseDate":"2000-01-01", "duration":100,
                 "mpa":{"id":5}, "genres":[{"id":2}]}
                """.formatted(id))).andExpect(status().isOk()).andExpect(jsonPath("$.mpa.name").value("NC-17"))
                .andExpect(jsonPath("$.genres", hasSize(1))).andExpect(jsonPath("$.genres[0].id").value(2));
        mvc.perform(put("/films").contentType(APPLICATION_JSON).content("""
                {"id":%d,"name":"Changed", "releaseDate":"2000-01-01", "duration":100, "mpa":{"id":1}}
                """.formatted(id))).andExpect(status().isOk()).andExpect(jsonPath("$.genres", hasSize(0)));
        mvc.perform(get("/films")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void filmValidationRejectsUnknownReferencesAndMissingMpa() throws Exception {
        String template = """
                {"name":"Film", "releaseDate":"2000-01-01", "duration":120, %s}
                """;
        mvc.perform(post("/films").contentType(APPLICATION_JSON)
                .content(template.formatted("\"mpa\":{\"id\":999}"))).andExpect(status().isNotFound());
        mvc.perform(post("/films").contentType(APPLICATION_JSON)
                .content(template.formatted("\"mpa\":{\"id\":1},\"genres\":[{\"id\":999}]")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/films").contentType(APPLICATION_JSON)
                .content(template.formatted("\"genres\":[]"))).andExpect(status().isBadRequest());
        mvc.perform(get("/films")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void friendshipRequestsConfirmationRemovalAndCommonFriends() throws Exception {
        long first = createUser("first");
        long second = createUser("second");
        long common = createUser("common");
        mvc.perform(put("/users/{id}/friends/{friendId}", first, second)).andExpect(status().isOk());
        mvc.perform(put("/users/{id}/friends/{friendId}", first, second)).andExpect(status().isOk());
        mvc.perform(get("/users/{id}/friends", first)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second));
        mvc.perform(get("/users/{id}/friends", second)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put("/users/{id}/friends/{friendId}", second, first)).andExpect(status().isOk());
        mvc.perform(delete("/users/{id}/friends/{friendId}", first, second)).andExpect(status().isOk());
        mvc.perform(get("/users/{id}/friends", second)).andExpect(jsonPath("$[0].id").value(first));
        mvc.perform(get("/users/{id}/friends", first)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put("/users/{id}/friends/{friendId}", first, common)).andExpect(status().isOk());
        mvc.perform(put("/users/{id}/friends/{friendId}", second, common)).andExpect(status().isOk());
        mvc.perform(get("/users/{id}/friends/common/{otherId}", first, second))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(common));
        mvc.perform(put("/users/{id}/friends/{friendId}", first, 999)).andExpect(status().isNotFound());
        mvc.perform(put("/users/{id}/friends/{friendId}", first, first)).andExpect(status().isBadRequest());
    }

    @Test
    void likesPersistAcrossFilmUpdatesAndAffectPopularity() throws Exception {
        long user = createUser("viewer");
        long first = createFilm("First");
        long second = createFilm("Second");
        mvc.perform(put("/films/{id}/like/{userId}", second, user)).andExpect(status().isOk());
        mvc.perform(put("/films/{id}/like/{userId}", second, user)).andExpect(status().isOk());
        mvc.perform(put("/films").contentType(APPLICATION_JSON).content("""
                {"id":%d,"name":"Updated", "releaseDate":"2000-01-01", "duration":100,"mpa":{"id":2}}
                """.formatted(second))).andExpect(status().isOk()).andExpect(jsonPath("$.likes", hasSize(1)));
        mvc.perform(get("/films/popular").param("count", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(second));
        mvc.perform(delete("/films/{id}/like/{userId}", second, user)).andExpect(status().isOk());
        mvc.perform(delete("/films/{id}/like/{userId}", second, user)).andExpect(status().isOk());
        mvc.perform(get("/films/popular")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(first));
        mvc.perform(put("/films/{id}/like/{userId}", second, 999)).andExpect(status().isNotFound());
        mvc.perform(put("/films/{id}/like/{userId}", 999, user)).andExpect(status().isNotFound());
    }

    private long createUser(String login) throws Exception {
        return response(mvc.perform(post("/users").contentType(APPLICATION_JSON).content("""
                {"email":"%s@example.com","login":"%s","birthday":"1990-01-01"}
                """.formatted(login, login))).andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    private long createFilm(String name) throws Exception {
        return response(mvc.perform(post("/films").contentType(APPLICATION_JSON).content("""
                {"name":"%s", "releaseDate":"2000-01-01", "duration":120,"mpa":{"id":1}}
                """.formatted(name))).andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    private JsonNode response(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
