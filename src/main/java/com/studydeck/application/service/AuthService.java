package com.studydeck.application.service;

import com.studydeck.domain.port.in.GetCurrentPrincipalQuery;

/**
 * Application service implementing {@link GetCurrentPrincipalQuery}.
 *
 * <p>This is a thin pass-through: the security layer (B3) resolves the authenticated identity from
 * the JWT/session and passes it as a {@link GetCurrentPrincipalQuery.Query}. This service simply
 * packages the values into the {@link Principal} result record.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 */
public final class AuthService implements GetCurrentPrincipalQuery {

  @Override
  public Principal execute(Query query) {
    return new Principal(query.ownerId(), query.email(), query.displayName());
  }
}
