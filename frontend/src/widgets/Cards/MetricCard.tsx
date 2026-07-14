import { Card, Group, Text, ThemeIcon } from "@mantine/core";
import styles from "./MetricCard.module.css";

type MetricCardProps = {
    label: string;
    value: string;
    description?: string;
    icon?: React.ReactNode;
    /** Mantine color key driving the label text and icon chip. @default "blue" */
    color?: string;
};

export function MetricCard({ label, value, description, icon, color = "blue" }: MetricCardProps) {
    return (
        <Card shadow="sm" radius="lg" p="xl" className={styles.card} style={{ height: "100%" }}>
            <Group justify="space-between" wrap="nowrap" align="flex-start">
                <Text size="sm" c={color} fw={700} tt="uppercase" style={{ minWidth: 0 }}>
                    {label}
                </Text>

                {icon ? (
                    <ThemeIcon color={color} variant="light" size={38} radius="md" style={{ flexShrink: 0 }}>
                        {icon}
                    </ThemeIcon>
                ) : null}
            </Group>

            <Text fw={800} size="2rem" lh={1.15} mt={20}>
                {value}
            </Text>

            {description ? (
                <Text c="dimmed" size="xs" mt={6}>
                    {description}
                </Text>
            ) : null}
        </Card>
    );
}
