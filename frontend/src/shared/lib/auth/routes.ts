export const ROUTE_PERMISSIONS: Record<string, string | undefined> = {
    '/profile': undefined,
    '/dashboard': undefined,

    '/dashboard/media-owner/proof': 'read:media',
    '/dashboard/media-owner/metrics': 'read:media',
    '/dashboard/media-owner/locations': 'read:media',

    '/dashboard/advertiser/metrics': 'read:campaign',
    '/dashboard/advertiser/campaigns': 'read:campaign',
    '/dashboard/advertiser/subscriptions': 'read:campaign',

    '/dashboard/admin/media/pending': 'update:verification',
    '/dashboard/admin/organization/verification': 'update:verification',
    '/dashboard/admin/metrics': 'patch:media_status',
    '/dashboard/admin/venues': 'manage:venues',
    '/dashboard/admin/bundles': 'manage:bundles',
    '/dashboard/admin/settings': 'manage:settings',

    '/dashboard/organization/overview': undefined,
    '/dashboard/organization/employees': 'read:employee',
};
