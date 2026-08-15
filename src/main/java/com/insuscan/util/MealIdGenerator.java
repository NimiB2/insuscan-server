package com.insuscan.util;

import com.insuscan.crud.MealRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the meal id portion that follows the system id, in the format {@code YYYYMMDD_NNN}.
 * <p>
 * The full Firestore document id is {@code systemId_YYYYMMDD_NNN} (for example
 * {@code insuscan_20260812_001}). Callers are responsible for prepending the system id,
 * consistently with how user ids and meal ids are composed elsewhere in the codebase.
 * <p>
 * The sequence counter is global per calendar day rather than per user, which keeps the
 * document id free of personal data while still guaranteeing uniqueness.
 */
@Component
public class MealIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(MealIdGenerator.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Matches the trailing sequence number of a document id, e.g. the "001" in "insuscan_20260812_001". */
    private static final Pattern TRAILING_SEQUENCE_PATTERN = Pattern.compile("_(\\d+)$");

    /** Zero-padded width of the daily sequence number. */
    private static final String SEQUENCE_FORMAT = "%03d";

    private final MealRepository mealRepository;

    @Value("${spring.application.name}")
    private String systemId;

    public MealIdGenerator(MealRepository mealRepository) {
        this.mealRepository = mealRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────

    public String generateMealId() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        int sequence = resolveNextSequence(datePart);

        String mealId = datePart + "_" + String.format(SEQUENCE_FORMAT, sequence);
        log.debug("Generated meal id: {} (document id: {}_{})", mealId, systemId, mealId);

        return mealId;
    }

    // ── Sequence resolution ───────────────────────────────────────────────

    private int resolveNextSequence(String datePart) {
        String documentIdPrefix = systemId + "_" + datePart + "_";
        List<String> existingIds = mealRepository.findIdsByPrefix(documentIdPrefix);

        int maxSequence = 0;
        for (String id : existingIds) {
            Matcher matcher = TRAILING_SEQUENCE_PATTERN.matcher(id);
            if (!matcher.find()) {
                log.debug("Meal id has no trailing sequence, skipping: {}", id);
                continue;
            }
            try {
                maxSequence = Math.max(maxSequence, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                log.debug("Meal id has an unparsable sequence, skipping: {}", id);
            }
        }

        return maxSequence + 1;
    }
}