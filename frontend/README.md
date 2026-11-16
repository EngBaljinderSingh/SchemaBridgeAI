# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.0.1.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

API requests use the same-origin `/api` path. The development server proxies these requests to `http://localhost:8080`; production Nginx already proxies `/api/` to the backend.

To use alternate local ports in PowerShell:

```powershell
$env:SCHEMABRIDGE_API_TARGET = 'http://127.0.0.1:8081'
npm start -- --host 127.0.0.1 --port 4300
```

Start a backend on that target port before using the application. The target environment variable is read when the development server starts.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
npm test -- --watch=false
```

## Running end-to-end tests

Start the backend and frontend, then run the existing Chromium browser and HTTP lifecycle tests:

```bash
npx playwright install chromium
npx playwright test
```

The default frontend URL is `http://localhost:4200`. For an alternate frontend port in PowerShell:

```powershell
$env:SCHEMABRIDGE_E2E_BASE_URL = 'http://127.0.0.1:4300'
npx playwright test
```

The tests create isolated test projects. They do not require Aviator to be available when the backend's configured fallback is enabled; this does not verify live AI connectivity or production broker delivery.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
