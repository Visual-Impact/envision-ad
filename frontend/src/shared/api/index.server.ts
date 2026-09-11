// Server-only entry point. Never re-export these from index.ts: auth0.ts instantiates the
// server Auth0 client at module load, which would pull server code into client bundles.
export { auth0 } from './auth0/auth0';
export { Auth0ManagementService } from './auth0/management';
