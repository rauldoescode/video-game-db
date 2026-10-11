package com.rauldoescode.video_game_db.dto.request;

import com.rauldoescode.video_game_db.library.GameStatus;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/**
 * The full new state of a library entry. PUT replaces the entry: an omitted optional field is
 * cleared, and an omitted {@code hoursPlayed} becomes 0. Blank text fields are stored as null.
 *
 * @param status         required
 * @param rating         1–10, or null for unrated
 * @param review         free text, or null
 * @param hoursPlayed    0 or more, or null for 0
 * @param platformPlayed up to 50 characters, the column width
 * @param startedAt      day the user started, or null
 * @param completedAt    day the user finished, not before {@code startedAt}, or null
 */
@UpdateLibraryEntryRequest.CompletedNotBeforeStarted
public record UpdateLibraryEntryRequest(
        @NotNull GameStatus status,
        @Min(1) @Max(10) Integer rating,
        @Size(max = 10_000) String review,
        @PositiveOrZero Integer hoursPlayed,
        @Size(max = 50) String platformPlayed,
        LocalDate startedAt,
        LocalDate completedAt
) {

    public UpdateLibraryEntryRequest {
        review = blankToNull(review);
        platformPlayed = blankToNull(platformPlayed);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Rejects a {@code completedAt} earlier than {@code startedAt}. The violation is reported on
     * {@code completedAt}, so it lands in the same {@code errors} array as a field constraint.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = CompletedNotBeforeStartedValidator.class)
    public @interface CompletedNotBeforeStarted {
        String message() default "must not be before startedAt";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class CompletedNotBeforeStartedValidator
            implements ConstraintValidator<CompletedNotBeforeStarted, UpdateLibraryEntryRequest> {

        @Override
        public boolean isValid(UpdateLibraryEntryRequest request, ConstraintValidatorContext context) {
            if (request == null || request.startedAt() == null || request.completedAt() == null
                    || !request.completedAt().isBefore(request.startedAt())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("completedAt")
                    .addConstraintViolation();
            return false;
        }
    }
}
