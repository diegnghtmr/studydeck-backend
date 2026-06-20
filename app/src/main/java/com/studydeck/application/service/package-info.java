/**
 * Application services — use case implementations.
 *
 * <p>Orchestrates domain objects and output ports to fulfill a use case. Framework-free: no Spring
 * annotations. Wired by {@link com.studydeck.infrastructure.config.BeanConfiguration}.
 *
 * <p>Implements input port interfaces from {@code com.studydeck.domain.port.in} and delegates to
 * output port interfaces from {@code com.studydeck.domain.port.out}.
 */
package com.studydeck.application.service;
