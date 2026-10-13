package com.envisionad.webservice.utils;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The single home of every email subject and body the backend sends, in English and French.
 * Texts live in {@code i18n/emails.properties} (English, also the base bundle) and
 * {@code i18n/emails_fr.properties}; callers assemble only the variable parts (names, lists,
 * links) and pass them in as {@code {n}} arguments.
 *
 * <p>Every text goes through {@link java.text.MessageFormat}, with or without arguments, so a
 * literal apostrophe is always written {@code ''} in the properties files. Pass arguments as
 * already-formatted strings: MessageFormat would otherwise reformat numbers in the server's
 * conventions (dropping trailing zeros, adding grouping).
 *
 * <p>This is its own message source rather than Spring's application-wide {@code messageSource},
 * so it needs no Spring context to construct — unit tests use {@code new EmailTexts()} and so
 * assert against the real texts.
 */
@Component
public class EmailTexts {

    /**
     * The language for any recipient who has not picked one in the app, matching the frontend's
     * {@code defaultLocale}. Nearly every client is a Quebec business.
     */
    public static final Locale DEFAULT_LOCALE = Locale.FRENCH;

    private final ResourceBundleMessageSource messages;

    public EmailTexts() {
        messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/emails");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Without this, a key missing from emails_fr would resolve against the server's own
        // locale instead of the English base bundle.
        messages.setFallbackToSystemLocale(false);
        messages.setAlwaysUseMessageFormat(true);
    }

    /**
     * Maps a language code as the frontend stores it (Auth0 {@code user_metadata.preferred_language},
     * {@code "en"} or {@code "fr"}) to the locale to write in. Anything else, including no
     * choice at all, gets {@link #DEFAULT_LOCALE}.
     */
    public static Locale localeFor(String preferredLanguage) {
        return "en".equalsIgnoreCase(preferredLanguage) ? Locale.ENGLISH : DEFAULT_LOCALE;
    }

    /** The text for {@code key} in {@code locale}, with {@code args} substituted. */
    public String get(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    /** A dollar amount the way {@code locale} writes it: {@code $12.50} or {@code 12,50 $}. */
    public String money(BigDecimal amount, Locale locale) {
        String number = amount.toPlainString();
        if (!Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            number = number.replace('.', ',');
        }
        return get("money", locale, number);
    }
}
