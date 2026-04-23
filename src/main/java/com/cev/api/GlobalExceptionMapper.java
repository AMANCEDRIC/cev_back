package com.cev.api;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestion centralisée des exceptions — retourne toujours du JSON structuré.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {

        // 404 - Ressource introuvable
        if (e instanceof NotFoundException) {
            return errorResponse(404, "NOT_FOUND", e.getMessage());
        }

        // 400 - Validation des champs
        if (e instanceof ConstraintViolationException cve) {
            String details = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + " : " + v.getMessage())
                    .collect(Collectors.joining(", "));
            return errorResponse(400, "VALIDATION_ERROR", details);
        }

        // 409 - Conflit (duplicate)
        if (e instanceof IllegalStateException) {
            return errorResponse(409, "CONFLICT", e.getMessage());
        }

        // 500 - Erreur interne
        LOG.errorf(e, "Erreur interne non gérée : %s", e.getMessage());
        return errorResponse(500, "INTERNAL_ERROR", "Une erreur interne est survenue");
    }

    private Response errorResponse(int status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "status",    status,
                        "code",      code,
                        "message",   message != null ? message : "Erreur inconnue",
                        "timestamp", LocalDateTime.now().toString()
                ))
                .build();
    }
}
