// Numerical/logic tests for symbolExpr.js (parser + evaluator + spread). Pure module → direct node import.
//   Run: node test/symbolExpr.test.mjs   (from frontend/prochart)
import { parseSymbolExpr, evalAst, computeSpread } from '../../../src/engine/symbolExpr.js';
let pass=0, fail=0; const near=(a,b,e=1e-9)=>a!=null&&b!=null&&Math.abs(a-b)<=e;
const ck=(n,c,got,exp)=>{ if(c){pass++;console.log('PASS',n);} else {fail++;console.log('FAIL',n,'got',JSON.stringify(got),'exp',JSON.stringify(exp));} };
const P=parseSymbolExpr;

// plain symbol
{ const r=P('EURUSD'); ck('plain symbol isExpr=false', r&&r.isExpr===false && r.symbols.join()==='EURUSD', r&&{i:r.isExpr,s:r.symbols},'false/[EURUSD]'); }
// ratio
{ const r=P('EURUSD/GBPUSD'); ck('ratio parse', r&&r.isExpr===true && r.symbols.sort().join()==='EURUSD,GBPUSD', r&&r.symbols,'[EURUSD,GBPUSD]');
  ck('ratio eval', r&&near(r.eval({EURUSD:1.1,GBPUSD:1.3}),1.1/1.3), r&&r.eval({EURUSD:1.1,GBPUSD:1.3}),1.1/1.3); }
// scalar mult
{ const r=P('XAUUSD*2'); ck('mult eval', near(r.eval({XAUUSD:2000}),4000), r.eval({XAUUSD:2000}),4000); }
// subtraction
{ const r=P('US30-US500'); ck('sub eval', near(r.eval({US30:35000,US500:4500}),30500), r.eval({US30:35000,US500:4500}),30500); }
// precedence & parens
{ ck('precedence A+B*C=7', near(P('A+B*C').eval({A:1,B:2,C:3}),7), P('A+B*C').eval({A:1,B:2,C:3}),7);
  ck('A*B+C=5', near(P('A*B+C').eval({A:1,B:2,C:3}),5), P('A*B+C').eval({A:1,B:2,C:3}),5);
  ck('(A+B)*C=9', near(P('(A+B)*C').eval({A:1,B:2,C:3}),9), P('(A+B)*C').eval({A:1,B:2,C:3}),9);
  ck('(A+B)/2=5', near(P('(A+B)/2').eval({A:4,B:6}),5), P('(A+B)/2').eval({A:4,B:6}),5); }
// unary minus
{ ck('unary -A+B=3', near(P('-A+B').eval({A:2,B:5}),3), P('-A+B').eval({A:2,B:5}),3);
  ck('2*-3=-6', near(P('2*-3').eval({}),-6), P('2*-3').eval({}),-6); }
// div-by-zero & missing → null
{ ck('div0 → null', P('A/B').eval({A:1,B:0})===null, P('A/B').eval({A:1,B:0}),null);
  ck('missing sym → null', P('A/B').eval({A:1})===null, P('A/B').eval({A:1}),null); }
// invalid → null
{ ck('trailing op A/ → null', P('A/')===null, P('A/'),null);
  ck('two syms no op → null', P('A B')===null, P('A B'),null);
  ck('unbalanced ( → null', P('(A+B')===null, P('(A+B'),null);
  ck('empty → null', P('')===null, P(''),null);
  ck('bad char → null', P('A@B')===null, P('A@B'),null); }
// computeSpread alignment + OHLC + h>=l guard
{ const A=[{t:1,o:2,h:3,l:1,c:2.5},{t:2,o:2.5,h:4,l:2,c:3},{t:3,o:3,h:3.5,l:2.5,c:3.2}];
  const B=[{t:1,o:1,h:1.2,l:0.8,c:1},{t:2,o:1,h:1.1,l:0.9,c:1},{t:9,o:1,h:1,l:1,c:1}];
  const r=P('A/B'); const sp=computeSpread({A,B}, r.ast);
  ck('spread aligns to common times', sp.length===2 && sp[0].t===1 && sp[1].t===2, sp.map(x=>x.t),'[1,2]');
  ck('spread t1 close=2.5', near(sp[0].c,2.5), sp[0].c,2.5);
  ck('spread t1 high=2.5 low=1.25', near(sp[0].h,2.5)&&near(sp[0].l,1.25), {h:sp[0].h,l:sp[0].l},{h:2.5,l:1.25});
  ck('spread t2 close=3', near(sp[1].c,3), sp[1].c,3);
  ck('spread h>=l always', sp.every(x=>x.h>=x.l), 'ok','h>=l'); }
console.log(`\n=== ${pass} passed, ${fail} failed ===`);
if(fail) process.exit(1);
