import { getRequestConfig } from 'next-intl/server';
import { routing } from './routing';

export default getRequestConfig(async ({ requestLocale }) => {
    let locale = await requestLocale;

    if (!locale || !(routing.locales as readonly string[]).includes(locale)) {
        locale = routing.defaultLocale;
    }

    return {
        locale,
        messages: (await import(`../../../../messages/${locale}.json`)).default,
        // next-intl strips IntlError messages in the production build, so
        // `error.code === 'INVALID_MESSAGE'` logs with no indication of which key
        // caused it. getMessageFallback still receives the key, so log it here.
        onError(error) {
            console.error(error);
        },
        getMessageFallback({ error, key, namespace }) {
            const fullKey = [namespace, key].filter(Boolean).join('.');
            if ((error.code as string) === 'INVALID_MESSAGE') {
                console.error(`[next-intl] INVALID_MESSAGE (server) at "${fullKey}"`);
            }
            return fullKey;
        }
    };
});