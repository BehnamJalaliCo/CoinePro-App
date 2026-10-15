// نمااسکریپت — موتورِ اجرای کدِ کاربر در Web Worker سندباکس (بدونِ DOM/شبکه، تایم‌اوت).
// مدلِ سری‌محور (vectorized): هر سری آرایه است. توابعِ ta.* آرایه می‌دهند؛
// کمک‌توابعِ عملگری (add/sub/...) عنصر-به-عنصر روی آرایه/عدد کار می‌کنند؛
// input/plot/strategy/label/... خروجی جمع می‌کنند. سازگار با ویرایشگر + پنلِ مرجع.

const WORKER_SRC = `
self.onmessage = function (e) {
  var d = e.data, O=d.open,H=d.high,L=d.low,C=d.close,V=d.volume,T=d.time, N=C.length, IN=d.inputs||{};
  var plots=[], shapes=[], hlines=[], bgs=[], labels=[], alerts=[], inputs=[], zones=[], fills=[], lines=[], boxes=[], tables=[], barcolors=[], candleplots=[], strat={enabled:false,long:null,short:null,exitL:null,exitS:null};

  function arr(x){ if(Array.isArray(x))return x; var a=new Array(N); for(var i=0;i<N;i++)a[i]=x; return a; }
  function bin(a,b,f){ a=arr(a);b=arr(b); var o=new Array(N); for(var i=0;i<N;i++){ o[i]=(a[i]==null||b[i]==null)?null:f(a[i],b[i]); } return o; }
  function un(a,f){ a=arr(a); var o=new Array(N); for(var i=0;i<N;i++) o[i]=a[i]==null?null:f(a[i]); return o; }
  function add(a,b){return bin(a,b,function(x,y){return x+y;});}
  function sub(a,b){return bin(a,b,function(x,y){return x-y;});}
  function mul(a,b){return bin(a,b,function(x,y){return x*y;});}
  function div(a,b){return bin(a,b,function(x,y){return y?x/y:null;});}
  function gt(a,b){return bin(a,b,function(x,y){return x>y;});}
  function lt(a,b){return bin(a,b,function(x,y){return x<y;});}
  function ge(a,b){return bin(a,b,function(x,y){return x>=y;});}
  function le(a,b){return bin(a,b,function(x,y){return x<=y;});}
  function and(a,b){return bin(a,b,function(x,y){return !!x&&!!y;});}
  function or(a,b){return bin(a,b,function(x,y){return !!x||!!y;});}
  function iff(c,a,b){ c=arr(c);a=arr(a);b=arr(b); var o=new Array(N); for(var i=0;i<N;i++)o[i]=c[i]?a[i]:b[i]; return o; }
  function nz(a,r){ a=arr(a); var o=new Array(N); for(var i=0;i<N;i++)o[i]=(a[i]==null)?(r||0):a[i]; return o; } // قبلاً از un می‌گذشت که روی nullها short-circuit می‌کرد ⇒ nz هرگز null را جایگزین نمی‌کرد (باگ).
  // برای نمایش (جدول/برچسب/رشته): از یک سری، آخرین مقدارِ معتبر را با گردکردنِ هوشمند بده
  function lastv(x){ if(Array.isArray(x)){ for(var i=x.length-1;i>=0;i--)if(x[i]!=null&&isFinite(x[i]))return x[i]; return null; } return x; }
  function disp(x){ var v=lastv(x); if(typeof v==='number'){ var a=Math.abs(v),d=a>=100?2:a>=1?3:5,m=Math.pow(10,d); return Math.round(v*m)/m; } return v==null?'':v; }

  function sma(s,p){s=arr(s);var o=new Array(N).fill(null),sum=0,cnt=0;for(var i=0;i<N;i++){var v=s[i];if(v==null){o[i]=null;continue;}sum+=v;cnt++;if(i>=p){if(s[i-p]!=null){sum-=s[i-p];cnt--;}}if(cnt>=p)o[i]=sum/p;}return o;}
  function ema(s,p){s=arr(s);var o=new Array(N).fill(null),k=2/(p+1),pr=null;for(var i=0;i<N;i++){var v=s[i];if(v==null)continue;pr=pr==null?v:v*k+pr*(1-k);if(i>=p-1)o[i]=pr;}return o;}
  function rma(s,p){s=arr(s);var o=new Array(N).fill(null),pr=null;for(var i=0;i<N;i++){var v=s[i];if(v==null)continue;pr=pr==null?v:(pr*(p-1)+v)/p;o[i]=i>=p-1?pr:null;}return o;}
  function wma(s,p){s=arr(s);var o=new Array(N).fill(null),dn=p*(p+1)/2;for(var i=p-1;i<N;i++){var x=0,ok=1;for(var j=0;j<p;j++){if(s[i-j]==null){ok=0;break;}x+=s[i-j]*(p-j);}o[i]=ok?x/dn:null;}return o;}
  function hma(s,p){var h=Math.max(1,Math.floor(p/2)),sq=Math.max(1,Math.round(Math.sqrt(p)));var a=wma(s,h),b=wma(s,p);var df=[];for(var i=0;i<N;i++)df[i]=(a[i]!=null&&b[i]!=null)?2*a[i]-b[i]:null;var w=wma(df.map(function(v){return v==null?0:v;}),sq);return w.map(function(v,i){return df[i]==null?null:v;});}
  function vwma(s,p){s=arr(s);var pv=mul(s,V),o=new Array(N).fill(null);var sp=sma(pv,p),sv=sma(V,p);for(var i=0;i<N;i++)o[i]=(sp[i]!=null&&sv[i])?sp[i]/sv[i]:null;return o;}
  function vwap(){var pv=0,vv=0,o=new Array(N);for(var i=0;i<N;i++){var tp=(H[i]+L[i]+C[i])/3;pv+=tp*(V[i]||0);vv+=V[i]||0;o[i]=vv?pv/vv:C[i];}return o;}
  function stdev(s,p){s=arr(s);var m=sma(s,p),o=new Array(N).fill(null);for(var i=p-1;i<N;i++){if(m[i]==null)continue;var x=0;for(var j=0;j<p;j++){var dd=s[i-j]-m[i];x+=dd*dd;}o[i]=Math.sqrt(x/p);}return o;}
  function change(s,n){s=arr(s);n=n||1;var o=new Array(N).fill(null);for(var i=n;i<N;i++)o[i]=(s[i]!=null&&s[i-n]!=null)?s[i]-s[i-n]:null;return o;}
  function mom(s,n){return change(s,n);}
  function roc(s,n){s=arr(s);n=n||1;var o=new Array(N).fill(null);for(var i=n;i<N;i++)o[i]=(s[i-n])?((s[i]-s[i-n])/s[i-n])*100:null;return o;}
  function highest(s,p){s=arr(s);var o=new Array(N).fill(null);for(var i=p-1;i<N;i++){var m=-Infinity;for(var j=0;j<p;j++)if(s[i-j]!=null)m=Math.max(m,s[i-j]);o[i]=isFinite(m)?m:null;}return o;}
  function lowest(s,p){s=arr(s);var o=new Array(N).fill(null);for(var i=p-1;i<N;i++){var m=Infinity;for(var j=0;j<p;j++)if(s[i-j]!=null)m=Math.min(m,s[i-j]);o[i]=isFinite(m)?m:null;}return o;}
  function rsi(s,p){p=p||14;s=arr(s);var o=new Array(N).fill(null),ag=0,al=0;for(var i=1;i<N;i++){var ch=s[i]-s[i-1],g=Math.max(ch,0),l=Math.max(-ch,0);if(i<=p){ag+=g;al+=l;if(i===p){ag/=p;al/=p;o[i]=100-100/(1+ag/(al||1e-9));}}else{ag=(ag*(p-1)+g)/p;al=(al*(p-1)+l)/p;o[i]=100-100/(1+ag/(al||1e-9));}}return o;}
  function tr(){var o=new Array(N);for(var i=0;i<N;i++)o[i]=i===0?H[i]-L[i]:Math.max(H[i]-L[i],Math.abs(H[i]-C[i-1]),Math.abs(L[i]-C[i-1]));return o;}
  function atr(p){p=p||14;return rma(tr(),p);}
  function cci(p){p=p||20;var tp=[];for(var i=0;i<N;i++)tp[i]=(H[i]+L[i]+C[i])/3;var m=sma(tp,p),o=new Array(N).fill(null);for(var i=p-1;i<N;i++){if(m[i]==null)continue;var md=0;for(var j=0;j<p;j++)md+=Math.abs(tp[i-j]-m[i]);md/=p;o[i]=md?(tp[i]-m[i])/(0.015*md):0;}return o;}
  function macd(s,f,sl,sg){f=f||12;sl=sl||26;sg=sg||9;var ml=sub(ema(s,f),ema(s,sl));var sig=ema(ml,sg).map(function(v,i){return ml[i]==null?null:v;});return {macd:ml,signal:sig,hist:sub(ml,sig)};}
  function bb(s,p,m){p=p||20;m=m||2;var b=sma(s,p),sd=stdev(s,p);return {mid:b,upper:add(b,mul(sd,m)),lower:sub(b,mul(sd,m))};}
  function stoch(p,dd){p=p||14;dd=dd||3;var hh=highest(H,p),ll=lowest(L,p),k=new Array(N).fill(null);for(var i=0;i<N;i++)if(hh[i]!=null)k[i]=(hh[i]===ll[i])?50:((C[i]-ll[i])/(hh[i]-ll[i]))*100;return {k:k,d:sma(k,dd).map(function(v,i){return k[i]==null?null:v;})};}
  function supertrend(p,m){p=p||10;m=m||3;var a=atr(p),line=new Array(N).fill(null),dir=new Array(N).fill(null),up=null,dn=null,d=1;for(var i=0;i<N;i++){if(a[i]==null)continue;var mid=(H[i]+L[i])/2,ub=mid+m*a[i],lb=mid-m*a[i];if(up!=null)ub=(ub<up||C[i-1]>up)?ub:up;if(dn!=null)lb=(lb>dn||C[i-1]<dn)?lb:dn;/* رَچِتِ باندِ نهاییِ استانداردِ SuperTrend: باند فقط تا وقتی قیمت داخلش است سفت می‌ماند و فقط با شکست ریست می‌شود (قبلاً هر بار دوباره پهن می‌شد ⇒ خطِ اشتباه). */if(d===1&&C[i]<(dn==null?lb:dn))d=-1;else if(d===-1&&C[i]>(up==null?ub:up))d=1;up=ub;dn=lb;dir[i]=d;line[i]=d===1?lb:ub;}return {line:line,dir:dir};}
  function crossover(a,b){a=arr(a);b=arr(b);var o=new Array(N).fill(false);for(var i=1;i<N;i++){if(a[i]!=null&&b[i]!=null&&a[i-1]!=null&&b[i-1]!=null)o[i]=a[i-1]<=b[i-1]&&a[i]>b[i];}return o;}
  function crossunder(a,b){a=arr(a);b=arr(b);var o=new Array(N).fill(false);for(var i=1;i<N;i++){if(a[i]!=null&&b[i]!=null&&a[i-1]!=null&&b[i-1]!=null)o[i]=a[i-1]>=b[i-1]&&a[i]<b[i];}return o;}
  function cross(a,b){return or(crossover(a,b),crossunder(a,b));}
  function rising(s,n){s=arr(s);var o=new Array(N).fill(false);for(var i=n;i<N;i++){var ok=true;for(var j=0;j<n;j++)if(!(s[i-j]>s[i-j-1])){ok=false;break;}o[i]=ok;}return o;}
  function falling(s,n){s=arr(s);var o=new Array(N).fill(false);for(var i=n;i<N;i++){var ok=true;for(var j=0;j<n;j++)if(!(s[i-j]<s[i-j-1])){ok=false;break;}o[i]=ok;}return o;}
  function barssince(c){c=arr(c);var o=new Array(N).fill(null),k=null;for(var i=0;i<N;i++){if(c[i]){k=0;}else if(k!=null)k++;o[i]=k;}return o;}
  function valuewhen(c,s,occ){c=arr(c);s=arr(s);occ=occ||0;var o=new Array(N).fill(null);for(var i=0;i<N;i++){var cnt=0;for(var j=i;j>=0;j--){if(c[j]){if(cnt===occ){o[i]=s[j];break;}cnt++;}}}return o;}

  // ───────── سری/تاریخچه (معادلِ history operator) ─────────
  function ref(s,n){ s=arr(s); n=(n==null?1:n)|0; var o=new Array(N).fill(null); for(var i=0;i<N;i++){ var j=i-n; o[i]=(j>=0&&j<N)?s[j]:null; } return o; } // ref(close,1) ≡ close[1]
  // مولتی‌تایم‌فریم: تجمیعِ src به تایم‌فریمِ بالاتر (mult کندل) بدونِ look-ahead.
  // agg: 'last'(پیش‌فرض/close) | 'max'(high) | 'min'(low) | 'sum'(volume). مقدارِ بستهٔ کاملِ قبل را برمی‌گرداند.
  function security(mult,s,agg){ mult=Math.max(1,Math.round(mult||1)); s=arr(s); agg=agg||'last'; var o=new Array(N).fill(null); for(var i=0;i<N;i++){ var k=Math.floor(i/mult); if(k<1){o[i]=null;continue;} var st=(k-1)*mult, en=k*mult-1, v=null; if(agg==='last'){v=s[en];} else if(agg==='max'){v=-Infinity;for(var j=st;j<=en;j++)if(s[j]!=null)v=Math.max(v,s[j]);v=isFinite(v)?v:null;} else if(agg==='min'){v=Infinity;for(var j=st;j<=en;j++)if(s[j]!=null)v=Math.min(v,s[j]);v=isFinite(v)?v:null;} else if(agg==='sum'){v=0;for(var j=st;j<=en;j++)if(s[j]!=null)v+=s[j];} o[i]=v; } return o; }
  var request={ security:function(mult,s,agg){ return security(mult,s,agg); } };
  function naf(x){ var a=arr(x); var o=new Array(N); for(var i=0;i<N;i++)o[i]=(a[i]==null); return o; }              // na(x) → بولین (قبلاً un روی null short-circuit می‌کرد ⇒ na هرگز true نمی‌شد)
  function toint(a){ return un(a,function(x){return Math.trunc(x);}); }
  function tofloat(a){ return un(a,function(x){return +x;}); }
  function tobool(a){ return un(a,function(x){return !!x;}); }
  function cumf(s){ s=arr(s); var o=new Array(N),r=0; for(var i=0;i<N;i++){ if(s[i]!=null)r+=s[i]; o[i]=r; } return o; }
  function sumf(s,p){ s=arr(s); var o=new Array(N).fill(null),sum=0,cnt=0; for(var i=0;i<N;i++){ var v=s[i]; if(v!=null){sum+=v;cnt++;} if(i>=p&&s[i-p]!=null){sum-=s[i-p];cnt--;} if(i>=p-1)o[i]=sum; } return o; }
  // ───────── میانگین‌های پیشرفته ─────────
  function variance(s,p){ var sd=stdev(s,p); return un(sd,function(x){return x*x;}); }
  function swma(s){ s=arr(s); var o=new Array(N).fill(null); for(var i=3;i<N;i++){ if(s[i]==null||s[i-1]==null||s[i-2]==null||s[i-3]==null)continue; o[i]=s[i-3]/6+s[i-2]*2/6+s[i-1]*2/6+s[i]/6; } return o; }
  function alma(s,p,off,sig){ s=arr(s);off=(off==null?0.85:off);sig=sig||6; var m=off*(p-1),w=p/sig,o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var num=0,den=0,ok=1; for(var j=0;j<p;j++){ var x=s[i-(p-1-j)]; if(x==null){ok=0;break;} var ww=Math.exp(-((j-m)*(j-m))/(2*w*w)); num+=x*ww; den+=ww; } o[i]=ok&&den?num/den:null; } return o; }
  function linreg(s,p,off){ s=arr(s);off=off||0;var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var sx=0,sy=0,sxx=0,sxy=0,ok=1; for(var j=0;j<p;j++){ var y=s[i-j]; if(y==null){ok=0;break;} var x=p-1-j; sx+=x;sy+=y;sxx+=x*x;sxy+=x*y; } if(!ok)continue; var d=p*sxx-sx*sx; if(!d)continue; var b=(p*sxy-sx*sy)/d,a=(sy-b*sx)/p; o[i]=a+b*(p-1-off); } return o; }
  function dema(s,p){ var e=ema(s,p); return sub(mul(e,2),ema(e,p)); }
  function tema(s,p){ var e1=ema(s,p),e2=ema(e1,p),e3=ema(e2,p); return add(sub(mul(e1,3),mul(e2,3)),e3); }
  function trix(s,p){ return mul(roc(ema(ema(ema(s,p),p),p),1),1); }
  // ───────── اسیلاتورها ─────────
  function wpr(p){ p=p||14; var hh=highest(H,p),ll=lowest(L,p),o=new Array(N).fill(null); for(var i=0;i<N;i++){ if(hh[i]!=null){ o[i]=(hh[i]===ll[i])?-50:(hh[i]-C[i])/(hh[i]-ll[i])*-100; } } return o; }
  function mfi(p){ p=p||14; var tp=[],rmf=[],pos=new Array(N).fill(0),neg=new Array(N).fill(0); for(var i=0;i<N;i++){ tp[i]=(H[i]+L[i]+C[i])/3; rmf[i]=tp[i]*(V[i]||0); if(i>0){ if(tp[i]>tp[i-1])pos[i]=rmf[i]; else if(tp[i]<tp[i-1])neg[i]=rmf[i]; } } var sp=sumf(pos,p),sn=sumf(neg,p),o=new Array(N).fill(null); for(var i=0;i<N;i++) if(sp[i]!=null) o[i]=sn[i]?100-100/(1+sp[i]/sn[i]):100; return o; }
  function cmo(s,p){ s=arr(s);p=p||9; var up=new Array(N).fill(0),dn=new Array(N).fill(0); for(var i=1;i<N;i++){ var ch=s[i]-s[i-1]; if(ch>0)up[i]=ch; else dn[i]=-ch; } var su=sumf(up,p),sd=sumf(dn,p),o=new Array(N).fill(null); for(var i=0;i<N;i++) if(su[i]!=null){ var t=su[i]+sd[i]; o[i]=t?100*(su[i]-sd[i])/t:0; } return o; }
  function tsi(s,sh,lo){ s=arr(s);sh=sh||13;lo=lo||25; var m=change(s,1),dbl=ema(ema(m,lo),sh),ab=ema(ema(un(m,Math.abs),lo),sh),o=new Array(N).fill(null); for(var i=0;i<N;i++) if(dbl[i]!=null&&ab[i]) o[i]=100*dbl[i]/ab[i]; return o; }
  function ao(){ var mp=div(add(arr(H),arr(L)),2); return sub(sma(mp,5),sma(mp,34)); }
  function stochrsi(s,p,k,dd){ p=p||14;k=k||3;dd=dd||3; var r=rsi(s,p),hh=highest(r,p),ll=lowest(r,p),raw=new Array(N).fill(null); for(var i=0;i<N;i++){ if(hh[i]!=null) raw[i]=(hh[i]===ll[i])?0:100*(r[i]-ll[i])/(hh[i]-ll[i]); } var ks=sma(raw,k); return {k:ks,d:sma(ks,dd)}; }
  // ───────── روند/جهت ─────────
  function dmi(p,sm){ p=p||14;sm=sm||14; var pdm=new Array(N).fill(0),ndm=new Array(N).fill(0),trr=tr(); for(var i=1;i<N;i++){ var up=H[i]-H[i-1],dw=L[i-1]-L[i]; pdm[i]=(up>dw&&up>0)?up:0; ndm[i]=(dw>up&&dw>0)?dw:0; } var atrr=rma(trr,p),sp=rma(pdm,p),sn=rma(ndm,p),pdi=new Array(N).fill(null),ndi=new Array(N).fill(null),dx=new Array(N).fill(null); for(var i=0;i<N;i++){ if(atrr[i]){ pdi[i]=100*sp[i]/atrr[i]; ndi[i]=100*sn[i]/atrr[i]; var su=pdi[i]+ndi[i]; dx[i]=su?100*Math.abs(pdi[i]-ndi[i])/su:0; } } return {plus:pdi,minus:ndi,adx:rma(dx,sm)}; }
  function adxf(p,sm){ return dmi(p,sm).adx; }
  function aroon(p){ p=p||14; var u=new Array(N).fill(null),d=new Array(N).fill(null); for(var i=p;i<N;i++){ var hi=-Infinity,lo=Infinity,hb=0,lb=0; for(var j=0;j<=p;j++){ if(H[i-j]>hi){hi=H[i-j];hb=j;} if(L[i-j]<lo){lo=L[i-j];lb=j;} } u[i]=100*(p-hb)/p; d[i]=100*(p-lb)/p; } return {up:u,down:d}; }
  function sar(st,inc,mx){ st=st||0.02;inc=inc||0.02;mx=mx||0.2; var o=new Array(N).fill(null),trend=1,af=st,ep=H[0],sv=L[0]; for(var i=1;i<N;i++){ sv=sv+af*(ep-sv); if(trend>0){ if(L[i]<sv){trend=-1;sv=ep;ep=L[i];af=st;} else if(H[i]>ep){ep=H[i];af=Math.min(mx,af+inc);} } else { if(H[i]>sv){trend=1;sv=ep;ep=H[i];af=st;} else if(L[i]<ep){ep=L[i];af=Math.min(mx,af+inc);} } o[i]=sv; } return o; }
  function ichimoku(cl,bl,sp){ cl=cl||9;bl=bl||26;sp=sp||52; function dl(p){var o=new Array(N).fill(null);for(var i=p-1;i<N;i++){var hi=-Infinity,lo=Infinity;for(var j=0;j<p;j++){hi=Math.max(hi,H[i-j]);lo=Math.min(lo,L[i-j]);}o[i]=(hi+lo)/2;}return o;} var conv=dl(cl),base=dl(bl); return {conversion:conv,base:base,spanA:div(add(conv,base),2),spanB:dl(sp)}; }
  // ───────── نوسان/باند ─────────
  function bbw(s,p,m){ var b=bb(s,p,m); return div(mul(sub(b.upper,b.lower),100),b.mid); }
  function kc(s,p,m,useTR){ p=p||20;m=m||2; var mid=ema(s,p),rng=(useTR===false)?sub(arr(H),arr(L)):tr(),ra=rma(rng,p); return {mid:mid,upper:add(mid,mul(ra,m)),lower:sub(mid,mul(ra,m))}; }
  function kcw(s,p,m,useTR){ var k=kc(s,p,m,useTR); return div(mul(sub(k.upper,k.lower),100),k.mid); }
  function donchian(p){ p=p||20; var u=highest(H,p),l=lowest(L,p); return {upper:u,lower:l,basis:div(add(u,l),2)}; }
  // ───────── پیووت/ساختار/حجم ─────────
  function pivothigh(left,right){ left=left||5;right=right||5; var o=new Array(N).fill(null); for(var i=left;i<N-right;i++){ var v=H[i],ok=1,j; for(j=1;j<=left;j++)if(H[i-j]>=v){ok=0;break;} if(ok)for(j=1;j<=right;j++)if(H[i+j]>v){ok=0;break;} if(ok)o[i]=v; } return o; }
  function pivotlow(left,right){ left=left||5;right=right||5; var o=new Array(N).fill(null); for(var i=left;i<N-right;i++){ var v=L[i],ok=1,j; for(j=1;j<=left;j++)if(L[i-j]<=v){ok=0;break;} if(ok)for(j=1;j<=right;j++)if(L[i+j]<v){ok=0;break;} if(ok)o[i]=v; } return o; }
  function highestbars(s,p){ s=arr(s); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var m=-Infinity,b=0; for(var j=0;j<p;j++)if(s[i-j]!=null&&s[i-j]>m){m=s[i-j];b=j;} o[i]=-b; } return o; }
  function lowestbars(s,p){ s=arr(s); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var m=Infinity,b=0; for(var j=0;j<p;j++)if(s[i-j]!=null&&s[i-j]<m){m=s[i-j];b=j;} o[i]=-b; } return o; }
  function correlation(a,b,p){ a=arr(a);b=arr(b); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var sa=0,sb=0,saa=0,sbb=0,sab=0,ok=1; for(var j=0;j<p;j++){ var x=a[i-j],y=b[i-j]; if(x==null||y==null){ok=0;break;} sa+=x;sb+=y;saa+=x*x;sbb+=y*y;sab+=x*y; } if(!ok)continue; var dn=Math.sqrt((p*saa-sa*sa)*(p*sbb-sb*sb)); o[i]=dn?(p*sab-sa*sb)/dn:null; } return o; }
  function medianf(s,p){ s=arr(s); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var w=[],ok=1; for(var j=0;j<p;j++){ if(s[i-j]==null){ok=0;break;} w.push(s[i-j]); } if(!ok)continue; w.sort(function(x,y){return x-y;}); o[i]=w[Math.floor(p/2)]; } return o; }
  function obv(){ var o=new Array(N).fill(null),r=0; for(var i=0;i<N;i++){ if(i>0){ if(C[i]>C[i-1])r+=V[i]||0; else if(C[i]<C[i-1])r-=V[i]||0; } o[i]=r; } return o; }
  function ad(){ var o=new Array(N).fill(null),r=0; for(var i=0;i<N;i++){ var rng=H[i]-L[i],mfm=rng?((C[i]-L[i])-(H[i]-C[i]))/rng:0; r+=mfm*(V[i]||0); o[i]=r; } return o; }
  function cmf(p){ p=p||20; var mfv=new Array(N); for(var i=0;i<N;i++){ var rng=H[i]-L[i]; mfv[i]=(rng?((C[i]-L[i])-(H[i]-C[i]))/rng:0)*(V[i]||0); } var sm=sumf(mfv,p),sv=sumf(arr(V),p),o=new Array(N).fill(null); for(var i=0;i<N;i++) if(sm[i]!=null&&sv[i]) o[i]=sm[i]/sv[i]; return o; }
  // ───────── آمار/رتبه/توزیع (Pine ta.* غایب‌ها) ─────────
  function fixnanf(s){ s=arr(s); var o=new Array(N).fill(null),last=null; for(var i=0;i<N;i++){ if(s[i]!=null&&isFinite(s[i]))last=s[i]; o[i]=last; } return o; } // fixnan: پرکردنِ null با آخرین مقدارِ معتبر
  function rangef(s,p){ return sub(highest(s,p),lowest(s,p)); }                                  // range: بیشینه−کمینهٔ p کندل
  function cogf(s,p){ s=arr(s); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var num=0,den=0,ok=1; for(var j=0;j<p;j++){ var x=s[i-j]; if(x==null){ok=0;break;} num+=x*(j+1); den+=x; } o[i]=(ok&&den)?-num/den:null; } return o; } // center of gravity
  function runmax(s){ s=arr(s); var o=new Array(N).fill(null),m=-Infinity; for(var i=0;i<N;i++){ if(s[i]!=null&&isFinite(s[i]))m=Math.max(m,s[i]); o[i]=isFinite(m)?m:null; } return o; } // ta.max: بیشینهٔ تجمعی از ابتدای تاریخ
  function runmin(s){ s=arr(s); var o=new Array(N).fill(null),m=Infinity; for(var i=0;i<N;i++){ if(s[i]!=null&&isFinite(s[i]))m=Math.min(m,s[i]); o[i]=isFinite(m)?m:null; } return o; } // ta.min: کمینهٔ تجمعی
  function percentrankf(s,p){ s=arr(s); var o=new Array(N).fill(null); for(var i=p;i<N;i++){ var cur=s[i]; if(cur==null)continue; var cnt=0,tot=0,ok=1; for(var j=1;j<=p;j++){ var x=s[i-j]; if(x==null){ok=0;break;} tot++; if(x<=cur)cnt++; } o[i]=(ok&&tot)?100*cnt/tot:null; } return o; } // درصدِ p مقدارِ قبلی که ≤ فعلی‌اند
  function pctnrf(s,p,pct){ s=arr(s); pct=(pct==null?50:pct); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var w=[],ok=1; for(var j=0;j<p;j++){ var x=s[i-j]; if(x==null){ok=0;break;} w.push(x); } if(!ok)continue; w.sort(function(a,b){return a-b;}); var r=Math.ceil(pct/100*p); if(r<1)r=1; if(r>p)r=p; o[i]=w[r-1]; } return o; } // percentile (nearest rank)
  function pctlif(s,p,pct){ s=arr(s); pct=(pct==null?50:pct); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var w=[],ok=1; for(var j=0;j<p;j++){ var x=s[i-j]; if(x==null){ok=0;break;} w.push(x); } if(!ok)continue; w.sort(function(a,b){return a-b;}); var rk=pct/100*(p-1),lo=Math.floor(rk),hi=Math.ceil(rk); o[i]=w[lo]+(w[hi]-w[lo])*(rk-lo); } return o; } // percentile (linear interpolation)
  function modef(s,p){ s=arr(s); var o=new Array(N).fill(null); for(var i=p-1;i<N;i++){ var cnt={},best=null,bestc=0,ok=1; for(var j=0;j<p;j++){ var x=s[i-j]; if(x==null){ok=0;break;} cnt[x]=(cnt[x]||0)+1; if(cnt[x]>bestc||(cnt[x]===bestc&&x<best)){bestc=cnt[x];best=x;} } o[i]=ok?best:null; } return o; } // mode: پرتکرارترین (کوچک‌ترین در تساوی)

  var ta={sma:sma,ema:ema,rma:rma,smma:rma,wma:wma,hma:hma,swma:swma,alma:alma,linreg:linreg,dema:dema,tema:tema,trix:trix,vwma:vwma,vwap:vwap,stdev:stdev,dev:stdev,variance:variance,change:change,mom:mom,roc:roc,cum:cumf,sum:sumf,highest:highest,lowest:lowest,highestbars:highestbars,lowestbars:lowestbars,median:medianf,correlation:correlation,rsi:rsi,stochrsi:stochrsi,wpr:wpr,cmo:cmo,tsi:tsi,ao:ao,mfi:mfi,tr:tr,atr:atr,cci:cci,macd:macd,bb:bb,bbw:bbw,kc:kc,kcw:kcw,donchian:donchian,stoch:stoch,supertrend:supertrend,sar:sar,dmi:dmi,adx:adxf,aroon:aroon,ichimoku:ichimoku,obv:obv,ad:ad,cmf:cmf,crossover:crossover,crossunder:crossunder,cross:cross,rising:rising,falling:falling,barssince:barssince,valuewhen:valuewhen,pivothigh:pivothigh,pivotlow:pivotlow,fixnan:fixnanf,range:rangef,cog:cogf,max:runmax,min:runmin,percentrank:percentrankf,percentile_nearest_rank:pctnrf,percentile_linear_interpolation:pctlif,mode:modef};
  var math={abs:function(a){return un(a,Math.abs);},max:function(a,b){return bin(a,b,Math.max);},min:function(a,b){return bin(a,b,Math.min);},round:function(a){return un(a,Math.round);},sqrt:function(a){return un(a,Math.sqrt);},pow:function(a,b){return bin(a,b,Math.pow);},avg:function(a,b){return div(add(a,b),2);},floor:function(a){return un(a,Math.floor);},ceil:function(a){return un(a,Math.ceil);},sign:function(a){return un(a,Math.sign);},exp:function(a){return un(a,Math.exp);},log:function(a){return un(a,Math.log);},log10:function(a){return un(a,function(x){return Math.log(x)/Math.LN10;});},sin:function(a){return un(a,Math.sin);},cos:function(a){return un(a,Math.cos);},tan:function(a){return un(a,Math.tan);},asin:function(a){return un(a,Math.asin);},acos:function(a){return un(a,Math.acos);},atan:function(a){return un(a,Math.atan);},todegrees:function(a){return un(a,function(x){return x*180/Math.PI;});},toradians:function(a){return un(a,function(x){return x*Math.PI/180;});},round_to_mintick:function(a){return un(a,function(x){return Math.round(x*1e5)/1e5;});},sum:sumf,random:function(mn,mx){mn=(mn==null?0:mn);mx=(mx==null?1:mx);var o=new Array(N);for(var i=0;i<N;i++)o[i]=mn+Math.random()*(mx-mn);return o;},pi:Math.PI,e:Math.E,phi:1.618033988749895};

  var inputSeen={};
  function input(def,title,type,opts){ var key=title||('input'+inputs.length); if(!inputSeen[key]){inputSeen[key]=1;var decl={key:key,type:type||(typeof def==='boolean'?'bool':'float'),def:def};if(opts){if(opts.min!=null)decl.min=opts.min;if(opts.max!=null)decl.max=opts.max;if(opts.step!=null)decl.step=opts.step;if(opts.options)decl.options=opts.options;}inputs.push(decl);} var v=IN[key]; return (v===undefined||v===null)?def:v; }
  input.int=function(d,t,mn,mx,st){return input(d,t,'int',{min:mn,max:mx,step:st});};
  input.float=function(d,t,mn,mx,st){return input(d,t,'float',{min:mn,max:mx,step:st});};
  input.bool=function(d,t){return input(d,t,'bool');};
  input.string=function(d,t,opts){return input(d,t,'string',{options:opts});};
  input.color=function(d,t){return input(d,t,'color');};
  input.source=function(d,t){ var key=t||('input'+inputs.length); if(!inputSeen[key]){inputSeen[key]=1;inputs.push({key:key,type:'source',def:'close'});} return d; };

  function S(x){ return arr(x); }
  function plot(s,name,col,w,style){ s=arr(s); plots.push({name:name||('plot'+plots.length),color:col||'#3b82f6',width:w||2,style:style||0,data:s.map(function(v,i){return v==null?null:{time:T[i],value:v};}).filter(Boolean)}); }
  function plotshape(c,name,shp,col){ c=arr(c); var pts=[]; for(var i=0;i<N;i++){if(c[i])pts.push({time:T[i],price:(shp==='down'?H[i]:L[i]),shape:shp||'up'});} shapes.push({name:name||'shape',color:col||'#22c55e',shape:shp||'up',points:pts}); }
  function plotchar(c,name,col){ plotshape(c,name,'circle',col); }
  // plotarrow(series) — فلشِ بالا برای مثبت، پایین برای منفی
  function plotarrow(s,name,cu,cd){ s=arr(s); var up=[],dn=[]; for(var i=0;i<N;i++){ if(s[i]>0)up.push({time:T[i],price:L[i],shape:'up'}); else if(s[i]<0)dn.push({time:T[i],price:H[i],shape:'down'}); } if(up.length)shapes.push({name:(name||'فلش')+'↑',color:cu||'#22c55e',shape:'up',points:up}); if(dn.length)shapes.push({name:(name||'فلش')+'↓',color:cd||'#ef4444',shape:'down',points:dn}); }
  // plotcandle(o,h,l,c) — رسمِ کندلِ سفارشی (مثلِ هایکین/رنکو در اسکریپت)
  function plotcandle(o,h,l,c,name,col){ o=arr(o);h=arr(h);l=arr(l);c=arr(c); var data=[]; for(var i=0;i<N;i++){ if(o[i]==null||h[i]==null||l[i]==null||c[i]==null)continue; data.push({time:T[i],open:o[i],high:h[i],low:l[i],close:c[i]}); } candleplots.push({name:name||'کندل',color:col||null,data:data}); }
  // plotbar(o,h,l,c) — رسمِ میلهٔ OHLC سفارشی (معادلِ plotbar در Pine)؛ hint: bars=true برای رندرِ میله‌ای
  function plotbar(o,h,l,c,name,col){ o=arr(o);h=arr(h);l=arr(l);c=arr(c); var data=[]; for(var i=0;i<N;i++){ if(o[i]==null||h[i]==null||l[i]==null||c[i]==null)continue; data.push({time:T[i],open:o[i],high:h[i],low:l[i],close:c[i]}); } candleplots.push({name:name||'میله',color:col||null,bars:true,data:data}); }
  function hline(p,name,col){ hlines.push({price:p,title:name||'',color:col||'#94a3b8'}); }
  function bgcolor(c,col){ c=arr(c); var b=[]; for(var i=0;i<N;i++)if(c[i])b.push(T[i]); bgs.push({color:col||'rgba(59,130,246,.12)',bars:b}); }
  function xtime(x){ if(x==null)return T[N-1]; var i=Math.round(x); if(i>=0&&i<N)return T[i]; if(x>1e7)return x; return T[N-1]; }
  // fill(a, b, color) — ناحیهٔ پرشده بینِ دو سری (رندر در چارت)
  function fill(a,b,col){ a=arr(a);b=arr(b); fills.push({color:col||'rgba(59,130,246,.12)',a:a.map(function(v,i){return v==null?null:{time:T[i],value:v};}).filter(Boolean),b:b.map(function(v,i){return v==null?null:{time:T[i],value:v};}).filter(Boolean)}); }
  // line.new / box.new / table.new — آبجکت‌های رسمِ برنامه‌نویسی‌شده (x = ایندکسِ کندل یا زمان)
  var line={ new:function(x1,y1,x2,y2,col,w,style){ lines.push({x1:xtime(x1),y1:y1,x2:xtime(x2),y2:y2,color:col||'#3b82f6',width:w||1,style:style||0}); return lines.length-1; }, set_xy1:function(){}, set_xy2:function(){}, set_color:function(){}, set_width:function(){}, delete:function(id){ if(id!=null&&lines[id])lines[id]=null; } };
  var box={ new:function(l,t,r,bm,col,bg){ boxes.push({left:xtime(l),top:t,right:xtime(r),bottom:bm,color:col||'#3b82f6',bg:bg||'rgba(59,130,246,.08)'}); return boxes.length-1; }, set_top:function(){}, set_bottom:function(){}, delete:function(id){ if(id!=null&&boxes[id])boxes[id]=null; } };
  function table(){}
  table.new=function(){ var t={cells:[]}; tables.push(t); return t; };
  table.cell=function(t,c,r,txt,col){ if(t&&t.cells)t.cells.push({col:c|0,row:r|0,text:String(disp(txt)),color:col||null}); };
  table.cell_set_text=function(t,c,r,txt){ if(t&&t.cells)t.cells.push({col:c|0,row:r|0,text:String(disp(txt))}); };
  // alert(message[, freq]) پویا — یا alert(condition, message)
  function alert(a,b){ if(Array.isArray(a)){ var bars=[]; for(var i=0;i<N;i++)if(a[i])bars.push(T[i]); alerts.push({msg:b||'هشدار',bars:bars,dynamic:true}); } else { alerts.push({msg:String(a==null?'هشدار':a),bars:[T[N-1]],dynamic:true}); } }
  // ناحیهٔ ریسک/ریوارد: جعبهٔ سبزِ کم‌رنگ برای هدف‌ها (تا TP3) و جعبهٔ قرمزِ کم‌رنگ برای حد ضرر (SL)
  // riskreward(entry, sl, tp1[, tp2, tp3]) — TP2/TP3 اختیاری‌اند.
  function riskreward(entry,sl,tp1,tp2,tp3){ entry=arr(entry);sl=arr(sl);tp1=arr(tp1);tp2=arr(tp2);tp3=arr(tp3); var e=null,s=null,a=null,b=null,c=null; for(var i=N-1;i>=0;i--){ if(entry[i]!=null&&sl[i]!=null&&tp1[i]!=null){e=entry[i];s=sl[i];a=tp1[i];b=tp2[i];c=tp3[i];break;} } if(e!=null){ var tps=[a]; if(b!=null)tps.push(b); if(c!=null)tps.push(c); zones.push({entry:e,sl:s,tps:tps,tp:a,fromTime:T[Math.max(0,N-60)],toTime:T[N-1]}); } }
  // barcolor(color[, condition]) — رنگِ کندل‌ها (همه یا شرطی)
  function barcolor(col,c){ if(c===undefined){ barcolors.push({color:col,all:true}); return; } c=arr(c); var b=[]; for(var i=0;i<N;i++)if(c[i])b.push(T[i]); barcolors.push({color:col,bars:b}); }
  var label={ new:function(c,s,txt,col){ c=arr(c);s=arr(s); for(var i=0;i<N;i++)if(c[i])labels.push({time:T[i],price:s[i],text:txt||'',color:col||'#e5e7eb'}); } };
  function alertcondition(c,msg){ c=arr(c); var b=[]; for(var i=0;i<N;i++)if(c[i])b.push(T[i]); alerts.push({msg:msg||'هشدار',bars:b}); }

  var strategy={ entry:function(dir,c){ if(dir==='long'||dir==='buy')strat.long=arr(c); else strat.short=arr(c); strat.enabled=true; }, exit:function(dir,c){ if(dir==='long')strat.exitL=arr(c); else strat.exitS=arr(c); }, close:function(c){ strat.exitL=arr(c); strat.exitS=arr(c); } };
  function study(){} function indicator(){}

  function hexToRgba(hex,alpha){ hex=String(hex||'').replace('#',''); if(hex.length===3)hex=hex[0]+hex[0]+hex[1]+hex[1]+hex[2]+hex[2]; if(hex.length<6)return 'rgba(59,130,246,'+alpha+')'; var r=parseInt(hex.substr(0,2),16),g=parseInt(hex.substr(2,2),16),b=parseInt(hex.substr(4,2),16); return 'rgba('+r+','+g+','+b+','+alpha+')'; }
  var color={blue:'#3b82f6',red:'#ef4444',green:'#22c55e',orange:'#f59e0b',purple:'#a855f7',gray:'#94a3b8',white:'#e5e7eb',yellow:'#eab308',aqua:'#22d3ee',teal:'#14b8a6',black:'#000000',silver:'#cbd5e1',lime:'#84cc16',maroon:'#7f1d1d',navy:'#1e3a8a',fuchsia:'#d946ef',
    new:function(c,t){ if(t==null)return c; var a=Math.max(0,Math.min(100,t)); return hexToRgba(c,((100-a)/100)); },
    rgb:function(r,g,b,t){ var a=(t==null?0:Math.max(0,Math.min(100,t))); return 'rgba('+(r|0)+','+(g|0)+','+(b|0)+','+((100-a)/100)+')'; }};
  var shape={up:'up',down:'down',circle:'circle',cross:'cross',flag:'flag',triangleup:'up',triangledown:'down',arrowup:'up',arrowdown:'down',labelup:'up',labeldown:'down'};
  // str.* — توابعِ رشته
  var str={ tostring:function(a,fmt){ return String(disp(a)); }, tonumber:function(a){ return Array.isArray(a)?a.map(Number):Number(a); }, length:function(s){ return s==null?0:String(s).length; }, contains:function(s,t){ return String(s).indexOf(t)>=0; }, replace_all:function(s,t,r){ return String(s).split(t).join(r); }, split:function(s,d){ return String(s).split(d); }, upper:function(s){ return String(s).toUpperCase(); }, lower:function(s){ return String(s).toLowerCase(); }, startswith:function(s,t){ return String(s).indexOf(t)===0; }, endswith:function(s,t){ var x=String(s),y=String(t); return x.indexOf(y,x.length-y.length)!==-1; }, format:function(f){ var args=Array.prototype.slice.call(arguments,1),k=0; return String(f).replace(/\\{\\d+([^}]*)?\\}/g,function(){ var v=args[k++]; return String(disp(v)); }); } };
  var open=O,high=H,low=L,close=C,volume=V,time=T,hl2=[],hlc3=[],ohlc4=[],hlcc4=[],bar_index=[];
  for(var i=0;i<N;i++){hl2[i]=(H[i]+L[i])/2;hlc3[i]=(H[i]+L[i]+C[i])/3;ohlc4[i]=(O[i]+H[i]+L[i]+C[i])/4;hlcc4[i]=(H[i]+L[i]+C[i]+C[i])/4;bar_index[i]=i;}
  var last_bar_index=N-1;
  // barstate.* — هر کدام یک سری بولین (مدلِ سری‌محور)
  var barstate={isfirst:(function(){var o=new Array(N).fill(false);if(N)o[0]=true;return o;})(),islast:(function(){var o=new Array(N).fill(false);if(N)o[N-1]=true;return o;})(),isnew:arr(true),isconfirmed:(function(){var o=new Array(N).fill(true);if(N)o[N-1]=false;return o;})(),isrealtime:arr(false),ishistory:arr(true)};

  try {
    // ── پیش‌پردازنده: سازگاریِ نحویِ Pine ──
    // close[1] → ref(close,1) (عملگرِ تاریخچه) · var/varip حذف (در مدلِ vectorized یک‌بار اجرا) · := → =
    function preprocess(src){
      src = String(src);
      src = src.replace(/\\bvarip\\b/g, '').replace(/\\bvar\\b/g, '');
      src = src.replace(/:=/g, '=');
      // IDENT یا IDENT.prop به‌همراهِ [اندیس] → ref(IDENT, اندیس)  (اندیس: عدد یا شناسه)
      src = src.replace(/([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\[\\s*([0-9]+|[A-Za-z_$][\\w$]*)\\s*\\]/g, 'ref($1, $2)');
      return src;
    }
    var __SRC = preprocess(d.source);
    var f = new Function('ta','math','input','plot','plotshape','plotchar','plotarrow','plotcandle','plotbar','hline','bgcolor','fill','riskreward','barcolor','label','alertcondition','alert','line','box','table','strategy','study','indicator','crossover','crossunder','cross','nz','na','iff','ref','security','request','int','float','bool','add','sub','mul','div','gt','lt','ge','le','and','or','color','shape','open','high','low','close','volume','time','hl2','hlc3','ohlc4','hlcc4','bar_index','last_bar_index','barstate','str','S', __SRC);
    f(ta,math,input,plot,plotshape,plotchar,plotarrow,plotcandle,plotbar,hline,bgcolor,fill,riskreward,barcolor,label,alertcondition,alert,line,box,table,strategy,study,indicator,crossover,crossunder,cross,nz,naf,iff,ref,security,request,toint,tofloat,tobool,add,sub,mul,div,gt,lt,ge,le,and,or,color,shape,open,high,low,close,volume,time,hl2,hlc3,ohlc4,hlcc4,bar_index,last_bar_index,barstate,str,S);

    var stratResult=null;
    if(strat.enabled){
      var comm=+(IN.__comm||0), slip=+(IN.__slip||0), cost=comm+slip; // کارمزد+اسلیپیج به ازای هر معامله
      var pos=0,entry=0,eq=0,peak=0,dd=0,wins=0,n=0,gp=0,gl=0,trades=[];
      function rec(r,t,dir){ r-=cost; eq+=r; n++; if(r>=0){wins++;gp+=r;}else gl+=-r; trades.push({t:t,r:r,dir:dir}); peak=Math.max(peak,eq); dd=Math.max(dd,peak-eq); }
      for(var i=1;i<N;i++){
        if(pos===0){ if(strat.long&&strat.long[i]){pos=1;entry=C[i];} else if(strat.short&&strat.short[i]){pos=-1;entry=C[i];} }
        else if(pos===1){ var ex=(strat.short&&strat.short[i])||(strat.exitL&&strat.exitL[i]); if(ex){rec(C[i]-entry,T[i],1);pos=(strat.short&&strat.short[i])?-1:0;if(pos===-1)entry=C[i];} }
        else if(pos===-1){ var ex2=(strat.long&&strat.long[i])||(strat.exitS&&strat.exitS[i]); if(ex2){rec(entry-C[i],T[i],-1);pos=(strat.long&&strat.long[i])?1:0;if(pos===1)entry=C[i];} }
      }
      var equity=[],run=0; trades.forEach(function(tr){run+=tr.r;equity.push({t:tr.t,e:run});});
      // متریک‌های پیشرفته
      var wArr=trades.filter(function(t){return t.r>=0;}).map(function(t){return t.r;});
      var lArr=trades.filter(function(t){return t.r<0;}).map(function(t){return t.r;});
      var sum=function(a){return a.reduce(function(x,y){return x+y;},0);};
      var avgW=wArr.length?sum(wArr)/wArr.length:0, avgL=lArr.length?sum(lArr)/lArr.length:0;
      var maxW=wArr.length?Math.max.apply(null,wArr):0, maxL=lArr.length?Math.min.apply(null,lArr):0;
      var longN=trades.filter(function(t){return t.dir===1;}).length, shortN=n-longN;
      var ws=0,ls=0,wsMax=0,lsMax=0; trades.forEach(function(t){ if(t.r>=0){ws++;ls=0;wsMax=Math.max(wsMax,ws);} else {ls++;ws=0;lsMax=Math.max(lsMax,ls);} });
      var avgTrade=n?eq/n:0, expectancy=n?(wins/n)*avgW+((n-wins)/n)*avgL:0;
      var mean=avgTrade, varc=0; trades.forEach(function(t){varc+=(t.r-mean)*(t.r-mean);}); var sd=n>1?Math.sqrt(varc/(n-1)):0; var sharpe=sd?(mean/sd)*Math.sqrt(Math.max(1,n)):0;
      stratResult={trades:n,net:eq,win:n?Math.round(wins/n*100):0,pf:gl?(gp/gl):(gp>0?99:0),dd:dd,equity:equity,list:trades.slice(-50),
        avgWin:avgW,avgLoss:avgL,maxWin:maxW,maxLoss:maxL,longN:longN,shortN:shortN,winStreak:wsMax,lossStreak:lsMax,avgTrade:avgTrade,expectancy:expectancy,sharpe:sharpe,grossProfit:gp,grossLoss:gl,wins:wins,losses:n-wins};
    }
    self.postMessage({ok:true,plots:plots,shapes:shapes,hlines:hlines,bgs:bgs,labels:labels,alerts:alerts,inputs:inputs,zones:zones,fills:fills,lines:lines.filter(Boolean),boxes:boxes.filter(Boolean),tables:tables,barcolors:barcolors,candleplots:candleplots,strategy:stratResult});
  } catch (err) {
    self.postMessage({ok:false,error:String(err&&err.message||err)});
  }
};
`;

let _url = null;
function workerUrl() { if (!_url) _url = URL.createObjectURL(new Blob([WORKER_SRC], { type: 'application/javascript' })); return _url; }

export function runScript(source, candles, inputs = {}, timeoutMs = 5000) {
  return new Promise((resolve) => {
    let done = false;
    const w = new Worker(workerUrl());
    const to = setTimeout(() => { if (done) return; done = true; w.terminate(); resolve({ ok: false, error: 'اجرای اسکریپت بیش از حد طول کشید (حلقهٔ بی‌نهایت؟).' }); }, timeoutMs);
    w.onmessage = (e) => { if (done) return; done = true; clearTimeout(to); w.terminate(); resolve(e.data); };
    w.onerror = (e) => { if (done) return; done = true; clearTimeout(to); w.terminate(); resolve({ ok: false, error: String(e.message || 'خطای اجرا') }); };
    w.postMessage({
      source, inputs,
      time: candles.map((c) => c.t), open: candles.map((c) => c.o), high: candles.map((c) => c.h),
      low: candles.map((c) => c.l), close: candles.map((c) => c.c), volume: candles.map((c) => c.v || 0),
    });
  });
}
