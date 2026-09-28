package com.shadow.backend.common.util;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.stereotype.Component;

@Component
public class PasswordUtil {

    private static final int ITERATIONS = 2;
    private static final int MEMORY = 65536;
    private static final int PARALLELISM = 1;

    private final Argon2 argon2 = Argon2Factory.create();

    public String hash(String rawPassword) {
        return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toCharArray());
    }

    public boolean verify(String rawPassword, String encodedPassword) {
        return argon2.verify(encodedPassword, rawPassword.toCharArray());
    }
}
