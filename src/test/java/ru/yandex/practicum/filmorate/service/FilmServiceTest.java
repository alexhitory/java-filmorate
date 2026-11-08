package ru.yandex.practicum.filmorate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.film.InMemoryFilmStorage;
import ru.yandex.practicum.filmorate.storage.user.InMemoryUserStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilmServiceTest {
    private FilmService filmService;
    private UserStorage userStorage;
    private Film validFilm;

    @BeforeEach
    void setUp() {
        userStorage = new InMemoryUserStorage();
        filmService = new FilmService(new InMemoryFilmStorage(), userStorage);
        validFilm = new Film();
        validFilm.setName("Test Film");
        validFilm.setDescription("Description");
        validFilm.setReleaseDate(LocalDate.of(2000, 1, 1));
        validFilm.setDuration(120);
    }

    @Test
    void createValidFilm() {
        Film created = filmService.create(validFilm);
        assertEquals(1, created.getId());
        assertEquals(1, filmService.findAll().size());
    }

    @Test
    void createFilmWithEmptyName() {
        validFilm.setName("");
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithBlankName() {
        validFilm.setName("   ");
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithNullName() {
        validFilm.setName(null);
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithDescriptionExactly200Characters() {
        validFilm.setDescription("a".repeat(200));
        assertDoesNotThrow(() -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithDescription201Characters() {
        validFilm.setDescription("a".repeat(201));
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithNullDescription() {
        validFilm.setDescription(null);
        assertDoesNotThrow(() -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithMinReleaseDate() {
        validFilm.setReleaseDate(LocalDate.of(1895, 12, 28));
        assertDoesNotThrow(() -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithReleaseDateBeforeMin() {
        validFilm.setReleaseDate(LocalDate.of(1895, 12, 27));
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithNullReleaseDate() {
        validFilm.setReleaseDate(null);
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithReleaseDateWithinOneYear() {
        validFilm.setReleaseDate(LocalDate.now().plusYears(1));
        assertDoesNotThrow(() -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithReleaseDateMoreThanOneYearInFuture() {
        validFilm.setReleaseDate(LocalDate.now().plusYears(1).plusDays(1));
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithZeroDuration() {
        validFilm.setDuration(0);
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithNegativeDuration() {
        validFilm.setDuration(-1);
        assertThrows(ValidationException.class, () -> filmService.create(validFilm));
    }

    @Test
    void createFilmWithPositiveDuration() {
        validFilm.setDuration(1);
        assertDoesNotThrow(() -> filmService.create(validFilm));
    }

    @Test
    void updateExistingFilm() {
        Film created = filmService.create(validFilm);
        created.setName("Updated Film");
        Film updated = filmService.update(created);
        assertEquals("Updated Film", updated.getName());
    }

    @Test
    void updateNonExistingFilm() {
        validFilm.setId(999);
        assertThrows(NotFoundException.class, () -> filmService.update(validFilm));
    }

    @Test
    void createFilmWithEmptyRequest() {
        Film emptyFilm = new Film();
        assertThrows(ValidationException.class, () -> filmService.create(emptyFilm));
    }

    @Test
    void addLikeCountsUserOnlyOnce() {
        Film film = filmService.create(validFilm);
        User user = createUser();

        filmService.addLike(film.getId(), user.getId());
        filmService.addLike(film.getId(), user.getId());

        assertEquals(1, filmService.findById(film.getId()).getLikes().size());
    }

    @Test
    void getPopularFilmsUsesLikesOrderAndDefaultLimit() {
        Film first = filmService.create(validFilm);
        Film second = new Film();
        second.setName("Second Film");
        second.setDescription("Description");
        second.setReleaseDate(LocalDate.of(2001, 1, 1));
        second.setDuration(90);
        second = filmService.create(second);
        User user = createUser();

        filmService.addLike(second.getId(), user.getId());

        assertEquals(second.getId(), filmService.getPopularFilms(null).get(0).getId());
        assertEquals(2, filmService.getPopularFilms(null).size());
        assertEquals(first.getId(), filmService.getPopularFilms(2).get(1).getId());
    }

    @Test
    void getPopularFilmsWithNegativeCountThrowsValidationException() {
        assertThrows(ValidationException.class, () -> filmService.getPopularFilms(-1));
    }

    @Test
    void getPopularFilmsWithZeroCountThrowsValidationException() {
        assertThrows(ValidationException.class, () -> filmService.getPopularFilms(0));
    }

    @Test
    void addLikeByUnknownUserThrowsNotFoundException() {
        Film film = filmService.create(validFilm);
        assertThrows(NotFoundException.class, () -> filmService.addLike(film.getId(), 999));
    }

    private User createUser() {
        User user = new User();
        user.setEmail("user@mail.ru");
        user.setLogin("user");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return userStorage.create(user);
    }
}
