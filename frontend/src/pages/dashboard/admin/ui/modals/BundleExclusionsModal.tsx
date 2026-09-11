"use client";

import {
    Badge,
    Button,
    Group,
    Loader,
    Modal,
    ScrollArea,
    Stack,
    Switch,
    Table,
    Text,
    TextInput,
} from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import { Bundle, BundleCandidateMedia } from "@/entities/bundle";
import { addExclusion, getCandidateMedias, removeExclusion } from "@/features/bundle-management";
import { formatCurrency } from "@/shared/lib/formatCurrency";

interface BundleExclusionsModalProps {
    opened: boolean;
    onClose: () => void;
    /** Called after any toggle, so the table's screen count and price stay honest. */
    onChanged: () => void;
    bundle: Bundle | null;
}

export function BundleExclusionsModal({ opened, onClose, onChanged, bundle }: BundleExclusionsModalProps) {
    const t = useTranslations("bundleManagement.exclusions");
    const locale = useLocale();

    const [candidates, setCandidates] = useState<BundleCandidateMedia[]>([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState("");
    const [pendingMediaId, setPendingMediaId] = useState<string | null>(null);

    const bundleId = bundle?.bundleId;

    // Reset per-open state during render rather than in an effect — React's
    // documented "adjust state during render" pattern, and what
    // react-hooks/set-state-in-effect requires. The effect below only fetches.
    const openKey = opened && bundleId ? bundleId : null;
    const [prevOpenKey, setPrevOpenKey] = useState<string | null>(openKey);
    if (openKey !== prevOpenKey) {
        setPrevOpenKey(openKey);
        setSearch("");
        setCandidates([]);
        setLoading(openKey !== null);
    }

    useEffect(() => {
        if (!opened || !bundleId) return;

        let cancelled = false;

        (async () => {
            try {
                const data = await getCandidateMedias(bundleId);
                if (!cancelled) setCandidates(data);
            } catch {
                if (!cancelled) {
                    notifications.show({ title: t("loadFailed"), message: "", color: "red" });
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [opened, bundleId, t]);

    const handleToggle = async (media: BundleCandidateMedia) => {
        if (!bundleId) return;
        setPendingMediaId(media.mediaId);
        const nextExcluded = !media.excluded;
        try {
            if (nextExcluded) {
                await addExclusion(bundleId, media.mediaId);
            } else {
                await removeExclusion(bundleId, media.mediaId);
            }
            setCandidates((current) =>
                current.map((c) => (c.mediaId === media.mediaId ? { ...c, excluded: nextExcluded } : c)),
            );
            onChanged();
        } catch {
            notifications.show({ title: t("toggleFailed"), message: "", color: "red" });
        } finally {
            setPendingMediaId(null);
        }
    };

    const query = search.trim().toLowerCase();
    const visible = query
        ? candidates.filter((c) =>
              [c.title, c.mediaOwnerName, c.city, c.region]
                  .filter(Boolean)
                  .some((field) => field!.toLowerCase().includes(query)),
          )
        : candidates;

    const includedCount = candidates.filter((c) => !c.excluded).length;

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title", { name: bundle ? (locale === "fr" ? bundle.nameFr : bundle.nameEn) : "" })}
            size="xl"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                <Text size="sm" c="dimmed">
                    {t("description")}
                </Text>

                <Group justify="space-between">
                    <TextInput
                        placeholder={t("searchPlaceholder")}
                        leftSection={<IconSearch size={16} />}
                        value={search}
                        onChange={(e) => setSearch(e.currentTarget.value)}
                        style={{ flex: 1 }}
                    />
                    <Badge variant="light" size="lg">
                        {t("includedCount", { included: includedCount, total: candidates.length })}
                    </Badge>
                </Group>

                {loading ? (
                    <Group justify="center" py="xl">
                        <Loader />
                    </Group>
                ) : (
                    <ScrollArea.Autosize mah={420}>
                        <Table striped highlightOnHover verticalSpacing="sm">
                            <Table.Thead>
                                <Table.Tr>
                                    <Table.Th>{t("media")}</Table.Th>
                                    <Table.Th>{t("owner")}</Table.Th>
                                    <Table.Th>{t("location")}</Table.Th>
                                    <Table.Th>{t("price")}</Table.Th>
                                    <Table.Th>{t("included")}</Table.Th>
                                </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                                {visible.length > 0 ? (
                                    visible.map((media) => (
                                        <Table.Tr key={media.mediaId}>
                                            <Table.Td>
                                                <Text fw={500}>{media.title}</Text>
                                            </Table.Td>
                                            <Table.Td>
                                                <Text size="sm">{media.mediaOwnerName}</Text>
                                            </Table.Td>
                                            <Table.Td>
                                                <Text size="sm" c="dimmed">
                                                    {[media.city, media.region].filter(Boolean).join(" · ")}
                                                </Text>
                                            </Table.Td>
                                            <Table.Td>
                                                <Text size="sm">
                                                    {formatCurrency(media.price ?? 0, { locale })}
                                                </Text>
                                            </Table.Td>
                                            <Table.Td>
                                                <Switch
                                                    checked={!media.excluded}
                                                    onChange={() => handleToggle(media)}
                                                    disabled={pendingMediaId === media.mediaId}
                                                    aria-label={t("toggleAria", { title: media.title })}
                                                />
                                            </Table.Td>
                                        </Table.Tr>
                                    ))
                                ) : (
                                    <Table.Tr>
                                        <Table.Td colSpan={5}>
                                            <Text ta="center" c="dimmed" py="xl">
                                                {candidates.length === 0 ? t("noCandidates") : t("noMatches")}
                                            </Text>
                                        </Table.Td>
                                    </Table.Tr>
                                )}
                            </Table.Tbody>
                        </Table>
                    </ScrollArea.Autosize>
                )}

                <Group justify="flex-end">
                    <Button variant="default" onClick={onClose}>
                        {t("close")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
