import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--moon', styles.heroBanner)}>
      <div className="container">
        <img
          className="hero__logo"
          src="img/logo.png"
          alt="MoonTweaker logo"
        />
        <h1 className="hero__title">{siteConfig.title}</h1>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className="button button--primary button--lg"
            to="/docs/getting-started/quick-start">
            Get started
          </Link>
          <Link
            className="button button--secondary button--lg"
            to="/docs/internals/mod-init/">
            How mod init works
          </Link>
        </div>
        <p className={styles.versionNote}>
          Minecraft 1.12.2 · Forge 14.23.5.2847+ · Lua 5.2 · Java 8
        </p>
      </div>
    </header>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="Lua-powered Minecraft 1.12.2 tweaking"
      description={siteConfig.tagline}>
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <section className="container" style={{paddingBottom: '4rem'}}>
          <div className="row">
            <div className="col col--8 col--offset-2">
              <h2>Everything is a stage</h2>
              <p>
                MoonTweaker does not patch Minecraft classes. It hooks ordinary
                Forge lifecycle events and runs your scripts at the exact point
                where each operation is legal: content registration at
                pre-initialization, recipes while the recipe registry is open,
                and loot, events and advancements when the server is ready.
              </p>
              <p>
                Read the <Link to="/docs/internals/mod-init/">
                  mod initialization deep dive
                </Link>{' '}
                for the full sequence, from Forge discovering the jar to the
                first server tick.
              </p>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
