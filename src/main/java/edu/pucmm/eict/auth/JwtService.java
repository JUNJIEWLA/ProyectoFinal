package edu.pucmm.eict.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import edu.pucmm.eict.models.Usuario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class JwtService {
    private final Algorithm algorithm;

    public JwtService() {
        String secret = System.getenv().getOrDefault("JWT_SECRET", "dev-secret-change-me");
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public String generateToken(Usuario usuario) {
        return JWT.create()
                .withSubject(usuario.getId())
                .withClaim("email", usuario.getEmail())
                .withClaim("rol", usuario.getRol())
                .withExpiresAt(Instant.now().plus(8, ChronoUnit.HOURS))
                .sign(algorithm);
    }

    public boolean isValid(String token) {
        try {
            JWT.require(algorithm).build().verify(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public DecodedJWT verify(String token) {
        return JWT.require(algorithm).build().verify(token);
    }
}

