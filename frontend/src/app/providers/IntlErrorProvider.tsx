"use client";

import { NextIntlClientProvider, type AbstractIntlMessages } from "next-intl";
import type { ReactNode } from "react";

interface IntlErrorProviderProps {
    locale: string;
    messages: AbstractIntlMessages;
    children: ReactNode;
}

// next-intl strips IntlError messages in the production build, so a bare
// `Error: INVALID_MESSAGE` in prod logs gives no indication of which key
// caused it. getMessageFallback still receives the key, so log it here.
export function IntlErrorProvider({ locale, messages, children }: IntlErrorProviderProps) {
    return (
        <NextIntlClientProvider
            locale={locale}
            messages={messages}
            onError={(error) => console.error(error)}
            getMessageFallback={({ error, key, namespace }) => {
                const fullKey = [namespace, key].filter(Boolean).join(".");
                if ((error.code as string) === "INVALID_MESSAGE") {
                    console.error(`[next-intl] INVALID_MESSAGE (client) at "${fullKey}"`);
                }
                return fullKey;
            }}
        >
            {children}
        </NextIntlClientProvider>
    );
}
