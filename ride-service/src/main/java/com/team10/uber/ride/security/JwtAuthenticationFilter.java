package com.team10.uber.ride.security;

import com.team10.uber.ride.security.chain.AuthContext;
import com.team10.uber.ride.security.chain.AuthHandler;
import com.team10.uber.ride.security.chain.RoleAuthorizationHandler;
import com.team10.uber.ride.security.chain.SignatureValidationHandler;
import com.team10.uber.ride.security.chain.TokenExtractionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        AuthHandler tokenExtractor = new TokenExtractionHandler();
        AuthHandler sigValidator   = new SignatureValidationHandler();
        AuthHandler roleAuthorizer = new RoleAuthorizationHandler();

        tokenExtractor.setNext(sigValidator);
        sigValidator.setNext(roleAuthorizer);

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
