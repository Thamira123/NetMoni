# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  # NetMoni frontend
  {
  Responsive React console for the NetMoni Spring Boot API.
    extends: [
  ## Run locally

  1. Start the backend and PostgreSQL using `docs/backend-setup.md` from the repository root.
  2. From this directory, install dependencies and start Vite:
      // Alternatively, use this for stricter rules
  ```powershell
  npm install
  npm run dev
  ```
      // Optionally, add this for stylistic rules
  The Vite development server proxies `/api` and `/ws` to `http://localhost:8080`. Use `VITE_API_BASE_URL` only when connecting to a deployed API; that deployment must allow the frontend origin through CORS.

  ## Checks
    ],
  ```powershell
  npm run lint
  npm run build
    },
