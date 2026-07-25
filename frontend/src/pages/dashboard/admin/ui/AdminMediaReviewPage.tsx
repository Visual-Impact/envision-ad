"use client";

import { useEffect, useState } from "react";
import {
  Container,
  Group,
  Button,
  Text,
  Title,
  Stack,
  Loader,
  Center,
  Modal,
} from "@mantine/core";
import { BackButton } from "@/widgets/BackButton";
import { useParams } from "next/navigation";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { getMediaById } from "@/features/media-management/api";
import { useTranslations } from "next-intl";
import { Media } from "@/entities/media";
import { useAdminMedia } from "@/pages/dashboard/admin/hooks/useAdminMedia";
import { notifications } from "@mantine/notifications";
import { MediaStatusEnum } from "@/entities/media/model/media";
import { useMediaQuery } from "@mantine/hooks";
import { MediaDetails } from "@/widgets/MediaDetails/MediaDetails";
import { MediaLocationReviewCard } from "./MediaLocationReviewCard";

export default function AdminMediaReviewPage() {
  const t = useTranslations("mediaPage");
  const isMobile = useMediaQuery("(max-width: 768px)");
  const t1 = useTranslations("admin.adminActions");
  const t2 = useTranslations("mediaModal.buttons");
  const router = useRouter();
  const params = useParams();
  const id = params?.id as string | undefined;

  const { approveMedia, denyMedia } = useAdminMedia();

  const [media, setMedia] = useState<Media | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [confirmAction, setConfirmAction] = useState<"approve" | "deny" | null>(
      null
  );

  const confirmOpen = confirmAction !== null;

  const openConfirm = (action: "approve" | "deny") => setConfirmAction(action);
  const closeConfirm = () => {
    if (!submitting) setConfirmAction(null);
  };

  const handleConfirm = async () => {
    if (!confirmAction || !id || !media) return;

    if (media.status !== MediaStatusEnum.PENDING) {
      closeConfirm();
      return;
    }

    try {
      setSubmitting(true);

      if (confirmAction === "approve") {
        await approveMedia(id);
        setMedia((prev) =>
            prev ? { ...prev, status: MediaStatusEnum.ACTIVE } : prev
        );
      } else {
        await denyMedia(id);
        setMedia((prev) =>
            prev ? { ...prev, status: MediaStatusEnum.REJECTED } : prev
        );
      }

      closeConfirm();
      notifications.show({
        title: t("success.title"),
        message: t("success." + confirmAction),
        color: "green",
      });

      router.push("/dashboard/admin/media/pending");
    } catch (e) {
      console.error(e);
      notifications.show({
        title: t("errors.error"),
        message: t("errors." + confirmAction + "Failed"),
        color: "red",
      });
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => {
    if (!id) return;

    const loadMedia = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await getMediaById(id);
        setMedia(data);
      } catch (err: unknown) {
        const msg =
            err instanceof Error ? err.message : "Failed to load media details.";
        setError(msg);
      } finally {
        setLoading(false);
      }
    };

    void loadMedia();
  }, [id]);

  if (loading) {
    return (
        <Container size="lg" py={isMobile ? "md" : "xl"} px={isMobile ? "xs" : "md"}>
          <Center>
            <Loader />
          </Center>
        </Container>
    );
  }

  if (error || !media) {
    return (
        <Container size="lg" py={isMobile ? "md" : "xl"} px={isMobile ? "xs" : "md"}>
          <Stack align="center" gap="sm">
            <Text fw={600}>{t("errorTitle")}</Text>
            <Text size="sm" c="dimmed">
              {error ?? t("errorNotFound")}
            </Text>
            <BackButton />
          </Stack>
        </Container>
    );
  }

  const isPending = media.status === MediaStatusEnum.PENDING;

  if (!isPending) {
    return (
        <Container size="lg" py={isMobile ? "md" : "xl"} px={isMobile ? "xs" : "md"}>
          <Stack align="center" gap="sm">
            <Title order={3}>{t1("reviewUnavailable")}</Title>

            <Text c="dimmed" ta="center">
              {t1("reviewDone", { status: media.status ?? "Unknown" })}
            </Text>

            <BackButton />
          </Stack>
        </Container>
    );
  }

  return (
      <>
        <Container size="md" py={20} px={isMobile? "sm" :80}>
          <MediaDetails
            media={media}
            loading={loading}
            error={error}
            activeAdsCount={null}
            extraSection={
              <MediaLocationReviewCard mediaLocation={media.mediaLocation} />
            }
          >

              <Button
                radius="xl"
                fullWidth
                variant="gradient"
                type="button"
                aria-label="Approve"
                onMouseDown={(e) => e.preventDefault()}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  openConfirm("approve");
                }}
            >
              {t1("approve")}
            </Button>

            <Button
                radius="xl"
                fullWidth
                color="red"
                variant="outline"
                type="button"
                aria-label="Deny"
                onMouseDown={(e) => e.preventDefault()}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  openConfirm("deny");
                }}
            >
              {t1("deny")}
            </Button>
          </MediaDetails>
        </Container>

        <Modal
            key={confirmAction ?? "closed"}
            opened={confirmOpen}
            onClose={closeConfirm}
            centered
            keepMounted={false}
            closeButtonProps={{ "aria-label": t1("close") }}
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
            title={
              confirmAction === "approve"
                  ? t1("approveMediaConfirmation")
                  : t1("denyMediaConfirmation")
            }
        >
          {!confirmAction ? null : (
              <>
                <Text mb="md">
                  {confirmAction === "approve"
                      ? t1("approveMediaText")
                      : t1("denyMediaText")}
                </Text>

                <Group justify="flex-end">
                  <Button
                      variant="default"
                      type="button"
                      disabled={submitting}
                      onClick={closeConfirm}
                      aria-label="Close"
                  >
                    {t2("cancel")}
                  </Button>

                  <Button
                      type="button"
                      loading={submitting}
                      color={confirmAction === "approve" ? "blue" : "red"}
                      onClick={handleConfirm}
                      aria-label="Confirm"
                  >
                    {confirmAction === "approve"
                        ? t1("approveMedia")
                        : t1("denyMedia")}
                  </Button>
                </Group>
              </>
          )}
        </Modal>
      </>
  );
}
