"use client";

import { useMemo, useState } from "react";
import {
    Modal,
    Stepper,
    Select,
    Button,
    Group,
    Text,
    Alert,
    Loader,
    Stack,
    List,
} from "@mantine/core";
import { IconUpload, IconTrash, IconArrowRight, IconArrowLeft } from "@tabler/icons-react";
import { CldUploadWidget } from "next-cloudinary";
import { useProofStepper } from "../../hooks/useProofStepper";
import axiosInstance from "@/shared/api/axios/axios";
import { useTranslations } from "next-intl";

type Props = {
    opened: boolean;
    onClose: () => void;
    mediaId: string;
    mediaName: string;
};

type UploadedProof = {
    url: string;
    name: string;
};

export default function SubmitProofStepperModal({
                                                    opened,
                                                    onClose,
                                                    mediaId,
                                                    mediaName,
                                                }: Props) {
    const [active, setActive] = useState(0);

    const { campaigns, selectedCampaignId, setSelectedCampaignId, loadingCampaigns } =
        useProofStepper(mediaId);

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [uploaded, setUploaded] = useState<UploadedProof[]>([]);
    const t = useTranslations("proofOfDisplay");


    // Reset once the modal transitions to closed. Adjusting state during
    // render (rather than in an effect) for a prop change is the pattern
    // React recommends — see
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (!opened) {
            setActive(0);
            setSelectedCampaignId(null);
            setUploaded([]);
            setError(null);
            setSubmitting(false);
        }
    }

    const widgetOptions = {
        sources: ["local", "url"] as ("local" | "url")[],
        resourceType: "auto",
        multiple: true,
        maxFiles: 10,
        maxFileSize: 50000000,
    };

    const handleUploadSuccess = (results: unknown) => {
        // next-cloudinary gives an object for each uploaded asset
        if (!results || typeof results !== "object") return;

        const resultsObj = results as { info?: unknown };
        const info = resultsObj.info;
        if (!info || typeof info !== "object") return;

        const infoObj = info as { secure_url?: unknown; original_filename?: unknown; format?: unknown };
        const secureUrl = infoObj.secure_url;

        if (typeof secureUrl !== "string" || !secureUrl.startsWith("https://res.cloudinary.com/")) return;

        // Prefer original_filename if present, otherwise fallback
        const originalFilename =
            typeof infoObj.original_filename === "string"
                ? `${infoObj.original_filename}.${typeof infoObj.format === "string" ? infoObj.format : "jpg"}`
                : secureUrl.split("/").pop() ?? "upload";

        setUploaded((prev) => {
            if (prev.some((p) => p.url === secureUrl)) return prev;
            return [...prev, { url: secureUrl, name: originalFilename }];
        });
    };

    const canGoNext = useMemo(() => {
        if (active === 0) return !!selectedCampaignId;
        return true;
    }, [active, selectedCampaignId]);

    const canSubmit = useMemo(() => {
        return !!selectedCampaignId && uploaded.length > 0 && !submitting;
    }, [selectedCampaignId, uploaded.length, submitting]);

    const nextStep = () => setActive((c) => (c < 1 ? c + 1 : c));
    const prevStep = () => setActive((c) => (c > 0 ? c - 1 : c));

    async function handleSubmit() {
        if (!selectedCampaignId) return;
        if (uploaded.length === 0) return;

        setSubmitting(true);
        setError(null);

        try {
            await axiosInstance.post("/proof-of-display/email", {
                mediaId,
                campaignId: selectedCampaignId,
                proofImageUrls: uploaded.map((u) => u.url),
            });

            setActive(2);
        } catch (e) {
            const msg = e instanceof Error ? e.message : String(e);
            setError(msg);
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <Modal opened={opened} onClose={onClose} centered size="lg" title={t("modal.title", { mediaName })} radius="lg" overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}>
            <Stepper active={active}>
                <Stepper.Step
                    label={t("stepper.campaign.label")}
                    description={t("stepper.campaign.description")}>
                    {loadingCampaigns ? (
                        <Loader />
                    ) : (
                        <Stack gap="sm">
                            <Select
                                label={t("campaignSelect.label")}
                                placeholder={t("campaignSelect.placeholder")}
                                data={campaigns}
                                value={selectedCampaignId}
                                onChange={setSelectedCampaignId}
                            />

                            <Group justify="flex-end">
                                <Button
                                    variant="gradient"
                                    rightSection={<IconArrowRight size={16} />}
                                    onClick={nextStep}
                                    disabled={!canGoNext}
                                >
                                    {t("buttons.next")}
                                </Button>
                            </Group>
                        </Stack>
                    )}
                </Stepper.Step>

                <Stepper.Step
                    label={t("stepper.upload.label")}
                    description={t("stepper.upload.description")}>
                    <Stack gap="sm">
                        <Text size="sm" c="dimmed">
                            {t("upload.helperText")}
                        </Text>

                        <CldUploadWidget
                            signatureEndpoint="/api/cloudinary/sign-upload"
                            onSuccess={handleUploadSuccess}
                            options={widgetOptions}
                        >
                            {({ open }) => (
                                <Button
                                    leftSection={<IconUpload size={16} />}
                                    onClick={() => open()}
                                    disabled={!selectedCampaignId || submitting}
                                    variant="light"
                                >
                                    {t("buttons.uploadProofImages")}
                                </Button>
                            )}
                        </CldUploadWidget>

                        {uploaded.length > 0 && (
                            <Stack gap={6}>
                                <Group justify="space-between">
                                    <Text size="sm" fw={600}>
                                        {t("upload.uploadedCount", { count: uploaded.length })}
                                    </Text>

                                    <Button
                                        size="xs"
                                        variant="subtle"
                                        color="red"
                                        leftSection={<IconTrash size={14} />}
                                        onClick={() => setUploaded([])}
                                        disabled={submitting}
                                    >{t("buttons.clear")}
                                    </Button>
                                </Group>

                                <List size="sm" spacing={4}>
                                    {uploaded.map((u) => (
                                        <List.Item key={u.url}>
                                            <Text size="sm" lineClamp={1}>
                                                {u.name}
                                            </Text>
                                        </List.Item>
                                    ))}
                                </List>
                            </Stack>
                        )}

                        {error && <Alert color="red">{error}</Alert>}

                        <Group justify="space-between" mt="sm">
                            <Button
                                variant="default"
                                leftSection={<IconArrowLeft size={16} />}
                                onClick={prevStep}
                                disabled={submitting}
                            >
                                {t("buttons.back")}
                            </Button>

                            <Button variant="gradient" onClick={handleSubmit} loading={submitting} disabled={!canSubmit}>
                                {t("buttons.submitProof")}
                            </Button>
                        </Group>
                    </Stack>
                </Stepper.Step>

                <Stepper.Completed>
                    <Stack gap="xs">
                        <Text fw={600}>{t("completed.title")}</Text>
                        <Text size="sm" c="dimmed">
                            {t("completed.description")}
                        </Text>
                        <Text size="sm">{t("completed.uploadedFiles", { count: uploaded.length })}</Text>

                        <Group justify="flex-end" mt="sm">
                            <Button variant="gradient" onClick={onClose}>{t("buttons.close")}</Button>
                        </Group>
                    </Stack>
                </Stepper.Completed>
            </Stepper>
        </Modal>
    );
}
