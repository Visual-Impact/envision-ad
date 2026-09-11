import createMiddleware from "next-intl/middleware";
import { routing } from "@/shared/lib/i18n";
import { auth0 } from "@/shared/api/index.server";
import { NextRequest, NextResponse } from "next/server";
import { Auth0ManagementService } from "@/shared/api/index.server";
import { ROUTE_PERMISSIONS } from "@/shared/lib/auth";
import {jwtDecode} from "jwt-decode";
import {Token} from "@/entities/auth";

const intlMiddleware = createMiddleware(routing);
const LOCALE_REGEX = new RegExp(`^/(${routing.locales.join('|')})(/.*)?$`);

function isValidLocale(locale: string): locale is (typeof routing.locales)[number] {
  return (routing.locales as readonly string[]).includes(locale);
}

async function mergeAuthHeaders(response: NextResponse, request: NextRequest): Promise<void> {
  const authResponse = await auth0.middleware(request);
  for (const [key, value] of authResponse.headers) {
    if (key.toLowerCase() !== 'x-middleware-next' || response.status < 300) {
      response.headers.set(key, value);
    }
  }
}

export default async function proxy(request: NextRequest) {
  if (request.nextUrl.pathname === "/auth/callback") {
    const error = request.nextUrl.searchParams.get('error');

    if (error) {
      const cookieLocale = request.cookies.get('NEXT_LOCALE')?.value;
      const locale = (cookieLocale && isValidLocale(cookieLocale))
              ? cookieLocale
              : routing.defaultLocale;

      const errorUrl = new URL(`/${locale}/auth/error`, request.url);
      errorUrl.searchParams.set('error', error);
      const errorDescription = request.nextUrl.searchParams.get('error_description');
      if (errorDescription) {
        errorUrl.searchParams.set('error_description', errorDescription);
      }

      return NextResponse.redirect(errorUrl);
    }
  }

  if (request.nextUrl.pathname.startsWith("/auth")) {
    return await auth0.middleware(request);
  }

  const intlResponse = intlMiddleware(request);

  if (intlResponse.status >= 300 && intlResponse.status < 400) {
    return intlResponse;
  }

  const [session, pathname] = await Promise.all([
    auth0.getSession(request),
    Promise.resolve(request.nextUrl.pathname)
  ]);

  const localeMatch = pathname.match(LOCALE_REGEX);
  const locale = localeMatch?.[1] || routing.defaultLocale;
  const routeKey = getRouteKey(pathname, locale);

  if (routeKey) {
    const matchedProtection = Object.entries(ROUTE_PERMISSIONS)
        .filter(([route]) => {
          if (routeKey === route) return true;
          return routeKey.startsWith(route + '/');
        })
        .sort((a, b) => b[0].length - a[0].length)[0];

    if (matchedProtection) {
      const [, requiredPermission] = matchedProtection;

      if (!session?.user) {
        const loginUrl = new URL(`/auth/login`, request.url);
        loginUrl.searchParams.set('ui_locales', locale);
        loginUrl.searchParams.set('returnTo', pathname);
        return NextResponse.redirect(loginUrl);
      }

      if (requiredPermission) {
        try {
          const { token } = await auth0.getAccessToken(request, intlResponse);
          const permissions = jwtDecode<Token>(token).permissions;

          if (!permissions.includes(requiredPermission)) {
            return NextResponse.rewrite(new URL(`/${locale}/forbidden`, request.url));
          }
        } catch (error) {
          console.error('Error checking permissions:', error);
          return NextResponse.rewrite(new URL(`/${locale}/forbidden`, request.url));
        }
      }
    }
  }

  if (session?.user) {
    let preferredLang = request.cookies.get('user_preferred_language')?.value;

    if (!preferredLang) {
      try {
        preferredLang = await Auth0ManagementService.getUserLanguage(session.user.sub);
        if (!preferredLang || !isValidLocale(preferredLang)) {
          preferredLang = routing.defaultLocale;
        }
      } catch (error) {
        console.error('Failed to fetch user preferred language:', error);
        preferredLang = routing.defaultLocale;
      }
    }

    if (preferredLang && isValidLocale(preferredLang) && locale !== preferredLang) {
      const newPathname = pathname.replace(new RegExp(`^/${locale}(/|$)`), `/${preferredLang}$1`);
      const url = new URL(newPathname, request.url);
      url.search = request.nextUrl.search;

      const response = NextResponse.redirect(url);
      response.cookies.set('user_preferred_language', preferredLang, {
        maxAge: 60 * 60 * 24 * 365,
        path: '/',
      });

      await mergeAuthHeaders(response, request);
      return response;
    }

    if (preferredLang) {
      intlResponse.cookies.set('user_preferred_language', preferredLang, {
        maxAge: 60 * 60 * 24 * 365,
        path: '/',
      });
    }
  }

  await mergeAuthHeaders(intlResponse, request);
  return intlResponse;
}

function getRouteKey(pathname: string, locale: string): string | null {
  const pathWithoutLocale = pathname.replace(new RegExp(`^/(${routing.locales.join('|')})`), '') || '/';
  const pathnames = routing.pathnames as Record<string, string | Record<string, string>>;

  for (const [key, value] of Object.entries(pathnames)) {
    if (typeof value === 'string') {
      if (value === pathWithoutLocale) return key;
    } else {
      const localizedPath = value[locale as keyof typeof value];
      if (localizedPath === pathWithoutLocale) return key;
    }
  }

  return null;
}

export const config = {
  matcher: ["/((?!api|trpc|_next|_vercel|.*\\..*).*)"
  ],
};