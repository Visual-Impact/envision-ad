import { createTheme } from '@mantine/core';
import { Josefin_Sans, Lato } from 'next/font/google';

const josefinSans = Josefin_Sans({
    subsets: ['latin'],
    variable: '--font-josefin',
    display: 'swap',
});

const lato = Lato({
    weight: ['400', '700'],
    subsets: ['latin'],
    variable: '--font-lato',
    display: 'swap',
});

export const theme = createTheme({
  colors: {
    deepBlue: [
      '#e8f4ff', '#d3e5f8', '#a7caf0', '#78ade8', '#5294e0',
      '#3982db', '#2878d9', '#1a64c2', '#0f59b0', '#004c9e',
    ],
    blue: [
      '#e7f5ff', '#d0ebff', '#a5d8ff', '#74c0fc', '#4dabf7',
      '#339af0', '#0795ED', '#1c7ed6', '#1971c2', '#1864ab',
    ],
    gray: [
      '#f6f8fb',
      '#e7ebf3',
      '#cfd6e4',
      '#e9eaee',
      '#9aa9c0',
      '#7f92ad',
      '#56677d',
      '#4f6175',
      '#374357',
      '#1f2638',
    ],
    teal: [
        '#e6fcf5',
        '#c3fae8',
        '#96f2d7',
        '#63e6be',
        '#38d9a9',
        '#20c997',
        '#0b6c4f',
        '#0ca678',
        '#099268',
        '#087f5b',
    ],
    red: [
        '#fff5f5',
        '#ffe3e3',
        '#ffc9c9',
        '#ffa8a8',
        '#ff8787',
        '#ff6b6b',
        '#be0000',
        '#f03e3e',
        '#e03131',
        '#c92a2a',
    ]
  },
  fontFamily: 'var(--font-lato), Lato, sans-serif', // for all regular text
  headings: {
    fontFamily: 'var(--font-josefin), Josefin Sans, sans-serif',
    sizes: {
      h1: { fontSize: '36px' },
      h2: { fontSize: '24px' },
      h3: { fontSize: '20px' },
      h4: { fontSize: '16px' },
      h5: { fontSize: '14px' },
    },
  },
  defaultRadius: 'lg',
  // Used by Mantine's built-in `variant="gradient"` (Button, ThemeIcon, ActionIcon, text gradient)
  // when no explicit `gradient` prop is passed. Matches the brand gradient established on the
  // home page hero (see docs/design-overhaul/project-brief.md). Additive — does not change the
  // default (non-gradient) variant of any component.
  defaultGradient: { from: '#0795ED', to: '#a855f7', deg: 95 },
  other: {
    gradientBrand: 'linear-gradient(95deg, #0795ED 0%, #a855f7 100%)',
    gradientBrandSoft: 'linear-gradient(95deg, rgba(7, 149, 237, 0.10) 0%, rgba(168, 85, 247, 0.10) 100%)',
  },
});

// Export fonts to use in layout.tsx
export { josefinSans, lato };