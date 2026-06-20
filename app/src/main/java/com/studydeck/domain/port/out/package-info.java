/**
 * Output ports — interfaces describing what the application NEEDS from infrastructure.
 *
 * <p>These interfaces are implemented by outbound adapters (JPA repositories, HTTP clients, message
 * publishers). Pure Java — no Spring, no framework annotations.
 *
 * <p>Naming convention: *Port (e.g., SaveDeckPort, LoadDeckPort, PublishEventPort).
 */
package com.studydeck.domain.port.out;
