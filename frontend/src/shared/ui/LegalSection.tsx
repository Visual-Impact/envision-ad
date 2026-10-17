import type { ReactNode } from "react";
import { Stack, Text, Title } from "@mantine/core";

/** One numbered section of a legal page (terms, privacy), anchored by id so it can be linked to. */
export function LegalSection({ id, title, children }: { id: string; title: string; children: ReactNode }) {
    return (
        <Stack component="section" id={id} gap="sm">
            <Title order={2} size="h3">{title}</Title>
            {children}
        </Stack>
    );
}

export function LegalParagraph({ children }: { children: ReactNode }) {
    return <Text>{children}</Text>;
}
