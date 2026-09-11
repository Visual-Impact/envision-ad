import { Button, Group, NumberInput, Popover, PopoverDropdown, PopoverTarget, Stack, Text } from "@mantine/core";
import { IconChevronDown } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";



interface FilterNumberInputProps{
    id?: string;
    value?: number|null;
    setValue: React.Dispatch<React.SetStateAction<number | null>>;
    label: string;
    placeholder?: string;
    prefix?: string;
    ariaLabel?: string;
    w?: number;
}

export function FilterNumberInput({value, setValue, label, placeholder, prefix, ariaLabel, w}: FilterNumberInputProps){
    return(
        <Stack gap="xs">
            <Text size="sm">{label}</Text>
            <Group gap='xs'>
                {prefix && <Text>{prefix}</Text>}
                <NumberInput
                    placeholder={placeholder}
                    decimalScale={2}
                    hideControls 
                    min={0}
                    w={w}
                    value={value ?? undefined}
                    aria-label={ariaLabel}
                    onChange={(v) => setValue(v === "" || v === null || v === undefined ? null : Number(v))}
                />
            </Group>
        </Stack>
    )
}

interface FilterPopoverProps {
    id?: string;
    buttonName: string;
    isActive?: boolean;
    applyActions: () => void;
    onReset?: () => void;
    onOpen?: () => void;
    children?: React.ReactNode;
}

function FilterPopover({id, buttonName, isActive, applyActions, onReset, onOpen, children}: FilterPopoverProps){
    const t = useTranslations('browse.browseactions.filters');
    const [opened, setOpened] = useState(false);

    const handleApply = useCallback(() => {
        applyActions();
        setOpened(false);
    }, [applyActions]);

    const handleReset = useCallback(() => {
        onReset?.();
        setOpened(false);
    }, [onReset]);

    function toggleOpen() {
        if (!opened) onOpen?.();
        setOpened(!opened);
    }

    useEffect(() => {
        if (!opened) return;

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Enter") {
                handleApply();
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [opened, handleApply]);
  
    return(
        <Popover id={id} opened={opened} onChange={setOpened} trapFocus position="bottom" withArrow shadow="md" keepMounted >
            <PopoverTarget>
                <Button onClick={toggleOpen} variant={isActive ? "light" : "white"} color={isActive ? "blue" : undefined} rightSection={<IconChevronDown />} size="xs">{buttonName}</Button>
            </PopoverTarget>
            <PopoverDropdown>
                {children}
                <Group justify={onReset ? "space-between" : "flex-end"} mt="md">
                    {onReset && (
                        <Button size="xs" variant="subtle" color="red" onClick={handleReset}>
                            {t("clear")}
                        </Button>
                    )}
                    <Button size="xs" onClick={handleApply}>
                        {t("showresults")}
                    </Button>
                </Group>
            </PopoverDropdown>
            
        </Popover>
    )
}

export interface FilterPricePopoverProps{
    id?: string;
    minPrice?: number|null;
    maxPrice?: number|null;
    setMinPrice: React.Dispatch<React.SetStateAction<number | null>>;
    setMaxPrice: React.Dispatch<React.SetStateAction<number | null>>;
}

export function FilterPricePopover({id, minPrice, maxPrice, setMinPrice, setMaxPrice}: FilterPricePopoverProps) {
    const t = useTranslations('browse.browseactions.filters');
    const [draftMin, setDraftMin] = useState<number | null>(minPrice ?? null);
    const [draftMax, setDraftMax] = useState<number | null>(maxPrice ?? null);

    function handleApply() {
        setMinPrice(draftMin);
        setMaxPrice(draftMax);
    }

    function handleReset() {
        setDraftMin(null);
        setDraftMax(null);
        setMinPrice(null);
        setMaxPrice(null);
    }

    function getPriceLabel() {
        if (minPrice != null && maxPrice != null) return `$${minPrice} – $${maxPrice}`;
        if (minPrice != null) return `≥ $${minPrice}`;
        if (maxPrice != null) return `≤ $${maxPrice}`;
        return t('price');
    }

    const isActive = minPrice != null || maxPrice != null;

    return(
        <FilterPopover id={id} buttonName={getPriceLabel()} isActive={isActive} applyActions={handleApply} onReset={isActive ? handleReset : undefined} onOpen={() => { setDraftMin(minPrice ?? null); setDraftMax(maxPrice ?? null); }}>
            <Group gap='lg' >
                <FilterNumberInput
                    label={t('from')}
                    prefix="$"
                    placeholder={t('from')}
                    ariaLabel="Minimum Price Filter Input"
                    w={125}
                    value={draftMin}
                    setValue={setDraftMin}
                />
                <FilterNumberInput
                    label={t('to')}
                    prefix="$"
                    placeholder={t('to')}
                    ariaLabel="Maximum Price Filter Input"
                    w={125}
                    value={draftMax}
                    setValue={setDraftMax}
                />
            </Group>
        </FilterPopover>
    )
}

export function FilterValuePopover({id, value, setValue, label, placeholder, prefix, ariaLabel}: FilterNumberInputProps) {
    const [draftValue, setdraftValue] = useState<number | null>(value ?? null);

    function handleApply() {
        setValue(draftValue);
    }

    function handleReset() {
        setdraftValue(null);
        setValue(null);
    }

    const activeLabel = value != null ? `≥ ${prefix ?? ""}${value.toLocaleString()}` : label;
    const isActive = value != null;

    return(
        <FilterPopover id={id} buttonName={activeLabel} isActive={isActive} applyActions={handleApply} onReset={isActive ? handleReset : undefined} onOpen={() => setdraftValue(value ?? null)}>
            <Group gap='lg'>
                <FilterNumberInput
                    label={label}
                    prefix={prefix}
                    placeholder={placeholder}
                    value={draftValue}
                    ariaLabel={ariaLabel}
                    setValue={setdraftValue}
                />
            </Group>
        </FilterPopover>
    )
}