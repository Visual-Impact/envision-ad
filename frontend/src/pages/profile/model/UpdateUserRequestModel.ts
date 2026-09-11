export interface UpdateUserRequestModel {
    given_name?: string;
    family_name?: string;
    nickname?: string;
    name?: string;
    user_metadata?: {
        bio?: string;
        [key: string]: unknown;
    };
}