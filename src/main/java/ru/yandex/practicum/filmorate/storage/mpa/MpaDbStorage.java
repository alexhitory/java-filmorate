package ru.yandex.practicum.filmorate.storage.mpa;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Mpa;

import java.util.List;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MpaDbStorage {
    private final JdbcTemplate jdbc;

    public List<Mpa> findAll() {
        return jdbc.query("SELECT id, name FROM mpa ORDER BY id",
                (rs, row) -> new Mpa(rs.getInt("id"), rs.getString("name")));
    }

    public Mpa findById(int id) {
        return jdbc.query("SELECT id, name FROM mpa WHERE id = ?",
                        (rs, row) -> new Mpa(rs.getInt("id"), rs.getString("name")), id)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("MPA not found: " + id));
    }
}
