const extendedMeetAtMap = new Map<number, string>();

export const setExtendedMeetAt = (matchId: number, extendedMeetAt: string) => {
    extendedMeetAtMap.set(matchId, extendedMeetAt);
};

export const applyExtendedMeetAt = <T extends { matchId: number; meetAt: string }>(matches: T[]): T[] => {
    return matches.map((match) => {
        const extended = extendedMeetAtMap.get(match.matchId);
        return extended ? { ...match, meetAt: extended } : match;
    });
};