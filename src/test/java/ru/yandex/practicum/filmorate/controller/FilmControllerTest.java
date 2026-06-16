package ru.yandex.practicum.filmorate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilmControllerTest {
    private FilmController filmController;
    private Film validFilm;

    @BeforeEach
    void setUp() {
        filmController = new FilmController();
        validFilm = new Film();
        validFilm.setName("Test Film");
        validFilm.setDescription("Description");
        validFilm.setReleaseDate(LocalDate.of(2000, 1, 1));
        validFilm.setDuration(120);
    }

    @Test
    void createValidFilm() {
        Film created = filmController.create(validFilm);
        assertEquals(1, created.getId());
        assertEquals(1, filmController.findAll().size());
    }

    @Test
    void createFilmWithEmptyName() {
        validFilm.setName("");
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithBlankName() {
        validFilm.setName("   ");
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithNullName() {
        validFilm.setName(null);
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithDescriptionExactly200Characters() {
        validFilm.setDescription("a".repeat(200));
        assertDoesNotThrow(() -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithDescription201Characters() {
        validFilm.setDescription("a".repeat(201));
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithNullDescription() {
        validFilm.setDescription(null);
        assertDoesNotThrow(() -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithMinReleaseDate() {
        validFilm.setReleaseDate(LocalDate.of(1895, 12, 28));
        assertDoesNotThrow(() -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithReleaseDateBeforeMin() {
        validFilm.setReleaseDate(LocalDate.of(1895, 12, 27));
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithNullReleaseDate() {
        validFilm.setReleaseDate(null);
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithZeroDuration() {
        validFilm.setDuration(0);
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithNegativeDuration() {
        validFilm.setDuration(-1);
        assertThrows(ValidationException.class, () -> filmController.create(validFilm));
    }

    @Test
    void createFilmWithPositiveDuration() {
        validFilm.setDuration(1);
        assertDoesNotThrow(() -> filmController.create(validFilm));
    }

    @Test
    void updateExistingFilm() {
        Film created = filmController.create(validFilm);
        created.setName("Updated Film");
        Film updated = filmController.update(created);
        assertEquals("Updated Film", updated.getName());
    }

    @Test
    void updateNonExistingFilm() {
        validFilm.setId(999);
        assertThrows(ValidationException.class, () -> filmController.update(validFilm));
    }

    @Test
    void createFilmWithEmptyRequest() {
        Film emptyFilm = new Film();
        assertThrows(ValidationException.class, () -> filmController.create(emptyFilm));
    }
}
