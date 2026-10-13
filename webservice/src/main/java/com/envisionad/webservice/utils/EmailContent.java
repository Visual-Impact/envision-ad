package com.envisionad.webservice.utils;

/** A plain-text email's subject and body, already rendered in the recipient's language. */
public record EmailContent(String subject, String body) {}
