package ru.yandex.practicum.filmorate.storage.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDbStorage implements UserStorage {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public User create(User user) {
        Map<String, Object> values = new HashMap<>();
        values.put("email", user.getEmail());
        values.put("login", user.getLogin());
        values.put("name", user.getName());
        values.put("birthday", user.getBirthday());
        long id = new SimpleJdbcInsert(jdbc).withTableName("users").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(values).longValue();
        user.setId(id);
        saveFriends(user);
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public User update(User user) {
        int updated = jdbc.update("UPDATE users SET email = ?, login = ?, name = ?, birthday = ? WHERE id = ?",
                user.getEmail(), user.getLogin(), user.getName(), user.getBirthday(), user.getId());
        if (updated == 0) {
            throw new NotFoundException("User not found: " + user.getId());
        }
        saveFriends(user);
        return findById(user.getId()).orElseThrow();
    }

    @Override
    @Transactional
    public void delete(long id) {
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    @Override
    public Optional<User> findById(long id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", this::mapUser, id).stream().findFirst();
    }

    @Override
    public Collection<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY id", this::mapUser);
    }

    private User mapUser(ResultSet rs, int row) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setEmail(rs.getString("email"));
        user.setLogin(rs.getString("login"));
        user.setName(rs.getString("name"));
        user.setBirthday(rs.getDate("birthday").toLocalDate());
        user.setFriends(new LinkedHashSet<>(jdbc.queryForList(
                "SELECT friend_id FROM friendships WHERE user_id = ? ORDER BY friend_id", Long.class, user.getId())));
        return user;
    }

    private void saveFriends(User user) {
        jdbc.update("DELETE FROM friendships WHERE user_id = ?", user.getId());
        for (Long friendId : user.getFriends()) {
            jdbc.update("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?)", user.getId(), friendId);
        }
    }
}
