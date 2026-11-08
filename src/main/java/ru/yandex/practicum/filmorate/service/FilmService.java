package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
public class FilmService {
    private static final LocalDate MIN_RELEASE_DATE = LocalDate.of(1895, 12, 28);
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int DEFAULT_POPULAR_COUNT = 10;

    private final FilmStorage filmStorage;
    private final UserStorage userStorage;

    @Autowired
    public FilmService(FilmStorage filmStorage, UserStorage userStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
    }

    public Film create(Film film) {
        validate(film);
        Film created = filmStorage.create(film);
        log.info("Добавлен новый фильм с id={}: {}", created.getId(), created.getName());
        return created;
    }

    public Film update(Film film) {
        validate(film);
        Film existing = getFilmById(film.getId());
        film.setLikes(existing.getLikes());
        Film updated = filmStorage.update(film);
        log.info("Обновлён фильм с id={}: {}", updated.getId(), updated.getName());
        return updated;
    }

    public Collection<Film> findAll() {
        log.debug("Запрошен список всех фильмов");
        return filmStorage.findAll();
    }

    public Film findById(long id) {
        log.debug("Запрошен фильм с id={}", id);
        return getFilmById(id);
    }

    public void addLike(long filmId, long userId) {
        Film film = getFilmById(filmId);
        checkUserExists(userId);
        film.getLikes().add(userId);
        filmStorage.update(film);
        log.info("Пользователь с id={} поставил лайк фильму с id={}", userId, filmId);
    }

    public void removeLike(long filmId, long userId) {
        Film film = getFilmById(filmId);
        checkUserExists(userId);
        film.getLikes().remove(userId);
        filmStorage.update(film);
        log.info("Пользователь с id={} удалил лайк у фильма с id={}", userId, filmId);
    }

    public List<Film> getPopularFilms(Integer count) {
        int limit = count == null ? DEFAULT_POPULAR_COUNT : count;
        if (limit <= 0) {
            throw new ValidationException("Количество популярных фильмов должно быть положительным числом");
        }
        log.debug("Запрошены {} популярных фильмов", limit);
        return filmStorage.findPopular(limit);
    }

    private Film getFilmById(long id) {
        return filmStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Фильм с id " + id + " не найден"));
    }

    private void checkUserExists(long id) {
        userStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id " + id + " не найден"));
    }

    private void validate(Film film) {
        if (film.getName() == null || film.getName().isBlank()) {
            log.error("Ошибка валидации фильма: название не может быть пустым");
            throw new ValidationException("Название фильма не может быть пустым");
        }
        if (film.getDescription() != null && film.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            log.error("Ошибка валидации фильма: описание превышает {} символов", MAX_DESCRIPTION_LENGTH);
            throw new ValidationException("Максимальная длина описания — 200 символов");
        }
        if (film.getReleaseDate() == null || film.getReleaseDate().isBefore(MIN_RELEASE_DATE)) {
            log.error("Ошибка валидации фильма: некорректная дата релиза {}", film.getReleaseDate());
            throw new ValidationException("Дата релиза не может быть раньше 28 декабря 1895 года");
        }
        if (film.getReleaseDate().isAfter(LocalDate.now().plusYears(1))) {
            log.error("Ошибка валидации фильма: дата релиза слишком далеко в будущем {}", film.getReleaseDate());
            throw new ValidationException("Дата релиза не может быть более чем на год в будущем");
        }
        if (film.getDuration() <= 0) {
            log.error("Ошибка валидации фильма: некорректная продолжительность {}", film.getDuration());
            throw new ValidationException("Продолжительность фильма должна быть положительным числом");
        }
    }
}
