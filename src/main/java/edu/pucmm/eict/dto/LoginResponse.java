package edu.pucmm.eict.dto;

import edu.pucmm.eict.models.Usuario;

public class LoginResponse {
    private final String token;
    private final Usuario usuario;

    public LoginResponse(String token, Usuario usuario) {
        this.token = token;
        this.usuario = usuario;
    }

    public String getToken() {
        return token;
    }

    public Usuario getUsuario() {
        return usuario;
    }
}

