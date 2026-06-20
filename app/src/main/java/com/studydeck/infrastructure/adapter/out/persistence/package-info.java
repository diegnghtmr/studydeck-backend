/**
 * Outbound persistence adapter — translates domain calls to JPA/DB operations.
 *
 * <p>Contains @Entity classes (JPA entities), Spring Data repositories, persistence adapters
 * implementing output port interfaces, and entity/domain mappers.
 *
 * <p>@Transactional belongs here, never in the domain. JPA annotations belong here, not in domain
 * model.
 */
package com.studydeck.infrastructure.adapter.out.persistence;
