import { SpecialSort } from "@/features/media-management";
import { SortOptions } from "@/features/media-management";
import { Group, ScrollArea, Select, Text } from "@mantine/core";
import { useTranslations } from "next-intl";

interface BrowseActionsProps{
    filters?: React.ReactNode;
    sort?: React.ReactNode;
    setSortBy: React.Dispatch<React.SetStateAction<string>>;
    sortSelectValue?: string;
    isMobileVertical: boolean;
    isMobile: boolean;
    mapVisible: boolean;
}

export default function BrowseActions({filters,  setSortBy, sortSelectValue, isMobileVertical, isMobile, mapVisible}: BrowseActionsProps){
    const t = useTranslations('browse.browseactions');
    const sortSelectData =[
        { value: SpecialSort.nearest, label: t('sort.nearest') },
        { value: SortOptions.priceAsc, label: (t('sort.price') + t('sort.asc')) },
        { value: SortOptions.priceDesc, label: (t('sort.price') + t('sort.desc')) },
        { value: SpecialSort.weeklyImpressionsAsc, label: (t('sort.impressions') + t('sort.asc')) },
        { value: SpecialSort.weeklyImpressionsDesc, label: (t('sort.impressions') + t('sort.desc')) },
        { value: SortOptions.loopDurationAsc, label: (t('sort.loop') + t('sort.asc')) },
        { value: SortOptions.loopDurationDesc, label: (t('sort.loop') + t('sort.desc')) },
    ];
    

    
    return(

        <Group justify={(isMobile && mapVisible)? "flex-end" :"space-between"}  style={{zIndex: 1}} w="100%">
            {isMobileVertical ? 
            <ScrollArea w="100%" type="never" >
                <Group wrap="nowrap" w="fit-content" >
                    <Group wrap="nowrap" grow>
                        {filters}
                    </Group>
                    <Group grow w="max-content">
                        <Select
                            id="SortSelect"
                            data={sortSelectData}
                            value={sortSelectValue}
                            defaultValue={SpecialSort.nearest}
                            allowDeselect={false}
                            onOptionSubmit={setSortBy}
                            w="100%"
                            size="xs"
                            />
                    </Group>
                </Group>
            </ScrollArea>
                :
                <><Group>
                    {(!isMobile && !mapVisible) && <Text>{t('filters.actionName')}</Text>}
                    {filters}
                </Group>
                <Group>
                    {(!isMobile && !mapVisible) && <Text>{t('sort.actionName')}</Text>}
                    <Select
                        id="SortSelect"
                        aria-label="Sort Selection"
                        data={sortSelectData}
                        value={sortSelectValue}
                        defaultValue={SpecialSort.nearest}
                        allowDeselect={false}
                        onOptionSubmit={setSortBy}
                        size="xs"
                    />
                </Group></>
            }
        </Group>
    )
}