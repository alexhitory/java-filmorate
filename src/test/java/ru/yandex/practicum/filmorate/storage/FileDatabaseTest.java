package ru.yandex.practicum.filmorate.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FileDatabaseTest {
    @TempDir
    private Path directory;

    @Test
    void dataSurvivesClosingAndReopeningFileDatabase() {
        String url = "jdbc:h2:file:" + directory.resolve("filmorate").toString().replace('\\', '/');
        JdbcTemplate first = open(url);
        initialize(first);
        User user = new User();
        user.setEmail("persistent@example.com");
        user.setLogin("persistent");
        user.setBirthday(LocalDate.of(1990, 1, 1));
        User saved = new UserDbStorage(first).create(user);
        first.execute("SHUTDOWN");

        JdbcTemplate reopened = open(url);
        initialize(reopened);
        assertThat(new UserDbStorage(reopened).findById(saved.getId())).contains(saved);
        assertThat(reopened.queryForObject("SELECT COUNT(*) FROM mpa", Integer.class)).isEqualTo(5);
        reopened.execute("SHUTDOWN");
    }

    private JdbcTemplate open(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, "sa", "password"));
    }

    private void initialize(JdbcTemplate jdbc) {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"))
                .execute(jdbc.getDataSource());
    }
}
