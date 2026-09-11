import {useState} from "react";
import {OrganizationRequestDTO, OrganizationSize} from "@/entities/organization";

export function useOrganizationForm() {
    const initialState: OrganizationRequestDTO = {
        name: "",
        organizationSize: OrganizationSize.SMALL,

        address: {
            street: "",
            city: "",
            state: "",
            zipCode: "",
            country: "",
        },

        roles: {
            advertiser: false,
            mediaOwner: false,
        },
    };

    const [formState, setFormState] = useState<OrganizationRequestDTO>(initialState);

    const updateField = <K extends keyof OrganizationRequestDTO>(
        key: K,
        value: OrganizationRequestDTO[K]
    ) => {
        setFormState(prev => ({
            ...prev,
            [key]: value,
        }));
    };


    const resetForm = () => {
        setFormState(initialState);
    };

    return {formState, updateField, resetForm, setFormState};
}
