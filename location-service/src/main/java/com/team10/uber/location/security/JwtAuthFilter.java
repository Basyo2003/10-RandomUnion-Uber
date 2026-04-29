package com.team10.uber.location.security;

import com.team10.uber.location.security.chain.AuthContext;
import com.team10.uber.location.security.chain.AuthResult;
import com.team10.uber.location.security.chain.RoleAuthorizationHandler;
import com.team10.uber.location.security.chain.SignatureValidationHandler;
import com.team10.uber.location.security.chain.TokenExtractionHandler;
import com.team10.uber.location.security.chain.UserLoaderHandler;
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


//Updated jwt auth filter from 3.4

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenExtractionHandler tokenExtractionHandler;

    public JwtAuthFilter(
            TokenExtractionHandler tokenExtractionHandler,
            SignatureValidationHandler signatureValidationHandler,
            UserLoaderHandler userLoaderHandler,
            RoleAuthorizationHandler roleAuthorizationHandler
    ) {
        this.tokenExtractionHandler = tokenExtractionHandler;
        tokenExtractionHandler.setNext(signatureValidationHandler)
                              .setNext(userLoaderHandler)
                              .setNext(roleAuthorizationHandler);
    }

    // 3.4 Chain of Responsibility - Spring Security calls this filter, then this filter calls the custom AuthHandler chain
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();

        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthContext ctx = new AuthContext(request);

        // Location Service has no ADMIN-only endpoints, so requiredRole stays null.
        // If later you add an ADMIN-only endpoint, set ctx.setRequiredRole("ADMIN") based on path/method.

        AuthResult result = tokenExtractionHandler.handle(ctx);

        if (!result.isSuccess()) {
            response.setStatus(result.getStatus());
            response.getWriter().write(result.getMessage());
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        ctx.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + ctx.getRole()))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute("userEmail", ctx.getEmail());
        request.setAttribute("userId", ctx.getUserId());
        request.setAttribute("userRole", ctx.getRole());

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return path.equals("/api/health")
                || path.equals("/actuator/health")
                || path.startsWith("/actuator");
    }
}