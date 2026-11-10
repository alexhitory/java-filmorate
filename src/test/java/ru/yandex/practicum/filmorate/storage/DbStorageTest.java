package ru.yandex.practicum.filmorate.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.Mpa;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.film.FilmDbStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreDbStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaDbStorage;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@JdbcTest
@AutoConfigureTestDatabase
@Import({UserDbStorage.class, FilmDbStorage.class, GenreDbStorage.class, MpaDbStorage.class})
class DbStorageTest {
    @Autowired
    private UserDbStorage users;
    @Autowired
    private FilmDbStorage films;
    @Autowired
    private GenreDbStorage genres;
    @Autowired
    private MpaDbStorage mpa;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void userCrudAndMissingIds() {
        assertThat(users.findAll()).isEmpty();
        User user = users.create(user("first"));
        assertThat(user.getId()).isPositive();
        assertThat(users.findById(user.getId())).contains(user);
        assertThat(users.findById(-1)).isEmpty();
        user.setName("Changed");
        user.setEmail("changed@example.com");
        user.setLogin("changed");
        user.setBirthday(LocalDate.of(1991, 2, 3));
        assertThat(users.update(user)).isEqualTo(user);
        assertThat(users.findAll()).containsExactly(user);
        users.delete(user.getId());
        users.delete(user.getId());
        assertThat(users.findAll()).isEmpty();
        assertThatThrownBy(() -> users.update(user)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void friendshipsAreDirectedIdempotentAndCascadeOnDelete() {
        User first = users.create(user("first"));
        User second = users.create(user("second"));
        first.getFriends().add(second.getId());
        users.update(first);
        users.update(first);
        assertThat(users.findById(first.getId()).orElseThrow().getFriends()).containsExactly(second.getId());
        assertThat(users.findById(second.getId()).orElseThrow().getFriends()).isEmpty();
        second.getFriends().add(first.getId());
        users.update(second);
        first.getFriends().clear();
        users.update(first);
        assertThat(users.findById(second.getId()).orElseThrow().getFriends()).containsExactly(first.getId());
        users.delete(first.getId());
        assertThat(users.findById(second.getId()).orElseThrow().getFriends()).isEmpty();
    }

    @Test
    void friendsAndCommonFriendsUseSingleQueriesAndReturnCompleteUsers() {
        User first = users.create(user("first"));
        User second = users.create(user("second"));
        User common = users.create(user("common"));
        User anotherCommon = users.create(user("anotherCommon"));
        User onlyFirst = users.create(user("onlyFirst"));
        first.getFriends().addAll(Set.of(onlyFirst.getId(), anotherCommon.getId(), common.getId()));
        second.getFriends().addAll(Set.of(anotherCommon.getId(), common.getId()));
        common.getFriends().addAll(Set.of(first.getId(), second.getId()));
        users.update(first);
        users.update(second);
        users.update(common);

        JdbcTemplate observedJdbc = spy(new JdbcTemplate(jdbc.getDataSource()));
        UserDbStorage observedStorage = new UserDbStorage(observedJdbc);
        List<User> friends = observedStorage.findFriends(first.getId());
        assertThat(friends).extracting(User::getId)
                .containsExactly(common.getId(), anotherCommon.getId(), onlyFirst.getId());
        assertThat(friends.get(0)).isEqualTo(common);
        verify(observedJdbc, times(1)).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        clearInvocations(observedJdbc);
        assertThat(observedStorage.findCommonFriends(first.getId(), second.getId()))
                .containsExactly(common, anotherCommon);
        verify(observedJdbc, times(1)).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        assertThat(users.findFriends(-1)).isEmpty();
        assertThat(users.findFriends(onlyFirst.getId())).isEmpty();
        assertThat(users.findCommonFriends(first.getId(), -1)).isEmpty();
        assertThat(users.findCommonFriends(first.getId(), onlyFirst.getId())).isEmpty();
        assertThat(users.findCommonFriends(first.getId(), first.getId())).isEqualTo(friends);
        assertThat(users.findAll()).hasSize(5).contains(common);
    }

    @Test
    void filmCrudHydratesReferencesAndReplacesGenres() {
        assertThat(films.findAll()).isEmpty();
        Film input = film("First");
        input.setGenres(new LinkedHashSet<>(List.of(new Genre(3, null), new Genre(1, null), new Genre(3, "ignored"))));
        Film created = films.create(input);
        assertThat(created.getMpa()).isEqualTo(new Mpa(1, "G"));
        assertThat(created.getGenres()).extracting(Genre::getId).containsExactly(1, 3);
        assertThat(films.findById(created.getId())).contains(created);
        assertThat(films.findById(-1)).isEmpty();
        created.setName("Changed");
        created.setDescription(null);
        created.setDuration(95);
        created.setReleaseDate(LocalDate.of(2002, 2, 3));
        created.setMpa(new Mpa(3, null));
        created.setGenres(Set.of(new Genre(2, null)));
        Film updated = films.update(created);
        assertThat(updated.getMpa()).isEqualTo(new Mpa(3, "PG-13"));
        assertThat(updated.getGenres()).containsExactly(genres.findById(2));
        assertThat(updated.getName()).isEqualTo("Changed");
        assertThat(updated.getDescription()).isNull();
        assertThat(updated.getDuration()).isEqualTo(95);
        assertThat(updated.getReleaseDate()).isEqualTo(created.getReleaseDate());
        assertThat(films.findAll()).containsExactly(updated);
        updated.setGenres(null);
        assertThat(films.update(updated).getGenres()).isEmpty();
        films.delete(updated.getId());
        films.delete(updated.getId());
        assertThat(films.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM film_genres", Integer.class)).isZero();
        assertThatThrownBy(() -> films.update(updated)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void likesAreUniquePopularFilmsAreSortedAndDeletesCascade() {
        User first = users.create(user("first"));
        User second = users.create(user("second"));
        Film a = films.create(film("A"));
        Film b = films.create(film("B"));
        Film c = films.create(film("C"));
        b.getLikes().add(first.getId());
        films.update(b);
        films.update(b);
        c.getLikes().addAll(Set.of(first.getId(), second.getId()));
        films.update(c);
        assertThat(films.findPopular(2)).extracting(Film::getId).containsExactly(c.getId(), b.getId());
        assertThat(films.findPopular(10)).hasSize(3);
        assertThat(films.findPopular(0)).isEmpty();
        b.getLikes().clear();
        assertThat(films.update(b).getLikes()).isEmpty();
        users.delete(first.getId());
        assertThat(films.findById(c.getId()).orElseThrow().getLikes()).containsExactly(second.getId());
        films.delete(c.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM film_likes", Integer.class)).isZero();
        assertThat(films.findPopular(10)).extracting(Film::getId).containsExactly(a.getId(), b.getId());
    }

    @Test
    void invalidReferencesAreRejectedBeforeChanges() {
        Film input = film("Invalid");
        input.setMpa(new Mpa(999, null));
        assertThatThrownBy(() -> films.create(input)).isInstanceOf(NotFoundException.class);
        input.setMpa(null);
        assertThatThrownBy(() -> films.create(input)).isInstanceOf(ValidationException.class);
        input.setMpa(new Mpa(1, null));
        input.setGenres(Set.of(new Genre(999, null)));
        assertThatThrownBy(() -> films.create(input)).isInstanceOf(NotFoundException.class);
        assertThat(films.findAll()).isEmpty();
        Film saved = films.create(film("Original"));
        saved.setName("Invalid update");
        saved.setGenres(Set.of(new Genre(999, null)));
        assertThatThrownBy(() -> films.update(saved)).isInstanceOf(NotFoundException.class);
        assertThat(films.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedWritesRollBackEntityAndRelationChanges() {
        Film invalid = film("Invalid likes");
        invalid.getLikes().add(-1L);
        int before = films.findAll().size();
        assertThatThrownBy(() -> films.create(invalid)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(films.findAll()).hasSize(before);

        Film saved = films.create(film("Original"));
        try {
            saved.setName("Must roll back");
            saved.getLikes().add(-1L);
            saved.setGenres(Set.of(new Genre(2, null)));
            assertThatThrownBy(() -> films.update(saved)).isInstanceOf(DataIntegrityViolationException.class);
            Film restored = films.findById(saved.getId()).orElseThrow();
            assertThat(restored.getName()).isEqualTo("Original");
            assertThat(restored.getGenres()).isEmpty();
            assertThat(restored.getLikes()).isEmpty();
        } finally {
            films.delete(saved.getId());
        }

        User invalidUser = user("Invalid friend");
        invalidUser.getFriends().add(-1L);
        int usersBefore = users.findAll().size();
        assertThatThrownBy(() -> users.create(invalidUser)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findAll()).hasSize(usersBefore);
    }

    @Test
    void databaseRejectsDanglingRelationsAndSelfFriendship() {
        User user = users.create(user("first"));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO friendships VALUES (?, ?)", user.getId(), user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO film_likes VALUES (?, ?)", -1, user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void referenceDataAndInitializationAreRepeatable() {
        User user = users.create(user("retained"));
        try {
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"))
                    .execute(jdbc.getDataSource());
            assertThat(users.findById(user.getId())).contains(user);
        } finally {
            users.delete(user.getId());
        }
        assertThat(mpa.findAll()).extracting(Mpa::getId).containsExactly(1, 2, 3, 4, 5);
        assertThat(mpa.findAll()).extracting(Mpa::getName).containsExactly("G", "PG", "PG-13", "R", "NC-17");
        assertThat(genres.findAll()).extracting(Genre::getId).containsExactly(1, 2, 3, 4, 5, 6);
        for (Mpa rating : mpa.findAll()) {
            assertThat(mpa.findById(rating.getId())).isEqualTo(rating);
        }
        for (Genre genre : genres.findAll()) {
            assertThat(genres.findById(genre.getId())).isEqualTo(genre);
        }
        assertThatThrownBy(() -> mpa.findById(999)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> genres.findById(999)).isInstanceOf(NotFoundException.class);
    }

    private User user(String login) {
        User user = new User();
        user.setEmail(login + "@example.com");
        user.setLogin(login);
        user.setName(login);
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return user;
    }

    private Film film(String name) {
        Film film = new Film();
        film.setName(name);
        film.setDescription("Description");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);
        film.setMpa(new Mpa(1, null));
        return film;
    }
}
