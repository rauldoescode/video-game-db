package com.rauldoescode.video_game_db.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} bodies. A ProblemDetail is the standard
 * error JSON: type, title, status, detail, and instance.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * {@code @Validated} on a controller runs Bean Validation as an interceptor and throws this
     * before the MVC handler sees the call. The property path ends in the parameter name ({@code search.q}).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(leafName(violation), violation.getMessage()))
                .toList());
        return problem;
    }

    /**
     * Query-parameter validation raised by Spring MVC itself, when the controller is not
     * {@code @Validated}. Same {@code errors} array as {@link #handleConstraintViolation}.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setInstance(URI.create(servletRequest.getRequestURI()));
        problem.setProperty("errors", fieldErrors(ex));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * IGDB has no game for this id.
     */
    @ExceptionHandler(GameNotFoundException.class)
    public ProblemDetail handleGameNotFoundException(GameNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /**
     * A search the controller accepted but {@code IgdbSearchParams} rejected, such as
     * {@code yearFrom} after {@code yearTo}.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", List.of(new FieldViolation("yearFrom", ex.getMessage())));
        return problem;
    }

    /**
     * One rejected parameter. {@code field} is the query name; {@code message} is the constraint text.
     */
    public record FieldViolation(String field, String message) {}

    private static List<FieldViolation> fieldErrors(HandlerMethodValidationException ex) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String field = fieldName(result.getMethodParameter().getParameterAnnotation(RequestParam.class),
                    result.getMethodParameter().getParameterName());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
                errors.add(new FieldViolation(field, message));
            }
        });
        return errors;
    }

    /**
     * The last node of a Bean Validation path is the parameter ({@code q} in {@code search.q}).
     */
    private static String leafName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name != null ? name : "unknown";
    }

    private static String fieldName(RequestParam requestParam, String parameterName) {
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        return parameterName != null ? parameterName : "unknown";
    }
}
