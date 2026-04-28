package com.team10.uber.ride.security;

import com.team10.uber.ride.security.chain.AuthContext;
import com.team10.uber.ride.security.chain.AuthHandler;
import com.team10.uber.ride.security.chain.RoleAuthorizationHandler;
import com.team10.uber.ride.security.chain.SignatureValidationHandler;
import com.team10.uber.ride.security.chain.TokenExtractionHandler;
import com.team10.uber.ride.security.chain.UserLoaderHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        AuthHandler tokenExtractor = new TokenExtractionHandler();
        AuthHandler sigValidator   = new SignatureValidationHandler();
        AuthHandler userLoader     = new UserLoaderHandler(jdbcTemplate);
        AuthHandler roleAuthorizer = new RoleAuthorizationHandler();

        tokenExtractor.setNext(sigValidator);
        sigValidator.setNext(userLoader);
        userLoader.setNext(roleAuthorizer);

        AuthContext ctx    = new AuthContext(request, response);
        AuthContext result = tokenExtractor.handle(ctx);

        if (!result.isPassed()) {
            response.setStatus(result.getErrorStatus());
            response.getWriter().write(result.getErrorMessage());
            response.getWriter().flush();
            return;
        }

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        result.getUserEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + result.getRole()))
                );
        SecurityContextHolder.getContext().setAuthentication(auth);

        request.setAttribute("userEmail", result.getUserEmail());
        request.setAttribute("userId",    result.getUserId());
        request.setAttribute("userRole",  result.getRole());

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator") || path.equals("/api/rides/health");
    }
}
