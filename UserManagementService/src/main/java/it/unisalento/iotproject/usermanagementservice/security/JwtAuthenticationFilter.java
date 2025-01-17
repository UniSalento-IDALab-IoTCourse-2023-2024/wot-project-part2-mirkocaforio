package it.unisalento.iotproject.usermanagementservice.security;

import it.unisalento.iotproject.usermanagementservice.domain.User;
import it.unisalento.iotproject.usermanagementservice.exceptions.AccessDeniedException;
import it.unisalento.iotproject.usermanagementservice.exceptions.UserNotAuthorizedException;
import it.unisalento.iotproject.usermanagementservice.service.UserCheckService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Service
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtilities jwtUtilities ;

    @Autowired
    private UserCheckService customerUserDetailsService ;

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException, UserNotAuthorizedException, AccessDeniedException {

        final String authorizationHeader = request.getHeader("Authorization");

        LOGGER.info("Authorization header: " + authorizationHeader);

        String username = null;
        String jwt = null;
        String role = null;

        try {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                jwt = authorizationHeader.substring(7);
                username = jwtUtilities.extractUsername(jwt);
                role = jwtUtilities.extractRole(jwt);
            } else {
                throw new AccessDeniedException("Missing token");
            }
        } catch (Exception e) {
            throw new AccessDeniedException("Invalid token: " + e.getMessage());
        }


        if (username != null && role != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            LOGGER.info("Checking user details");
            User user = this.customerUserDetailsService.loadUserByUsername(username);

            String userEmail;
            String userRole;
            boolean userEnabled;

            // Se token valido e risposta del cqrs null, si assume che l'utente sia l'email del token
            if (user == null){
                LOGGER.info("User not found in CQRS, assuming user is the email from the token");
                userEmail = username;
                userRole = role;
                userEnabled = true;
            }else {
                userEmail = user.getEmail();
                userRole = user.getRole();
                userEnabled = user.getEnabled();
            }

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(userEmail) // Assume email is username
                    .password("") // Password field is not used in JWT authentication
                    .authorities(userRole) // Set roles or authorities from the UserDetailsDTO
                    .build();

            if (jwtUtilities.validateToken(jwt, userDetails, userRole) && userEnabled) {
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            } else {
                throw new UserNotAuthorizedException("User not authorized");
            }
        }

        if ( SecurityContextHolder.getContext().getAuthentication() == null ) {
            throw new AccessDeniedException("No authentication found");
        }

        chain.doFilter(request, response);
    }

}