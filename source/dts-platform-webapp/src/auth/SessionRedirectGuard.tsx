import { useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router';
import { useRedirectIntentStore } from './session-auth';

/**
 * Subscribes to the redirect intent store and performs React Router navigation.
 *
 * Must be placed inside <BrowserRouter> / <HashRouter> so useNavigate() works.
 * Typically rendered in App.tsx alongside <SessionManager />.
 *
 * Flow:
 *   1. Any caller (apiClient, SessionManager, analyticsApi) writes intent to Zustand
 *   2. This component detects the intent change
 *   3. Navigates to /auth/login?redirect=<returnPath> via React Router (soft, no reload)
 *   4. Clears the intent so it doesn't re-trigger
 */
export default function SessionRedirectGuard() {
	const intent = useRedirectIntentStore((s) => s.intent);
	const clearIntent = useRedirectIntentStore((s) => s.clearIntent);
	const navigate = useNavigate();
	const location = useLocation();
	const lastSeqRef = useRef(0);

	useEffect(() => {
		if (!intent || intent.seq <= lastSeqRef.current) return;
		lastSeqRef.current = intent.seq;

		// Don't redirect if already on login page
		const currentPath = location.pathname;
		if (currentPath === '/auth/login' || currentPath.endsWith('/auth/login')) {
			clearIntent();
			return;
		}

		const returnUrl = encodeURIComponent(intent.returnPath);
		navigate(`/auth/login?redirect=${returnUrl}`, { replace: true });
		clearIntent();
	}, [intent, navigate, location.pathname, clearIntent]);

	return null;
}
