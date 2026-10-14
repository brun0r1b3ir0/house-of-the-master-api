package com.houseofthemaster.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.validar(header.substring(7));

                UsernamePasswordAuthenticationToken autenticacao =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of());

                SecurityContextHolder.getContext().setAuthentication(autenticacao);

            } catch (JwtException | IllegalArgumentException e) {
                // Token inválido ou vencido: segue sem autenticar.
                // Se a rota exigir login, o Spring responde 401.
            }
        }

        filterChain.doFilter(request, response);
    }
}