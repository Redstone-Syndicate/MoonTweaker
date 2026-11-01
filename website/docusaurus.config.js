// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'MoonTweaker',
  tagline: 'Lua-powered server tweaking for Minecraft 1.12.2',
  favicon: 'img/logo.png',

  url: 'https://kartendsy.github.io',
  baseUrl: '/MoonTweaker/',

  organizationName: 'Kartendsy',
  projectName: 'MoonTweaker',
  trailingSlash: false,

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },
  themes: ['@docusaurus/theme-mermaid'],

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          editUrl:
            'https://github.com/Kartendsy/MoonTweaker/tree/main/website/',
          showLastUpdateTime: false,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/logo.png',
      colorMode: {
        defaultMode: 'dark',
        disableSwitch: false,
        respectPrefersColorScheme: true,
      },
      docs: {
        sidebar: {
          hideable: true,
          autoCollapseCategories: false,
        },
      },
      navbar: {
        title: 'MoonTweaker',
        logo: {
          alt: 'MoonTweaker logo',
          src: 'img/logo.png',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docs',
            position: 'left',
            label: 'Docs',
          },
          {
            to: '/docs/api/recipes',
            position: 'left',
            label: 'API',
          },
          {
            to: '/docs/guides/advancement-trees',
            position: 'left',
            label: 'Guides',
          },
          {
            to: '/docs/development/building',
            position: 'left',
            label: 'Development',
          },
          {
            href: 'https://github.com/Kartendsy/MoonTweaker',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Documentation',
            items: [
              {label: 'Introduction', to: '/docs/intro'},
              {label: 'Installation', to: '/docs/getting-started/installation'},
              {label: 'Quick start', to: '/docs/getting-started/quick-start'},
            ],
          },
          {
            title: 'Deep dives',
            items: [
              {label: 'Mod initialization', to: '/docs/internals/mod-init/'},
              {label: 'Script engine', to: '/docs/internals/script-engine/'},
              {label: 'Architecture', to: '/docs/internals/architecture'},
            ],
          },
          {
            title: 'Reference',
            items: [
              {label: 'Advancements', to: '/docs/api/advancements'},
              {label: 'Error reference', to: '/docs/reference/errors'},
              {label: 'FAQ', to: '/docs/reference/faq'},
            ],
          },
          {
            title: 'Project',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/Kartendsy/MoonTweaker',
              },
              {
                label: 'Issues',
                href: 'https://github.com/Kartendsy/MoonTweaker/issues',
              },
              {label: 'License', to: '/docs/reference/license'},
            ],
          },
        ],
        copyright: `MoonTweaker by Kartendsy. GPL-3.0-or-later. Built with Docusaurus.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.vsDark,
        additionalLanguages: ['lua', 'java', 'json', 'bash', 'groovy'],
      },
      tableOfContents: {
        minHeadingLevel: 2,
        maxHeadingLevel: 4,
      },
    }),
};

export default config;
