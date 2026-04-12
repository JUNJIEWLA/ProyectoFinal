package edu.pucmm.eict.services;

import edu.pucmm.eict.models.Usuario;

import java.util.Map;

public class UserService {
    private final Map<String, String> passwordsByEmail = Map.of(
            "admin@encuestas.local", "admin123",
            "digitador@encuestas.local", "digitador123"
    );

    private final Map<String, Usuario> usersByEmail = Map.of(
            "admin@encuestas.local", new Usuario("1", "Administrador", "admin@encuestas.local", "ADMIN"),
            "digitador@encuestas.local", new Usuario("2", "Digitador", "digitador@encuestas.local", "OPERADOR")
    );

    public Usuario authenticate(String email, String password) {
        String expectedPassword = passwordsByEmail.get(email);
        if (expectedPassword == null || !expectedPassword.equals(password)) {
            return null;
        }
        return usersByEmail.get(email);
    }

    public Usuario findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Usuario user : usersByEmail.values()) {
            if (id.equals(user.getId())) {
                return user;
            }
        }
        return null;
    }
}

