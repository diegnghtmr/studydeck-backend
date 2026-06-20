/**
 * Inbound REST adapter — translates HTTP to application port calls.
 *
 * <p>Contains @RestController classes, request/response DTOs (records), and mappers. Depends on
 * input port interfaces only — never on concrete application service classes.
 *
 * <p>Spring annotations are allowed here. jakarta.validation is allowed here.
 */
package com.studydeck.infrastructure.adapter.in.web;
