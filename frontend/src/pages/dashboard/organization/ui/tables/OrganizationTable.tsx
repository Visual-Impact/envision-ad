import {Button, Card, Group, Stack, Text, Title} from "@mantine/core";
import {useTranslations} from "next-intl";
import {OrganizationResponseDTO} from "@/entities/organization";
import {IconDiscountCheck, IconEdit} from "@tabler/icons-react";
import {useUser} from "@auth0/nextjs-auth0/client";

interface OrganizationDetailProps {
    organization: OrganizationResponseDTO;
    onEdit?: (id: string | number) => void;
}

export function OrganizationDetail({organization, onEdit}: OrganizationDetailProps) {
    const t = useTranslations("organization");
    const {user} = useUser();

    if (!organization) return <Text>No organization data found</Text>;

    return (
        <Card shadow="sm" padding="lg" radius="lg">
            <Stack gap="sm">
                <Group justify="space-between">
                    <Group gap="xs">
                        <Title order={2}>{organization.name}</Title>
                        {organization.verified &&
                            <IconDiscountCheck size={30} stroke={1.5} />
                        }
                    </Group>
                    <Group gap="xs">
                        {onEdit && user?.sub === organization.ownerId && (
                            <Button
                                leftSection={<IconEdit size={16}/>}
                                variant="light"
                                color="blue"
                                size="xs"
                                onClick={() => onEdit(organization.businessId)}
                            >
                                {t("table.edit")}
                            </Button>
                        )}
                    </Group>
                </Group>

                <Stack gap={4}>
                    <Text>
                        <strong>{t("table.organizationSize")}: </strong>
                        {t(`sizes.${organization.organizationSize}`)}
                    </Text>
                    <Text>
                        <strong>{t("table.address")}: </strong>
                        {organization.address
                            ? `${organization.address.street}, ${organization.address.city}, ${organization.address.state}`
                            : "-"}
                    </Text>
                    <Text>
                        <strong>{t("table.dateCreated")}: </strong>
                        {organization.dateCreated
                            ? new Date(organization.dateCreated).toLocaleDateString()
                            : "-"}
                    </Text>
                    <Text>
                        <strong>{t("table.roles")}: </strong>
                        {organization.roles ? (
                            <>
                                {organization.roles.mediaOwner && t("roles.mediaOwner")}
                                {organization.roles.mediaOwner && organization.roles.advertiser && ", "}
                                {organization.roles.advertiser && t("roles.advertiser")}

                                {!organization.roles.mediaOwner && !organization.roles.advertiser && "-"}
                            </>
                        ) : (
                            "-"
                        )}
                    </Text>
                </Stack>
            </Stack>
        </Card>
    );
}
