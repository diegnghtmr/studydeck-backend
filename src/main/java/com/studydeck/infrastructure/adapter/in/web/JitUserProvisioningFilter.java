package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ProvisionUserUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Just-in-time (JIT) user provisioning filter.
 *
 * <p>For every authenticated request carrying a validated JWT, ensures a {@code user_account} row
 * exists for the JWT {@code sub} claim. This satisfies the FK constraint on {@code deck.owner_id →
 * user_account(id)} so that subsequent controller handlers succeed.
 *
 * <p>Placement: registered AFTER {@code BearerTokenAuthenticationFilter} in the Spring Security
 * filter chain so the {@link org.springframework.security.core.context.SecurityContext} is already
 * populated when this filter runs.
 *
 * <p>Claim resolution order:
 *
 * <ol>
 *   <li>{@code email} claim
 *   <li>{@code preferred_username} claim (fall-back)
 *   <li>JWT {@code sub} as last resort
 * </ol>
 *
 * <p>Display-name resolution order:
 *
 * <ol>
 *   <li>{@code name} claim
 *   <li>{@code preferred_username} claim
 * </ol>
 */
class JitUserProvisioningFilter extends OncePerRequestFilter {

  private final ProvisionUserUseCase provisionUser;

  JitUserProvisioningFilter(ProvisionUserUseCase provisionUser) {
    this.provisionUser = provisionUser;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Jwt jwt) {

      String sub = jwt.getSubject();
      if (sub != null) {
        try {
          OwnerId userId = new OwnerId(UUID.fromString(sub));
          String email = resolveEmail(jwt, sub);
          String displayName = resolveDisplayName(jwt);

          provisionUser.execute(new ProvisionUserUseCase.Command(userId, email, displayName));
        } catch (IllegalArgumentException ignored) {
          // Malformed sub UUID — do not provision; let the request continue
          // (the controller will likely return 400/401 on its own)
        }
      }
    }

    filterChain.doFilter(request, response);
  }

  private static String resolveEmail(Jwt jwt, String sub) {
    String email = jwt.getClaimAsString("email");
    if (email != null && !email.isBlank()) {
      return email;
    }
    String preferredUsername = jwt.getClaimAsString("preferred_username");
    if (preferredUsername != null && !preferredUsername.isBlank()) {
      return preferredUsername;
    }
    return sub; // last resort — satisfies the non-blank constraint
  }

  private static String resolveDisplayName(Jwt jwt) {
    String name = jwt.getClaimAsString("name");
    if (name != null && !name.isBlank()) {
      return name;
    }
    return jwt.getClaimAsString("preferred_username");
  }
}
