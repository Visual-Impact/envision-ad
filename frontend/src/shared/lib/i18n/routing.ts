import { defineRouting } from 'next-intl/routing';

export const routing = defineRouting({
  locales: ['en', 'fr'],
  defaultLocale: 'fr',
  localePrefix: 'always',
  pathnames: {
    // If locales use the same pathname
    '/': '/',

    '/forbidden': {
      'en': '/forbidden',
      'fr': '/interdit'
    },

    // If locales use different paths
    '/profile': {
      'en': '/profile',
      'fr': '/profil'
    },
    '/browse': {
      'en': '/browse',
      'fr': '/parcourir'
    },
    '/bundles': {
      'en': '/bundles',
      'fr': '/forfaits'
    },
    '/about': {
      'en': '/about',
      'fr': '/a-propos'
    },
    '/privacy': {
      'en': '/privacy',
      'fr': '/confidentialite'
    },
    '/dashboard': {
      'en': '/dashboard',
      'fr': '/tableau-de-bord'
    },

    //dashboards

    //media Owner dashboard
    '/dashboard/media-owner/media': {
      'en': '/dashboard/media-owner/media',
      'fr': '/tableau-de-bord/media-owner/media'
    },
    '/dashboard/media-owner/metrics': {
      'en': '/dashboard/media-owner/metrics',
      'fr': '/tableau-de-bord/media-owner/metriques'
    },
    '/dashboard/media-owner/proof': {
      'en': '/dashboard/media-owner/proof',
      'fr': '/tableau-de-bord/media-owner/preuve'
    },
    '/dashboard/media-owner/locations': {
      'en': '/dashboard/media-owner/locations',
      'fr': '/tableau-de-bord/media-owner/emplacements'
    },

    //advertiser dashboard
    '/dashboard/advertiser/metrics': {
      'en': '/dashboard/advertiser/metrics',
      'fr': '/tableau-de-bord/annonceur/metriques'
    },
    '/dashboard/advertiser/overview': {
      'en': '/dashboard/advertiser/overview',
      'fr': '/tableau-de-bord/annonceur/apercu'
    },
    '/dashboard/advertiser/campaigns': {
      'en': '/dashboard/advertiser/campaigns',
      'fr': '/tableau-de-bord/annonceur/campagnes'
    },
    '/dashboard/advertiser/subscriptions': {
      'en': '/dashboard/advertiser/subscriptions',
      'fr': '/tableau-de-bord/annonceur/abonnements'
    },

    // admin dashboard
    "/dashboard/admin/media/pending": {
      'en': "/dashboard/admin/media/pending",
      'fr': "/tableau-de-bord/admin/media/en-attente",
    },
    "/dashboard/admin/organization/verification": {
      'en': "/dashboard/admin/organization/verification",
      'fr': "/tableau-de-bord/admin/organization/verification",
    },
    "/dashboard/admin/metrics": {
      en: "/dashboard/admin/metrics",
      fr: "/tableau-de-bord/admin/metriques",
    },
    "/dashboard/admin/venues": {
      en: "/dashboard/admin/venues",
      fr: "/tableau-de-bord/admin/lieux",
    },
    "/dashboard/admin/bundles": {
      en: "/dashboard/admin/bundles",
      fr: "/tableau-de-bord/admin/forfaits",
    },
    "/dashboard/admin/settings": {
      en: "/dashboard/admin/settings",
      fr: "/tableau-de-bord/admin/parametres",
    },

    //organization dashboard
    '/dashboard/organization/overview': {
      'en': '/dashboard/organization/overview',
      'fr': '/tableau-de-bord/entreprise/apercu'
    },
    '/dashboard/organization/employees': {
      'en': '/dashboard/organization/employees',
      'fr': '/tableau-de-bord/entreprise/employes'
    }
  }
});
