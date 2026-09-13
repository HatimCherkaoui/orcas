const REPO_URL = 'https://github.com/orcas/workflow-orchestrator';

/** Sidebar footer: the Orcas trademark (links out to the GitHub repo) and the current year. */
export function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="sidebar-footer">
      <a href={REPO_URL} target="_blank" rel="noreferrer" className="brand-link">
        Orcas {year}
      </a>
    </footer>
  );
}
