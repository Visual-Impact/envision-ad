const ADMIN_PERMISSIONS = ['patch:media_status', 'readAll:verification', 'update:verification'];

export function isAdmin(permissions: string[]): boolean {
    return ADMIN_PERMISSIONS.every((permission) => permissions.includes(permission));
}
