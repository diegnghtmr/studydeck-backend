/**
 * Input ports — interfaces describing what the application CAN DO.
 *
 * <p>These interfaces are implemented by application services and called by inbound adapters (REST
 * controllers, MCP adapters, CLI). Pure Java — no Spring, no framework annotations.
 *
 * <p>Naming convention: *UseCase for commands, *Query for reads.
 */
package com.studydeck.domain.port.in;
