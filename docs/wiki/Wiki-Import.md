# Publishing this documentation to the GitHub Wiki

This folder contains the Markdown source for the project wiki pages:

- `Home.md`
- `Architecture.md`
- `Services.md`
- `Getting-Started.md`
- `Authentication-and-OAuth2.md`
- `API-Reference.md`
- `Configuration.md`
- `Security-and-Operations.md`
- `Development-and-Delivery.md`

The main repository documentation is useful for code review and versioned changes. The native GitHub Wiki is stored
separately from the regular source repository.

## Important note

These files are source content for Wiki pages. Committing files under `docs/wiki/` does **not** by itself create pages
under the repository's Wiki tab. To publish, create pages in the GitHub Wiki using the matching page names and paste the
content, or clone the Wiki's dedicated Git repository and add these Markdown files there.

The connected GitHub integration used for this repository can create and update normal repository files, but it does not
expose a write operation for the separate Wiki Git repository. No native Wiki page should be reported as published until
it is visible under the Wiki tab.

Suggested page creation order: Home, Architecture, Services, Getting Started, Authentication and OAuth2, API Reference,
Configuration, Security and Operations, Development and Delivery.
