import { useTransition } from "react";
import { Button } from "@mantine/core";
import { useRouter, usePathname } from "@/shared/lib/i18n";
import { useLocale } from "next-intl";
import Image from "next/image";
import { useUser } from "@auth0/nextjs-auth0/client";
import { useSearchParams } from "next/navigation";

const locales = [
  { image: "/images/english.png", locale: "en", alt: "English", label: "EN" },
  { image: "/images/french.png", locale: "fr", alt: "Français", label: "FR" },
];

export function LanguagePicker() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const currentLocale = useLocale();
  const [isPending, startTransition] = useTransition();
  const { user } = useUser();

  const handleToggle = async () => {
    const nextLocale = currentLocale === "en" ? "fr" : "en";

    if (user) {
      try {
        const response = await fetch(`/api/auth0/update-user-language/${encodeURIComponent(user.sub)}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ locale: nextLocale }),
        });

        if (!response.ok) {
          const errorData = await response.json().catch(() => ({}));
          console.error('Failed to save language preference:', response.status, errorData.error || response.statusText);
        }
      } catch (err) {
        console.error('Failed to save language preference', err);
      }
    }

    startTransition(() => {
      router.replace(
          {
            pathname,
            query: Object.fromEntries(searchParams?.entries() || [])
          },
          { locale: nextLocale }
      );
    });
  };

  const current =
      locales.find((item) => item.locale !== currentLocale) || locales[1];

  return (
      <Button
          onClick={handleToggle}
          disabled={isPending}
          variant="transparent"
          radius="xl"
          px="xs"
          aria-label="Switch language"
          styles={{ root: { display: "inline-flex", alignItems: "center", minWidth: "unset" } }}
      >
        <Image src={current.image} width={20} height={20} alt={current.alt} />
      </Button>
  );
}