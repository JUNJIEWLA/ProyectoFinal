package edu.pucmm.eict;

import edu.pucmm.eict.auth.JwtService;
import edu.pucmm.eict.models.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void shouldGenerateValidToken() {
        JwtService jwtService = new JwtService();
        Usuario user = new Usuario("1", "Admin", "admin@encuestas.local", "ADMIN");

        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isValid(token));
    }
}

