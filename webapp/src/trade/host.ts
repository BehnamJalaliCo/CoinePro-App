// What the workspace lends trading's components that sit outside its props (the bottom panel has
// none): the sign-in dialog. Set by TradeHost when it mounts.
export const hostActions: { signIn(): void } = { signIn: () => undefined };
