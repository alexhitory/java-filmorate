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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        return queryUsers("SELECT * FROM users WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public Collection<User> findAll() {
        return queryUsers("SELECT * FROM users");
    }

    @Override
    public List<User> findFriends(long userId) {
        return queryUsers("SELECT u.* FROM users u JOIN friendships f ON f.friend_id = u.id WHERE f.user_id = ?",
                userId);
    }

    @Override
    public List<User> findCommonFriends(long userId, long otherId) {
        return queryUsers("SELECT u.* FROM users u JOIN friendships a ON a.friend_id = u.id "
                + "JOIN friendships b ON b.friend_id = u.id WHERE a.user_id = ? AND b.user_id = ?",
                userId, otherId);
    }

    private List<User> queryUsers(String selection, Object... args) {
        return jdbc.query("SELECT u.*, f.friend_id FROM (" + selection + ") u "
                + "LEFT JOIN friendships f ON f.user_id = u.id ORDER BY u.id, f.friend_id", rs -> {
                    Map<Long, User> users = new LinkedHashMap<>();
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        User user = users.get(id);
                        if (user == null) {
                            user = mapUser(rs);
                            users.put(id, user);
                        }
                        Long friendId = rs.getObject("friend_id", Long.class);
                        if (friendId != null) {
                            user.getFriends().add(friendId);
                        }
                    }
                    return new ArrayList<>(users.values());
                }, args);
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setEmail(rs.getString("email"));
        user.setLogin(rs.getString("login"));
        user.setName(rs.getString("name"));
        user.setBirthday(rs.getDate("birthday").toLocalDate());
        return user;
    }

    private void saveFriends(User user) {
        jdbc.update("DELETE FROM friendships WHERE user_id = ?", user.getId());
        if (!user.getFriends().isEmpty()) {
            jdbc.batchUpdate("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?)",
                    user.getFriends(), user.getFriends().size(), (statement, friendId) -> {
                        statement.setLong(1, user.getId());
                        statement.setLong(2, friendId);
                    });
        }
    }
}
