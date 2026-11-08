package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.storage.genre.GenreDbStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaDbStorage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FilmDbStorage implements FilmStorage {
    private static final String SELECT_FILMS = "SELECT f.*, m.name AS mpa_name FROM films f "
            + "JOIN mpa m ON m.id = f.mpa_id ";
    private final JdbcTemplate jdbc;
    private final GenreDbStorage genreStorage;
    private final MpaDbStorage mpaStorage;

    @Override
    @Transactional
    public Film create(Film film) {
        validateReferences(film);
        Map<String, Object> values = new HashMap<>();
        values.put("name", film.getName());
        values.put("description", film.getDescription());
        values.put("release_date", film.getReleaseDate());
        values.put("duration", film.getDuration());
        values.put("mpa_id", film.getMpa().getId());
        long id = new SimpleJdbcInsert(jdbc).withTableName("films").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(values).longValue();
        film.setId(id);
        saveRelations(film);
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public Film update(Film film) {
        if (findById(film.getId()).isEmpty()) {
            throw new NotFoundException("Film not found: " + film.getId());
        }
        validateReferences(film);
        jdbc.update("UPDATE films SET name = ?, description = ?, release_date = ?, duration = ?, mpa_id = ? WHERE id = ?",
                film.getName(), film.getDescription(), film.getReleaseDate(), film.getDuration(),
                film.getMpa().getId(), film.getId());
        saveRelations(film);
        return findById(film.getId()).orElseThrow();
    }

    @Override
    @Transactional
    public void delete(long id) {
        jdbc.update("DELETE FROM films WHERE id = ?", id);
    }

    @Override
    public Optional<Film> findById(long id) {
        return jdbc.query(SELECT_FILMS + "WHERE f.id = ?", this::mapFilm, id).stream().findFirst();
    }

    @Override
    public Collection<Film> findAll() {
        return jdbc.query(SELECT_FILMS + "ORDER BY f.id", this::mapFilm);
    }

    @Override
    public List<Film> findPopular(int count) {
        return jdbc.query(SELECT_FILMS + "ORDER BY (SELECT COUNT(*) FROM film_likes l WHERE l.film_id = f.id) DESC, "
                + "f.id LIMIT ?", this::mapFilm, count);
    }

    private void validateReferences(Film film) {
        if (film.getMpa() == null) {
            throw new ValidationException("MPA is required");
        }
        mpaStorage.findById(film.getMpa().getId());
        if (film.getGenres() != null) {
            for (Genre genre : film.getGenres()) {
                if (genre == null) {
                    throw new ValidationException("Genre must not be null");
                }
                genreStorage.findById(genre.getId());
            }
        }
    }

    private void saveRelations(Film film) {
        jdbc.update("DELETE FROM film_genres WHERE film_id = ?", film.getId());
        Set<Integer> genreIds = new LinkedHashSet<>();
        if (film.getGenres() != null) {
            film.getGenres().forEach(genre -> genreIds.add(genre.getId()));
        }
        for (Integer genreId : genreIds) {
            jdbc.update("INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)", film.getId(), genreId);
        }
        jdbc.update("DELETE FROM film_likes WHERE film_id = ?", film.getId());
        for (Long userId : film.getLikes()) {
            jdbc.update("INSERT INTO film_likes (film_id, user_id) VALUES (?, ?)", film.getId(), userId);
        }
    }

    private Film mapFilm(ResultSet rs, int row) throws SQLException {
        Film film = new Film();
        film.setId(rs.getLong("id"));
        film.setName(rs.getString("name"));
        film.setDescription(rs.getString("description"));
        film.setReleaseDate(rs.getDate("release_date").toLocalDate());
        film.setDuration(rs.getInt("duration"));
        film.setMpa(new Mpa(rs.getInt("mpa_id"), rs.getString("mpa_name")));
        film.setGenres(new LinkedHashSet<>(jdbc.query("SELECT g.id, g.name FROM genres g "
                        + "JOIN film_genres fg ON fg.genre_id = g.id WHERE fg.film_id = ? ORDER BY g.id",
                (genres, index) -> new Genre(genres.getInt("id"), genres.getString("name")), film.getId())));
        film.setLikes(new LinkedHashSet<>(jdbc.queryForList(
                "SELECT user_id FROM film_likes WHERE film_id = ? ORDER BY user_id", Long.class, film.getId())));
        return film;
    }
}
