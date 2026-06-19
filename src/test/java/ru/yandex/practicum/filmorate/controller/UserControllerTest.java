package ru.yandex.practicum.filmorate.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserControllerTest {
    private UserController userController;
    private User validUser;

    @BeforeEach
    void setUp() {
        userController = new UserController();
        validUser = new User();
        validUser.setEmail("test@mail.ru");
        validUser.setLogin("testlogin");
        validUser.setName("Test User");
        validUser.setBirthday(LocalDate.of(1990, 1, 1));
    }

    @Test
    void createValidUser() {
        User created = userController.create(validUser);
        assertEquals(1, created.getId());
        assertEquals(1, userController.findAll().size());
    }

    @Test
    void createUserWithEmptyEmail() {
        validUser.setEmail("");
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void createUserWithEmailWithoutAt() {
        validUser.setEmail("testmail.ru");
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void createUserWithNullEmail() {
        validUser.setEmail(null);
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void createUserWithEmptyLogin() {
        validUser.setLogin("");
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void createUserWithLoginContainingSpaces() {
        validUser.setLogin("test login");
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void createUserWithNullLogin() {
        validUser.setLogin(null);
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void createUserWithEmptyNameUsesLogin() {
        validUser.setName("");
        User created = userController.create(validUser);
        assertEquals(validUser.getLogin(), created.getName());
    }

    @Test
    void createUserWithNullNameUsesLogin() {
        validUser.setName(null);
        User created = userController.create(validUser);
        assertEquals(validUser.getLogin(), created.getName());
    }

    @Test
    void createUserWithBirthdayToday() {
        validUser.setBirthday(LocalDate.now());
        assertDoesNotThrow(() -> userController.create(validUser));
    }

    @Test
    void createUserWithBirthdayInFuture() {
        validUser.setBirthday(LocalDate.now().plusDays(1));
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void createUserWithNullBirthday() {
        validUser.setBirthday(null);
        assertThrows(ValidationException.class, () -> userController.create(validUser));
    }

    @Test
    void updateExistingUser() {
        User created = userController.create(validUser);
        created.setEmail("new@mail.ru");
        User updated = userController.update(created);
        assertEquals("new@mail.ru", updated.getEmail());
    }

    @Test
    void updateNonExistingUser() {
        validUser.setId(999);
        assertThrows(ValidationException.class, () -> userController.update(validUser));
    }

    @Test
    void createUserWithEmptyRequest() {
        User emptyUser = new User();
        assertThrows(ValidationException.class, () -> userController.create(emptyUser));
    }
}
