import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const features = [
  {
    title: 'Lua-first, no Java required',
    description:
      'Write recipes, loot tables, advancements and gameplay rules in plain Lua 5.2 files. No recompilation, no coremod, no patched Minecraft classes.',
  },
  {
    title: 'Recipes and loot',
    description:
      'Add shaped, shapeless and furnace recipes, remove any registered crafting recipe, and rewrite loot pools with weighted entries.',
  },
  {
    title: 'Content registration',
    description:
      'Register items, material items, blocks, tools, armor and fluids from Lua, complete with ItemBlocks and buckets.',
  },
  {
    title: 'Machine integration',
    description:
      'Queue recipes for Thermal Expansion, Ender IO and Immersive Engineering through version-tolerant reflection.',
  },
  {
    title: 'Triumph-style advancements',
    description:
      'Define advancement trees in Lua, hide individual advancements or whole vanilla trees, or replace all of vanilla with your own pack.',
  },
  {
    title: 'Sandboxed runtime',
    description:
      'No filesystem, no reflection from Lua, no os.execute. Scripts are loaded per stage with readable, per-file errors.',
  },
];

function Feature({title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="featureCard">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {features.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
