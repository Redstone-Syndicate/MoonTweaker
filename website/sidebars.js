// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'intro',
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      items: [
        'getting-started/installation',
        'getting-started/quick-start',
        'getting-started/layout',
      ],
    },
    {
      type: 'category',
      label: 'Mod Initialization',
      collapsed: false,
      items: [
        'internals/mod-init/index',
        'internals/mod-init/discovery-and-preinit',
        'internals/mod-init/registry-events',
        'internals/mod-init/recipe-registration',
        'internals/mod-init/postinit-and-load-complete',
        'internals/mod-init/server-lifecycle',
        'internals/mod-init/runtime',
        'internals/mod-init/state-and-reload',
      ],
    },
    {
      type: 'category',
      label: 'Script Engine',
      collapsed: false,
      items: [
        'internals/script-engine/index',
        'internals/script-engine/sandbox-and-modules',
        'internals/script-engine/reload',
      ],
    },
    {
      type: 'category',
      label: 'Internals',
      collapsed: true,
      items: [
        'internals/architecture',
        'internals/advancements',
        'internals/loot',
        'internals/registry',
        'internals/machines',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      collapsed: false,
      items: [
        'api/data-types',
        'api/globals',
        'api/recipes',
        'api/loot',
        'api/registry',
        'api/machines',
        'api/events',
        'api/advancements',
        'api/commands',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      collapsed: true,
      items: [
        'guides/advancement-trees',
        'guides/finding-ids',
        'guides/assets',
        'guides/testing',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      collapsed: true,
      items: [
        'reference/errors',
        'reference/limitations',
        'reference/faq',
        'reference/logging',
        'reference/license',
      ],
    },
    {
      type: 'category',
      label: 'Development',
      collapsed: true,
      items: ['development/building', 'development/extending'],
    },
  ],
};

export default sidebars;
