package com.nexora;

import static org.junit.jupiter.api.Assertions.*;

import com.nexora.model.User;
import com.nexora.repository.UserRepository;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DatabaseConnectivityTest {
    @Autowired DataSource dataSource;
    @Autowired UserRepository users;

    @Test
    void databaseConnectionIsValid() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertTrue(c.isValid(2));
        }
    }

    @Test
    void userRepositoryRoundTrip() {
        User u = new User();
        u.email = TestSupport.uniqueEmail();
        u.displayName = "DB Test";
        u.passwordHash = "x";
        users.save(u);
        assertTrue(users.findByEmailIgnoreCase(u.email.toUpperCase()).isPresent());
        users.delete(u);
        assertFalse(users.existsByEmailIgnoreCase(u.email));
    }
}
