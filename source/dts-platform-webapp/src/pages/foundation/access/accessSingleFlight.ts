export type SingleFlightRef = { current: boolean };

export const acquireSingleFlight = (lock: SingleFlightRef) => {
	if (lock.current) return false;
	lock.current = true;
	return true;
};

export const releaseSingleFlight = (lock: SingleFlightRef) => {
	lock.current = false;
};
