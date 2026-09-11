"use client";


import { ActionIcon } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { useRouter } from "next/navigation";


type BackButtonProps = {
  returnPath?: string;
};

export function BackButton({ returnPath }: BackButtonProps) {
  const router = useRouter();

  return (
    <ActionIcon
      variant="subtle"
      radius="xl"
      size="lg"
      onClick={() => {
        if (returnPath) {
          router.push(returnPath);
        } else {
          router.back();
        }
      }}
      aria-label="Go back"
    >
      <IconArrowLeft size={20} />
    </ActionIcon>
  );
}
