package ru.yandex.practicum.filmorate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class UserService {
    private final UserStorage userStorage;

    @Autowired
    public UserService(UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    public User create(User user) {
        validate(user);
        fillNameIfEmpty(user);
        User created = userStorage.create(user);
        log.info("Создан новый пользователь с id={}: {}", created.getId(), created.getLogin());
        return created;
    }

    public User update(User user) {
        validate(user);
        User existing = getUserById(user.getId());
        fillNameIfEmpty(user);
        user.setFriends(existing.getFriends());
        User updated = userStorage.update(user);
        log.info("Обновлён пользователь с id={}: {}", updated.getId(), updated.getLogin());
        return updated;
    }

    public Collection<User> findAll() {
        log.debug("Запрошен список всех пользователей");
        return userStorage.findAll();
    }

    public User findById(long id) {
        log.debug("Запрошен пользователь с id={}", id);
        return getUserById(id);
    }

    public void addFriend(long userId, long friendId) {
        if (userId == friendId) {
            log.error("Попытка добавить пользователя id={} в друзья самому себе", userId);
            throw new ValidationException("Нельзя добавить самого себя в друзья");
        }
        User user = getUserById(userId);
        User friend = getUserById(friendId);
        user.getFriends().add(friendId);
        friend.getFriends().add(userId);
        userStorage.update(user);
        userStorage.update(friend);
        log.info("Пользователь с id={} добавил в друзья пользователя с id={}", userId, friendId);
    }

    public void removeFriend(long userId, long friendId) {
        User user = getUserById(userId);
        User friend = getUserById(friendId);
        user.getFriends().remove(friendId);
        friend.getFriends().remove(userId);
        userStorage.update(user);
        userStorage.update(friend);
        log.info("Пользователь с id={} удалил из друзей пользователя с id={}", userId, friendId);
    }

    public List<User> getFriends(long userId) {
        User user = getUserById(userId);
        log.debug("Запрошен список друзей пользователя с id={}", userId);
        return user.getFriends().stream()
                .map(this::getUserById)
                .sorted(Comparator.comparingLong(User::getId))
                .toList();
    }

    public List<User> getCommonFriends(long userId, long otherId) {
        User user = getUserById(userId);
        User other = getUserById(otherId);
        log.debug("Запрошен список общих друзей пользователей с id={} и id={}", userId, otherId);
        return user.getFriends().stream()
                .filter(other.getFriends()::contains)
                .map(this::getUserById)
                .sorted(Comparator.comparingLong(User::getId))
                .toList();
    }

    private User getUserById(long id) {
        return userStorage.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id " + id + " не найден"));
    }

    private void fillNameIfEmpty(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
    }

    private void validate(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank() || !user.getEmail().contains("@")) {
            log.error("Ошибка валидации пользователя: некорректный email {}", user.getEmail());
            throw new ValidationException("Электронная почта не может быть пустой и должна содержать символ @");
        }
        if (user.getLogin() == null || user.getLogin().isBlank() || user.getLogin().contains(" ")) {
            log.error("Ошибка валидации пользователя: некорректный login {}", user.getLogin());
            throw new ValidationException("Логин не может быть пустым и содержать пробелы");
        }
        if (user.getBirthday() == null || user.getBirthday().isAfter(LocalDate.now())) {
            log.error("Ошибка валидации пользователя: некорректная дата рождения {}", user.getBirthday());
            throw new ValidationException("Дата рождения не может быть в будущем");
        }
    }
}
