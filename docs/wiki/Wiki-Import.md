# Publishing iSLDevs Banking Platform documentation to GitHub Wiki

This folder contains the **canonical, versioned Markdown source** for [isldevs/banking-platform](https://github.com/isldevs/banking-platform):

- `Home.md`
- `Architecture.md`
- `Services.md`
- `Getting-Started.md`
- `Authentication-and-OAuth2.md`
- `API-Reference.md`
- `Configuration.md`
- `Security-and-Operations.md`
- `Development-and-Delivery.md`

Changes in this folder are versioned and reviewed with the main repository. GitHub's native Wiki, if enabled, is a **separate Git repository** and is not synchronized automatically.

## Important note

These files are source content for Wiki pages. Committing files under `docs/wiki/` does **not** by itself create pages
under the repository's Wiki tab. To publish, create pages in the GitHub Wiki using the matching page names and paste the
content, or clone the Wiki's dedicated Git repository and add these Markdown files there.

The connected GitHub integration can update regular repository files but does not expose a write operation for the separate Wiki repository. No native Wiki page should be reported as published until visible under the Wiki tab.

Once the native Wiki is enabled and an initial page exists, its Git remote is typically `https://github.com/isldevs/banking-platform.wiki.git`. Verify the remote exists before cloning it; do not assume that updating `docs/wiki/` publishes it.

These source pages use **relative Markdown links** such as `[Architecture](Architecture.md)` so GitHub source browsing works. Check and adjust links as needed if importing into the native Wiki renderer.

Suggested page creation order: Home, Architecture, Services, Getting Started, Authentication and OAuth2, API Reference,
Configuration, Security and Operations, Development and Delivery.
