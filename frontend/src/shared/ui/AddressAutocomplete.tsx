"use client";

import { useEffect, useRef, useState } from "react";
import { Combobox, Loader, TextInput, useCombobox } from "@mantine/core";
import { useDebouncedValue } from "@mantine/hooks";
import { AddressDetails, SearchLocations } from "@/shared/lib/geolocation";

interface AddressAutocompleteProps {
    label?: string;
    placeholder?: string;
    description?: string;
    language: string;
    noResultsText?: string;
    onSelect: (details: AddressDetails) => void;
}

export function AddressAutocomplete({
    label,
    placeholder,
    description,
    language,
    noResultsText,
    onSelect,
}: AddressAutocompleteProps) {
    const combobox = useCombobox({
        onDropdownClose: () => combobox.resetSelectedOption(),
    });

    const [query, setQuery] = useState("");
    const [debouncedQuery] = useDebouncedValue(query, 350);
    const [results, setResults] = useState<AddressDetails[]>([]);
    const [loading, setLoading] = useState(false);
    const skipNextSearch = useRef(false);

    useEffect(() => {
        let cancelled = false;

        (async () => {
            if (skipNextSearch.current) {
                skipNextSearch.current = false;
                return;
            }

            if (debouncedQuery.trim().length < 3) {
                setResults([]);
                return;
            }

            setLoading(true);
            try {
                const matches = await SearchLocations(debouncedQuery, language);
                if (cancelled) return;
                setResults(matches);
                if (matches.length > 0) {
                    combobox.openDropdown();
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [debouncedQuery, language]);

    const options = results.map((result, index) => (
        <Combobox.Option value={String(index)} key={`${result.display_name}-${index}`}>
            {result.display_name}
        </Combobox.Option>
    ));

    return (
        <Combobox
            store={combobox}
            withinPortal={false}
            onOptionSubmit={(value) => {
                const selected = results[Number(value)];
                if (selected) {
                    onSelect(selected);
                    skipNextSearch.current = true;
                    setQuery(selected.display_name);
                    setResults([]);
                }
                combobox.closeDropdown();
            }}
        >
            <Combobox.Target>
                <TextInput
                    label={label}
                    description={description}
                    placeholder={placeholder}
                    value={query}
                    rightSection={loading ? <Loader size="xs" /> : null}
                    onChange={(event) => {
                        setQuery(event.currentTarget.value);
                        combobox.updateSelectedOptionIndex();
                    }}
                    onFocus={() => {
                        if (results.length > 0) combobox.openDropdown();
                    }}
                    onBlur={() => combobox.closeDropdown()}
                />
            </Combobox.Target>

            <Combobox.Dropdown>
                <Combobox.Options mah={220} style={{ overflowY: "auto" }}>
                    {options.length > 0 ? options : <Combobox.Empty>{noResultsText}</Combobox.Empty>}
                </Combobox.Options>
            </Combobox.Dropdown>
        </Combobox>
    );
}
