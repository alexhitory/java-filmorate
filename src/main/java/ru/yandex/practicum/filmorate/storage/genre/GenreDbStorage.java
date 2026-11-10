package ru.yandex.practicum.filmorate.storage.genre;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Genre;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenreDbStorage {
    private final JdbcTemplate jdbc;

    public List<Genre> findAll() {
        return jdbc.query("SELECT id, name FROM genres ORDER BY id",
                (rs, row) -> new Genre(rs.getInt("id"), rs.getString("name")));
    }

    public Genre findById(int id) {
        return jdbc.query("SELECT id, name FROM genres WHERE id = ?",
                        (rs, row) -> new Genre(rs.getInt("id"), rs.getString("name")), id)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Genre not found: " + id));
    }
}
