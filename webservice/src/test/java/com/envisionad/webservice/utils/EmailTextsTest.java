package com.envisionad.webservice.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTextsTest {

    private final EmailTexts texts = new EmailTexts();

    // ---------- language choice ----------

    @Test
    void localeFor_mapsTheFrontendsLanguageCodes_andDefaultsToFrench() {
        assertEquals(Locale.ENGLISH, EmailTexts.localeFor("en"));
        assertEquals(Locale.FRENCH, EmailTexts.localeFor("fr"));
        assertEquals(Locale.FRENCH, EmailTexts.localeFor(null), "never picked a language");
        assertEquals(Locale.FRENCH, EmailTexts.localeFor("de"), "a code the app does not offer");
    }

    @Test
    void anUnsupportedLocale_fallsBackToTheEnglishBaseBundle_notTheServersLocale() {
        assertEquals("Your Envision Ad account is ready", texts.get("account.ready.subject", Locale.GERMAN));
    }

    @Test
    void money_isWrittenTheWayEachLanguageWritesIt() {
        assertEquals("$8.00", texts.money(new BigDecimal("8.00"), Locale.ENGLISH));
        assertEquals("1234,50 $", texts.money(new BigDecimal("1234.50"), Locale.FRENCH));
    }

    // ---------- both languages stay in step ----------

    @Test
    void englishAndFrench_haveTheSameKeys_andEachKeyTakesTheSameArguments() throws IOException {
        Properties english = load("i18n/emails.properties");
        Properties french = load("i18n/emails_fr.properties");

        assertEquals(english.stringPropertyNames(), french.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            assertEquals(placeholders(english.getProperty(key)), placeholders(french.getProperty(key)),
                    "arguments of " + key);
        }
    }

    @Test
    void apostrophes_surviveMessageFormat_inBothLanguages() {
        assertTrue(texts.get("account.resend.body", Locale.ENGLISH, "Acme", "T", "L").contains("Here's a fresh link"));
        assertTrue(texts.get("creatives.subscription.body", Locale.FRENCH, "Acme", "B", "", "1 $", "C", "")
                .contains("Acme vient de s'abonner"));
        assertTrue(texts.get("account.resend.body", Locale.FRENCH, "Acme", "T", "L").endsWith("— L'équipe Envision Ad"));
    }

    // ---------- list lines: French puts a space before the colon, English doesn't ----------

    @Test
    void listLines_followEachLanguagesColonSpacing() {
        assertEquals("Spot (IMAGE): https://x/s.jpg",
                texts.get("creatives.item", Locale.ENGLISH, "Spot", "IMAGE", "https://x/s.jpg"));
        assertEquals("Spot (IMAGE) : https://x/s.jpg",
                texts.get("creatives.item", Locale.FRENCH, "Spot", "IMAGE", "https://x/s.jpg"));
        assertEquals("Spot: https://x/s.jpg",
                texts.get("creatives.subscription.item", Locale.ENGLISH, "Spot", "https://x/s.jpg"));
        assertEquals("Spot : https://x/s.jpg",
                texts.get("creatives.subscription.item", Locale.FRENCH, "Spot", "https://x/s.jpg"));
        assertEquals("Acme, Brossard Bundle: link set (https://c/v)",
                texts.get("reminder.owner.line", Locale.ENGLISH, "Acme", "Brossard Bundle", "link set (https://c/v)"));
        assertEquals("Acme, Forfait Brossard : lien ajouté (https://c/v)",
                texts.get("reminder.owner.line", Locale.FRENCH, "Acme", "Forfait Brossard", "lien ajouté (https://c/v)"));
        assertEquals("Forfait Brossard (Visual Impact) : https://c/v",
                texts.get("reminder.advertiser.line", Locale.FRENCH, "Forfait Brossard", "Visual Impact", "https://c/v"));
        assertEquals("Brossard Bundle (Visual Impact): https://c/v",
                texts.get("reminder.advertiser.line", Locale.ENGLISH, "Brossard Bundle", "Visual Impact", "https://c/v"));
    }

    @Test
    void listLines_keepApostrophesInNames() {
        assertEquals("Anvar's All Star Cuisine, Forfait Brossard : lien ajouté (https://c/v)",
                texts.get("reminder.owner.line", Locale.FRENCH,
                        "Anvar's All Star Cuisine", "Forfait Brossard", "lien ajouté (https://c/v)"));
    }

    // ---------- English is word for word what the services used to build inline ----------
    // Each expected string below is the pre-i18n concatenation, copied from the service it lived in.

    @Test
    void english_creativesSwap_matchesThePreviousText() {
        String sections = "- Spot (IMAGE): https://x/spot.jpg\n\nFor your Gym screens:\n- Shake (IMAGE): https://x/s.jpg\n\n";
        assertEquals("New creatives for your Envision Ad screens — Acme Co",
                texts.get("creatives.subject", Locale.ENGLISH, "Acme Co"));
        assertEquals("Hi there,\n\n"
                        + "Acme Co has updated the campaign displayed on your screens to \"Autumn\".\n"
                        + "Please update your displays with the following creatives:\n\n"
                        + sections
                        + "Screens affected: Lobby, Hall"
                        + "\n\nThanks,\n— The Envision Ad Team",
                texts.get("creatives.swap.body", Locale.ENGLISH, "Acme Co", "Autumn", sections, "Lobby, Hall"));
        assertEquals("For your Gym screens:", texts.get("creatives.section.venue", Locale.ENGLISH, "Gym"));
    }

    @Test
    void english_creativesUpdate_matchesThePreviousText() {
        assertEquals("Hi there,\n\n"
                        + "Acme Co has changed the creatives for \"Autumn\", the campaign already running on your screens.\n"
                        + "Please replace the previous set with the following:\n\n"
                        + "- Spot (IMAGE): https://x/spot.jpg\n\n"
                        + "Screens affected: Lobby"
                        + "\n\nThanks,\n— The Envision Ad Team",
                texts.get("creatives.update.body", Locale.ENGLISH,
                        "Acme Co", "Autumn", "- Spot (IMAGE): https://x/spot.jpg\n\n", "Lobby"));
    }

    @Test
    void english_newSubscription_matchesThePreviousText() {
        String screens = "- Downtown (Complexe, Montreal)\n- Metro\n";
        String creatives = "- Spot: https://x/spot.jpg\n";
        assertEquals("Hi there,\n\n"
                        + "Acme Co just subscribed to the \"Full Network\" bundle, which includes the following of your screens:\n\n"
                        + screens
                        + "\nYou'll earn $8.00/month from this subscription.\n"
                        + "\nCampaign: Summer\n\n"
                        + "Please update your display(s) with the following creatives:\n\n"
                        + creatives
                        + "\nThanks for partnering with Envision Ad!\n"
                        + "— The Envision Ad Team",
                texts.get("creatives.subscription.body", Locale.ENGLISH, "Acme Co", "Full Network", screens,
                        texts.money(new BigDecimal("8.00"), Locale.ENGLISH), "Summer", creatives));
    }

    @Test
    void english_invitation_matchesThePreviousText() {
        String link = "https://app/invite?businessId=b&token=t";
        assertEquals("Invitation to join Acme on Envision Ad", texts.get("invitation.subject", Locale.ENGLISH, "Acme"));
        assertEquals("Hi there,\n\n"
                        + "You've been invited to join " + "Acme" + " on Envision Ad, "
                        + "a platform connecting digital signage screens with advertisers.\n\n"
                        + "Click the link below to accept:\n"
                        + link + "\n\n"
                        + "This invitation link expires in 1 hour.\n\n"
                        + "— The Envision Ad Team",
                texts.get("invitation.body", Locale.ENGLISH, "Acme", link));
    }

    @Test
    void english_accountEmails_matchThePreviousText() {
        String ticketUrl = "https://auth0/ticket";
        String login = "https://app" + "/auth/login";
        assertEquals("Your Envision Ad account is ready", texts.get("account.ready.subject", Locale.ENGLISH));
        assertEquals("Hi " + "Jane" + ",\n\n"
                        + "You've joined " + "Acme" + " on Envision Ad.\n\n"
                        + "Set your password to get started:\n" + ticketUrl + "\n\n"
                        + "Once set, log in here:\n" + login + "\n\n"
                        + "— The Envision Ad Team",
                texts.get("account.joined.body", Locale.ENGLISH, "Jane", "Acme", ticketUrl, login));
        assertEquals("Hi " + "there" + ",\n\n"
                        + "An Envision Ad account has been created for " + "Acme" + ".\n\n"
                        + "Set your password to get started:\n" + ticketUrl + "\n\n"
                        + "Once set, log in here:\n" + login + "\n\n"
                        + "— The Envision Ad Team",
                texts.get("account.created.body", Locale.ENGLISH,
                        texts.get("greeting.anonymous", Locale.ENGLISH), "Acme", ticketUrl, login));
        assertEquals("Hi Jane,", texts.get("greeting.named", Locale.ENGLISH, "Jane"));
        assertEquals("Hi there,\n\n"
                        + "Here's a fresh link to set your password for " + "Acme" + " on Envision Ad:\n"
                        + ticketUrl + "\n\n"
                        + "Once set, log in here:\n" + login + "\n\n"
                        + "— The Envision Ad Team",
                texts.get("account.resend.body", Locale.ENGLISH, "Acme", ticketUrl, login));
    }

    // ---------- helpers ----------

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = EmailTextsTest.class.getClassLoader().getResourceAsStream(resource)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
