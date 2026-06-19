package ru.yandex.practicum.filmorate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.InMemoryUserStorage;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {
    private UserService userService;
    private User validUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(new InMemoryUserStorage());
        validUser = new User();
        validUser.setEmail("test@mail.ru");
        validUser.setLogin("testlogin");
        validUser.setName("Test User");
        validUser.setBirthday(LocalDate.of(1990, 1, 1));
    }

    @Test
    void createValidUser() {
        User created = userService.create(validUser);
        assertEquals(1, created.getId());
        assertEquals(1, userService.findAll().size());
    }

    @Test
    void createUserWithEmptyEmail() {
        validUser.setEmail("");
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void createUserWithEmailWithoutAt() {
        validUser.setEmail("testmail.ru");
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void createUserWithNullEmail() {
        validUser.setEmail(null);
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void createUserWithEmptyLogin() {
        validUser.setLogin("");
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void createUserWithLoginContainingSpaces() {
        validUser.setLogin("test login");
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void createUserWithNullLogin() {
        validUser.setLogin(null);
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void createUserWithEmptyNameUsesLogin() {
        validUser.setName("");
        User created = userService.create(validUser);
        assertEquals(validUser.getLogin(), created.getName());
    }

    @Test
    void createUserWithNullNameUsesLogin() {
        validUser.setName(null);
        User created = userService.create(validUser);
        assertEquals(validUser.getLogin(), created.getName());
    }

    @Test
    void createUserWithBirthdayToday() {
        validUser.setBirthday(LocalDate.now());
        assertDoesNotThrow(() -> userService.create(validUser));
    }

    @Test
    void createUserWithBirthdayInFuture() {
        validUser.setBirthday(LocalDate.now().plusDays(1));
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void createUserWithNullBirthday() {
        validUser.setBirthday(null);
        assertThrows(ValidationException.class, () -> userService.create(validUser));
    }

    @Test
    void updateExistingUser() {
        User created = userService.create(validUser);
        created.setEmail("new@mail.ru");
        User updated = userService.update(created);
        assertEquals("new@mail.ru", updated.getEmail());
    }

    @Test
    void updateNonExistingUser() {
        validUser.setId(999);
        assertThrows(NotFoundException.class, () -> userService.update(validUser));
    }

    @Test
    void createUserWithEmptyRequest() {
        User emptyUser = new User();
        assertThrows(ValidationException.class, () -> userService.create(emptyUser));
    }

    @Test
    void addFriendAddsUsersMutually() {
        User first = userService.create(validUser);
        User second = createUser("second@mail.ru", "second");

        userService.addFriend(first.getId(), second.getId());

        assertEquals(1, userService.getFriends(first.getId()).size());
        assertEquals(second.getId(), userService.getFriends(first.getId()).get(0).getId());
        assertEquals(first.getId(), userService.getFriends(second.getId()).get(0).getId());
    }

    @Test
    void removeFriendRemovesUsersMutually() {
        User first = userService.create(validUser);
        User second = createUser("second@mail.ru", "second");
        userService.addFriend(first.getId(), second.getId());

        userService.removeFriend(first.getId(), second.getId());

        assertEquals(0, userService.getFriends(first.getId()).size());
        assertEquals(0, userService.getFriends(second.getId()).size());
    }

    @Test
    void getCommonFriendsReturnsOnlyMutualFriends() {
        User first = userService.create(validUser);
        User second = createUser("second@mail.ru", "second");
        User common = createUser("common@mail.ru", "common");
        User onlyFirst = createUser("only@mail.ru", "only");
        userService.addFriend(first.getId(), common.getId());
        userService.addFriend(second.getId(), common.getId());
        userService.addFriend(first.getId(), onlyFirst.getId());

        assertEquals(1, userService.getCommonFriends(first.getId(), second.getId()).size());
        assertEquals(common.getId(), userService.getCommonFriends(first.getId(), second.getId()).get(0).getId());
    }

    @Test
    void addSelfAsFriendThrowsValidationException() {
        User user = userService.create(validUser);
        assertThrows(ValidationException.class, () -> userService.addFriend(user.getId(), user.getId()));
    }

    private User createUser(String email, String login) {
        User user = new User();
        user.setEmail(email);
        user.setLogin(login);
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return userService.create(user);
    }
}
