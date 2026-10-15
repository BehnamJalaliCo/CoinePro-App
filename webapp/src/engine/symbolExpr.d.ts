// Types for the old Pro-Chart's symbol-expression parser (symbolExpr.js): spreads and formulas
// such as BTCUSDT/ETHUSDT, XAUUSD*2, (EURUSD+GBPUSD)/2.
export interface ExprNode { op?: '+' | '-' | '*' | '/'; left?: ExprNode; right?: ExprNode; num?: number; sym?: string }
export interface ParsedExpr { isExpr: boolean; symbols: string[]; ast: ExprNode; eval(bySym: Record<string, number>): number | null }
export interface ExprBar { t: number; o: number; h: number; l: number; c: number; v: number }
export function parseSymbolExpr(str: string): ParsedExpr | null;
export function evalAst(node: ExprNode, bySym: Record<string, number>): number | null;
export function computeSpread(candlesBySym: Record<string, ExprBar[]>, ast: ExprNode): ExprBar[];
export default parseSymbolExpr;
