// نمااسکریپت — موتورِ اجرای «کندل‌به‌کندل» (Pine-style bar-by-bar VM) در Web Worker سندباکس.
// برخلافِ namascript.js (مدلِ سری‌محور/vectorized که کلِ بدنه را یک‌بار روی آرایه اجرا می‌کند)،
// این موتور بدنهٔ کاربر را برای «هر کندل» یک‌بار از چپ‌به‌راست اجرا می‌کند و معناشناسیِ واقعیِ Pine را می‌دهد:
//   • var/varip  → یک‌بار مقداردهی و در همهٔ کندل‌ها پایدار (persistent state)
//   • x := expr  → انتساب درون‌کندلی به متغیرِ پایدار (متمایز از تعریفِ var)
//   • close[1] / expr[n] → دسترسیِ تاریخچه به مقدارِ n کندلِ قبل (history operator)
//   • barstate.* → وضعیتِ واقعیِ کندل (isfirst/islast/isnew/isconfirmed/ishistory)
//   • na / nz   → مدیریتِ مقدارِ تهی (null)
// خروجی دقیقاً هم‌شکلِ runScript است (همان سطل‌ها: plots/shapes/.../strategy) تا «جایگزینِ drop-in» باشد.
// خودکفاست: کمک‌توابعِ کوچک عمداً اینجا تکرار شده‌اند تا به namascript.js وابسته نباشد.

const WORKER_SRC = `
self.onmessage = function (e) {
  var d = e.data, O=d.open,H=d.high,L=d.low,C=d.close,V=d.volume,T=d.time, N=C.length, IN=d.inputs||{};
  var plots=[], shapes=[], hlines=[], bgs=[], labels=[], alerts=[], inputs=[], zones=[], fills=[], lines=[], boxes=[], tables=[], barcolors=[], candleplots=[], strat={enabled:false,longBars:[],shortBars:[],exitLBars:[],exitSBars:[]};

  // ───────── NA / مدیریتِ تهی (مقدارِ تهی = null) ─────────
  function isna(x){ return x==null || (typeof x==='number' && !isFinite(x)); }
  function naf(x){ return isna(x); }                       // na(x) → بولین
  function nz(x,r){ return isna(x)?(r==null?0:r):x; }
  function num(x){ return isna(x)?null:+x; }
  function toint(x){ return isna(x)?null:Math.trunc(x); }
  function tofloat(x){ return isna(x)?null:+x; }
  function tobool(x){ return !!x && !isna(x); }
  function disp(v){ if(typeof v==='number'&&isFinite(v)){ var a=Math.abs(v),dd=a>=100?2:a>=1?3:5,m=Math.pow(10,dd); return Math.round(v*m)/m; } return v==null?'':v; }
  function xtime(x){ if(x==null)return T[bar]; var i=Math.round(x); if(i>=0&&i<N)return T[i]; if(x>1e7)return x; return T[bar]; }
  function hexToRgba(hex,alpha){ hex=String(hex||'').replace('#',''); if(hex.length===3)hex=hex[0]+hex[0]+hex[1]+hex[1]+hex[2]+hex[2]; if(hex.length<6)return 'rgba(59,130,246,'+alpha+')'; var r=parseInt(hex.substr(0,2),16),g=parseInt(hex.substr(2,2),16),b=parseInt(hex.substr(4,2),16); return 'rgba('+r+','+g+','+b+','+alpha+')'; }

  // ───────── موتورِ سری: هر سری یک Series با حافظهٔ تاریخچه است ─────────
  // مقدارِ هر کندل در hist[i] نگه‌داشته می‌شود؛ s.get(n) ⇒ مقدارِ n کندلِ قبل (history operator).
  var ALL_SERIES = [];
  function Series(){ this.hist = new Array(N); this.cur = null; }
  Series.prototype.set = function(v){ this.cur = isna(v)?null:v; };       // مقدارِ کندلِ جاری
  Series.prototype.get = function(n){ n=(n==null?0:n)|0; var j=bar-n; return (j>=0&&j<N)?(j===bar?this.cur:this.hist[j]):null; };
  Series.prototype.commit = function(){ this.hist[bar]=this.cur; };       // پایانِ کندل: ثبت در تاریخچه
  function newSeries(){ var s=new Series(); ALL_SERIES.push(s); return s; }

  // ───────── حالتِ پایدارِ توابعِ ta.* (هر call-site حالتِ خودش را دارد) ─────────
  // در Pine هر فراخوانیِ ta.sma(...) حالتِ غلتانِ مستقل دارد؛ ما به هر call-site یک کلیدِ پایدار می‌دهیم.
  var TA_STATE = {};
  function stkey(name){ TA_STATE.__c=(TA_STATE.__c||{}); var k=name+'@'+(TA_STATE.__site||0); return k; }
  function st(name){ var k=stkey(name); return TA_STATE[k]||(TA_STATE[k]={}); }

  // ────────────────────────────────────────────────────────────────────────────
  // ta.* — نسخهٔ «کندل‌به‌کندل»: هر تابع روی کندلِ جاری یک اسکالر می‌دهد و حالتش را در st(...) نگه می‌دارد.
  // برای پنجره‌های غلتان از بافرِ حلقوی استفاده می‌کنیم؛ بازگشتی‌ها (ema/rma) حالتِ قبلی را به‌خاطر دارند.
  // ────────────────────────────────────────────────────────────────────────────
  function pushWin(s,v,p){ s.b=s.b||[]; s.b.push(v); if(s.b.length>p)s.b.shift(); return s.b; }
  function sma(src,p){ var s=st('sma'); var b=pushWin(s,src,p); if(b.length<p)return null; var sum=0; for(var i=0;i<b.length;i++){ if(isna(b[i]))return null; sum+=b[i]; } return sum/p; }
  function ema(src,p){ var s=st('ema'); if(isna(src))return s.pr==null?null:s.pr; var k=2/(p+1); s.pr=(s.pr==null)?src:src*k+s.pr*(1-k); s.n=(s.n||0)+1; return s.n>=p?s.pr:null; }
  function rma(src,p){ var s=st('rma'); if(isna(src))return s.pr==null?null:s.pr; s.pr=(s.pr==null)?src:(s.pr*(p-1)+src)/p; s.n=(s.n||0)+1; return s.n>=p?s.pr:null; }
  function wma(src,p){ var s=st('wma'); var b=pushWin(s,src,p); if(b.length<p)return null; var x=0,dn=p*(p+1)/2; for(var j=0;j<p;j++){ if(isna(b[p-1-j]))return null; x+=b[p-1-j]*(p-j); } return x/dn; }
  function stdev(src,p){ var s=st('stdev'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=0,i; for(i=0;i<p;i++){ if(isna(b[i]))return null; m+=b[i]; } m/=p; var v=0; for(i=0;i<p;i++){ var dd=b[i]-m; v+=dd*dd; } return Math.sqrt(v/p); }
  function variance(src,p){ var v=stdev(src,p); return v==null?null:v*v; }
  function highest(src,p){ var s=st('highest'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=-Infinity; for(var i=0;i<p;i++){ if(isna(b[i]))return null; if(b[i]>m)m=b[i]; } return isFinite(m)?m:null; }
  function lowest(src,p){ var s=st('lowest'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=Infinity; for(var i=0;i<p;i++){ if(isna(b[i]))return null; if(b[i]<m)m=b[i]; } return isFinite(m)?m:null; }
  function highestbars(src,p){ var s=st('highestbars'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=-Infinity,bk=0; for(var i=0;i<p;i++){ var idx=p-1-i; if(isna(b[idx]))return null; if(b[idx]>m){m=b[idx];bk=i;} } return -bk; }
  function lowestbars(src,p){ var s=st('lowestbars'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=Infinity,bk=0; for(var i=0;i<p;i++){ var idx=p-1-i; if(isna(b[idx]))return null; if(b[idx]<m){m=b[idx];bk=i;} } return -bk; }
  function sumf(src,p){ var s=st('sum'); var b=pushWin(s,src,p); if(b.length<p)return null; var x=0; for(var i=0;i<p;i++){ if(isna(b[i]))return null; x+=b[i]; } return x; }
  function cumf(src){ var s=st('cum'); if(s.r==null)s.r=0; if(!isna(src))s.r+=src; return s.r; }
  function change(src,n){ var s=st('change'); n=n||1; s.b=s.b||[]; s.b.push(src); if(s.b.length>n+1)s.b.shift(); if(s.b.length<n+1)return null; var a=s.b[s.b.length-1-n]; return (isna(src)||isna(a))?null:src-a; }
  function mom(src,n){ return change(src,n); }
  function roc(src,n){ var s=st('roc'); n=n||1; s.b=s.b||[]; s.b.push(src); if(s.b.length>n+1)s.b.shift(); if(s.b.length<n+1)return null; var a=s.b[s.b.length-1-n]; return a?((src-a)/a)*100:null; }
  function trf(){ var s=st('tr'); var pc=s.pc; s.pc=C[bar]; if(pc==null)return H[bar]-L[bar]; return Math.max(H[bar]-L[bar],Math.abs(H[bar]-pc),Math.abs(L[bar]-pc)); }
  function atr(p){ p=p||14; var s=st('atr'); var t=trf(); if(isna(t))return s.pr==null?null:s.pr; s.pr=(s.pr==null)?t:(s.pr*(p-1)+t)/p; s.n=(s.n||0)+1; return s.n>=p?s.pr:null; }
  function rsi(src,p){ p=p||14; var s=st('rsi'); if(s.prev==null){ s.prev=src; s.n=0; return null; } var ch=src-s.prev; s.prev=src; var g=Math.max(ch,0),l=Math.max(-ch,0); s.n=(s.n||0)+1; if(s.n<=p){ s.ag=(s.ag||0)+g; s.al=(s.al||0)+l; if(s.n===p){ s.ag/=p; s.al/=p; return 100-100/(1+s.ag/(s.al||1e-9)); } return null; } s.ag=(s.ag*(p-1)+g)/p; s.al=(s.al*(p-1)+l)/p; return 100-100/(1+s.ag/(s.al||1e-9)); }
  function cci(p){ p=p||20; var tp=(H[bar]+L[bar]+C[bar])/3; var s=st('cci'); var b=pushWin(s,tp,p); if(b.length<p)return null; var m=0,i; for(i=0;i<p;i++)m+=b[i]; m/=p; var md=0; for(i=0;i<p;i++)md+=Math.abs(b[i]-m); md/=p; return md?(tp-m)/(0.015*md):0; }
  function crossover(a,b){ var s=st('crossover'); var pa=s.pa,pb=s.pb; s.pa=a; s.pb=b; if(isna(a)||isna(b)||isna(pa)||isna(pb))return false; return pa<=pb && a>b; }
  function crossunder(a,b){ var s=st('crossunder'); var pa=s.pa,pb=s.pb; s.pa=a; s.pb=b; if(isna(a)||isna(b)||isna(pa)||isna(pb))return false; return pa>=pb && a<b; }
  function crossf(a,b){ var s=st('cross'); var pa=s.pa,pb=s.pb; s.pa=a; s.pb=b; if(isna(a)||isna(b)||isna(pa)||isna(pb))return false; return (pa<=pb&&a>b)||(pa>=pb&&a<b); }
  function rising(src,n){ var s=st('rising'); s.b=s.b||[]; s.b.push(src); if(s.b.length>n+1)s.b.shift(); if(s.b.length<n+1)return false; for(var j=0;j<n;j++)if(!(s.b[s.b.length-1-j]>s.b[s.b.length-2-j]))return false; return true; }
  function falling(src,n){ var s=st('falling'); s.b=s.b||[]; s.b.push(src); if(s.b.length>n+1)s.b.shift(); if(s.b.length<n+1)return false; for(var j=0;j<n;j++)if(!(s.b[s.b.length-1-j]<s.b[s.b.length-2-j]))return false; return true; }
  function barssince(cond){ var s=st('barssince'); if(cond)s.k=0; else if(s.k!=null)s.k++; return s.k==null?null:s.k; }
  function valuewhen(cond,src,occ){ occ=occ||0; var s=st('valuewhen'); s.list=s.list||[]; if(cond)s.list.unshift(src); return s.list.length>occ?s.list[occ]:null; }
  function macd(src,f,sl,sg){ f=f||12;sl=sl||26;sg=sg||9; var s=st('macd'); var ef=emaLocal(s,'ef',src,f), es=emaLocal(s,'es',src,sl); var ml=(ef==null||es==null)?null:ef-es; var sig=emaLocal(s,'sig',ml,sg); return {macd:ml,signal:sig,hist:(ml==null||sig==null)?null:ml-sig}; }
  function bb(src,p,m){ p=p||20;m=m||2; var mid=sma(src,p),sd=stdevSite(src,p); return {mid:mid,upper:(mid==null||sd==null)?null:mid+sd*m,lower:(mid==null||sd==null)?null:mid-sd*m}; }
  function stoch(p,dd){ p=p||14;dd=dd||3; var hh=highestSite(H[bar],p),ll=lowestSite(L[bar],p); var s=st('stoch'); var k=null; if(hh!=null)k=(hh===ll)?50:((C[bar]-ll)/(hh-ll))*100; var dv=smaLocal(s,'d',k,dd); return {k:k,d:dv}; }
  // کمک‌توابعِ محلیِ ema/sma/stdev/highest/lowest برای مرکّب‌ها (هر کلید درونِ یک حالتِ مشترک)
  function emaLocal(s,key,src,p){ var c=s[key]||(s[key]={}); if(isna(src))return c.pr==null?null:c.pr; var k=2/(p+1); c.pr=(c.pr==null)?src:src*k+c.pr*(1-k); c.n=(c.n||0)+1; return c.n>=p?c.pr:null; }
  function smaLocal(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(c.b.length<p)return null; var sum=0; for(var i=0;i<p;i++){ if(isna(c.b[i]))return null; sum+=c.b[i]; } return sum/p; }
  function stdevSite(src,p){ var s=st('stdevSite'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=0,i; for(i=0;i<p;i++){ if(isna(b[i]))return null; m+=b[i]; } m/=p; var v=0; for(i=0;i<p;i++){ var dd=b[i]-m; v+=dd*dd; } return Math.sqrt(v/p); }
  function highestSite(src,p){ var s=st('highestSite'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=-Infinity; for(var i=0;i<p;i++){ if(isna(b[i]))return null; if(b[i]>m)m=b[i]; } return m; }
  function lowestSite(src,p){ var s=st('lowestSite'); var b=pushWin(s,src,p); if(b.length<p)return null; var m=Infinity; for(var i=0;i<p;i++){ if(isna(b[i]))return null; if(b[i]<m)m=b[i]; } return m; }

  // ────────────────────────────────────────────────────────────────────────────
  // ta.* پیچیده — نسخهٔ «کندل‌به‌کندل» با حالتِ پایدارِ هر call-site (st('...')).
  // فرمول‌ها دقیقاً مثلِ موتورِ vectorized (namascript.js) تا خروجی هم‌مقدار باشد.
  // ────────────────────────────────────────────────────────────────────────────

  // supertrend(p,m) → {line,dir} — معادلِ خط ۵۰ namascript.js (band-clamp + flip).
  // حالت: a(atr) جدا، up/dn باندهای کلمپ‌شده، d جهت، pc کلوزِ کندلِ قبل (C[i-1]).
  function trLocal(s){ var pc=s.__tpc; s.__tpc=C[bar]; if(pc==null)return H[bar]-L[bar]; return Math.max(H[bar]-L[bar],Math.abs(H[bar]-pc),Math.abs(L[bar]-pc)); }
  function atrLocal(s,p){ var t=trLocal(s); if(isna(t))return s.__apr==null?null:s.__apr; s.__apr=(s.__apr==null)?t:(s.__apr*(p-1)+t)/p; s.__an=(s.__an||0)+1; return s.__an>=p?s.__apr:null; }
  function supertrend(p,m){ p=p||10;m=m||3; var s=st('supertrend'); var a=atrLocal(s,p); if(a==null){ return {line:null,dir:null}; } var mid=(H[bar]+L[bar])/2,ub=mid+m*a,lb=mid-m*a; var pUp=s.up,pDn=s.dn,pc=(bar>0?C[bar-1]:null); var d=(s.d==null)?1:s.d; if(pUp!=null&&pc!=null)ub=pc>pUp?Math.max(ub,pUp):ub; if(pDn!=null&&pc!=null)lb=pc<pDn?Math.min(lb,pDn):lb; if(d===1&&C[bar]<(pDn==null?lb:pDn))d=-1; else if(d===-1&&C[bar]>(pUp==null?ub:pUp))d=1; s.up=ub; s.dn=lb; s.d=d; return {line:(d===1?lb:ub),dir:d}; }

  // vwap() — VWAP تجمعی از کندلِ صفر (typical price)؛ معادلِ خط ۳۶ namascript.js.
  function vwap(){ var s=st('vwap'); if(s.pv==null){ s.pv=0; s.vv=0; } var tp=(H[bar]+L[bar]+C[bar])/3,vol=(V[bar]||0); s.pv+=tp*vol; s.vv+=vol; return s.vv?s.pv/s.vv:C[bar]; }

  // ichimoku(cl,bl,sp) → {conversion,base,spanA,spanB} — donchian-mid، معادلِ خط ۹۱.
  // برای هر دوره یک پنجرهٔ غلتانِ H/L نگه می‌داریم (همان حالتِ مشترکِ call-site).
  function donMid(s,key,p){ var c=s[key]||(s[key]={bh:[],bl:[]}); c.bh.push(H[bar]); c.bl.push(L[bar]); if(c.bh.length>p){c.bh.shift();c.bl.shift();} if(c.bh.length<p)return null; var hi=-Infinity,lo=Infinity; for(var i=0;i<p;i++){ if(c.bh[i]>hi)hi=c.bh[i]; if(c.bl[i]<lo)lo=c.bl[i]; } return (hi+lo)/2; }
  function ichimoku(cl,bl,sp){ cl=cl||9;bl=bl||26;sp=sp||52; var s=st('ichimoku'); var conv=donMid(s,'c',cl),base=donMid(s,'b',bl),spB=donMid(s,'s',sp); var spA=(conv==null||base==null)?null:(conv+base)/2; return {conversion:conv,base:base,spanA:spA,spanB:spB}; }

  // pivothigh(left,right)/pivotlow — معادلِ خط ۹۸/۹۹ namascript.js.
  // در حالتِ کندل‌به‌کندل پیووت «right کندل بعد» تأیید می‌شود (مثلِ Pine): پنجرهٔ
  // left+right+1 نگه می‌داریم و کاندید را در موقعیتِ right-از-آخر می‌سنجیم. مقدارِ پیووت
  // در همان کندلِ تأیید برمی‌گردد (نه null) و در غیرِ این صورت null.
  function pivothigh(left,right){ left=left||5;right=right||5; var s=st('pivothigh'); s.b=s.b||[]; s.b.push(H[bar]); var need=left+right+1; if(s.b.length>need)s.b.shift(); if(s.b.length<need)return null; var v=s.b[left],ok=1,j; for(j=1;j<=left;j++)if(s.b[left-j]>=v){ok=0;break;} if(ok)for(j=1;j<=right;j++)if(s.b[left+j]>v){ok=0;break;} return ok?v:null; }
  function pivotlow(left,right){ left=left||5;right=right||5; var s=st('pivotlow'); s.b=s.b||[]; s.b.push(L[bar]); var need=left+right+1; if(s.b.length>need)s.b.shift(); if(s.b.length<need)return null; var v=s.b[left],ok=1,j; for(j=1;j<=left;j++)if(s.b[left-j]<=v){ok=0;break;} if(ok)for(j=1;j<=right;j++)if(s.b[left+j]<v){ok=0;break;} return ok?v:null; }

  // ────────────────────────────────────────────────────────────────────────────
  // کمک‌توابعِ محلیِ keyed (هر کلید درونِ یک حالتِ مشترکِ call-site یک فیلترِ مستقل)
  // برای مرکّب‌ها (dema/tema/trix/...) که خروجیِ یک فیلتر را به فیلترِ دیگر می‌دهند.
  // ────────────────────────────────────────────────────────────────────────────
  // rmaLocal — معادلِ rma در namascript.js (خط ۳۲): nullها رد می‌شوند (pr حفظ) و خروجی با گیتِ
  // ایندکسِ مطلقِ کندل (bar>=p-1) باز می‌شود، نه با شمارش؛ پس در زنجیره (ورودیِ nullدارِ ابتدایی مثلِ DX در ADX)
  // دقیقاً با vectorized هم‌مقدار/هم‌تراز است.
  function rmaLocal(s,key,src,p){ var c=s[key]||(s[key]={}); if(!isna(src)){ c.pr=(c.pr==null)?src:(c.pr*(p-1)+src)/p; } return (bar>=p-1&&c.pr!=null)?c.pr:null; }
  function wmaLocal(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(c.b.length<p)return null; var x=0,dn=p*(p+1)/2; for(var j=0;j<p;j++){ if(isna(c.b[p-1-j]))return null; x+=c.b[p-1-j]*(p-j); } return x/dn; }
  function sumLocal(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(c.b.length<p)return null; var x=0; for(var i=0;i<p;i++){ if(isna(c.b[i]))return null; x+=c.b[i]; } return x; }
  function highLocal(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(c.b.length<p)return null; var m=-Infinity; for(var i=0;i<p;i++){ if(isna(c.b[i]))return null; if(c.b[i]>m)m=c.b[i]; } return isFinite(m)?m:null; }
  function lowLocal(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(c.b.length<p)return null; var m=Infinity; for(var i=0;i<p;i++){ if(isna(c.b[i]))return null; if(c.b[i]<m)m=c.b[i]; } return isFinite(m)?m:null; }
  // highChain/lowChain — معادلِ highest/lowest در namascript.js (خط ۴۱/۴۲): درونِ پنجره nullها رد می‌شوند
  // و خروجی با گیتِ ایندکسِ مطلقِ کندل (bar>=p-1) باز می‌شود (اگر همهٔ پنجره null باشد ⇒ null). برای زنجیره‌ها
  // (مثلِ highest(rsi,...) در stochrsi) که ورودیِ nullِ ابتدایی دارند تا با vectorized هم‌تراز بماند.
  function highChain(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(bar<p-1)return null; var m=-Infinity; for(var i=0;i<c.b.length;i++){ if(!isna(c.b[i])&&c.b[i]>m)m=c.b[i]; } return isFinite(m)?m:null; }
  function lowChain(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(bar<p-1)return null; var m=Infinity; for(var i=0;i<c.b.length;i++){ if(!isna(c.b[i])&&c.b[i]<m)m=c.b[i]; } return isFinite(m)?m:null; }
  function changeLocal(s,key,src,n){ var c=s[key]||(s[key]={b:[]}); n=n||1; c.b.push(src); if(c.b.length>n+1)c.b.shift(); if(c.b.length<n+1)return null; var a=c.b[c.b.length-1-n]; return (isna(src)||isna(a))?null:src-a; }
  function rocLocal(s,key,src,n){ var c=s[key]||(s[key]={b:[]}); n=n||1; c.b.push(src); if(c.b.length>n+1)c.b.shift(); if(c.b.length<n+1)return null; var a=c.b[c.b.length-1-n]; return a?((src-a)/a)*100:null; }
  function rsiLocal(s,key,src,p){ var c=s[key]||(s[key]={}); p=p||14; if(c.prev==null){ c.prev=src; c.n=0; return null; } var ch=src-c.prev; c.prev=src; var g=Math.max(ch,0),l=Math.max(-ch,0); c.n=(c.n||0)+1; if(c.n<=p){ c.ag=(c.ag||0)+g; c.al=(c.al||0)+l; if(c.n===p){ c.ag/=p; c.al/=p; return 100-100/(1+c.ag/(c.al||1e-9)); } return null; } c.ag=(c.ag*(p-1)+g)/p; c.al=(c.al*(p-1)+l)/p; return 100-100/(1+c.ag/(c.al||1e-9)); }

  // ───────── میانگین‌های پیشرفته (معادلِ خط ۳۴/۷۳–۷۸ namascript.js) ─────────
  // hma — wma(p/2) و wma(p) → df=2a-b، سپس wma(df, round(sqrt(p))). df==null ⇒ خروجی null.
  function hma(src,p){ var s=st('hma'); var h=Math.max(1,Math.floor(p/2)),sq=Math.max(1,Math.round(Math.sqrt(p))); var a=wmaLocal(s,'a',src,h),b=wmaLocal(s,'b',src,p); var df=(a==null||b==null)?null:2*a-b; var w=wmaLocal(s,'w',df==null?0:df,sq); return df==null?null:w; }
  // swma — وزن‌های [1/6,2/6,2/6,1/6] روی s[i-3..i] (پنجرهٔ ۴ کندل).
  function swma(src){ var s=st('swma'); s.b=s.b||[]; s.b.push(src); if(s.b.length>4)s.b.shift(); if(s.b.length<4)return null; var b=s.b; if(isna(b[0])||isna(b[1])||isna(b[2])||isna(b[3]))return null; return b[0]/6+b[1]*2/6+b[2]*2/6+b[3]/6; }
  // alma — وزن‌های گاوسی روی پنجرهٔ p (offset/sigma).
  function alma(src,p,off,sig){ off=(off==null?0.85:off);sig=sig||6; var s=st('alma'); s.b=s.b||[]; s.b.push(src); if(s.b.length>p)s.b.shift(); if(s.b.length<p)return null; var m=off*(p-1),w=p/sig,num=0,den=0; for(var j=0;j<p;j++){ var x=s.b[j]; if(isna(x))return null; var ww=Math.exp(-((j-m)*(j-m))/(2*w*w)); num+=x*ww; den+=ww; } return den?num/den:null; }
  // linreg — رگرسیونِ خطیِ کمترین‌مربعات روی پنجرهٔ p؛ مقدار = a + b*(p-1-off).
  function linreg(src,p,off){ off=off||0; var s=st('linreg'); s.b=s.b||[]; s.b.push(src); if(s.b.length>p)s.b.shift(); if(s.b.length<p)return null; var sx=0,sy=0,sxx=0,sxy=0; for(var j=0;j<p;j++){ var y=s.b[p-1-j]; if(isna(y))return null; var x=p-1-j; sx+=x;sy+=y;sxx+=x*x;sxy+=x*y; } var dn=p*sxx-sx*sx; if(!dn)return null; var bb=(p*sxy-sx*sy)/dn,aa=(sy-bb*sx)/p; return aa+bb*(p-1-off); }
  // dema = 2*ema - ema(ema) ؛ tema = 3*e1 - 3*e2 + e3 ؛ trix = roc(ema³,1).
  function dema(src,p){ var s=st('dema'); var e=emaChain(s,'e',src,p); var e2=emaChain(s,'e2',e,p); return (e==null||e2==null)?null:2*e-e2; }
  function tema(src,p){ var s=st('tema'); var e1=emaChain(s,'e1',src,p); var e2=emaChain(s,'e2',e1,p); var e3=emaChain(s,'e3',e2,p); return (e1==null||e2==null||e3==null)?null:3*e1-3*e2+e3; }
  function trix(src,p){ var s=st('trix'); var e1=emaChain(s,'e1',src,p); var e2=emaChain(s,'e2',e1,p); var e3=emaChain(s,'e3',e2,p); return rocLocal(s,'r',e3,1); }
  // vwma = sma(close*V,p) / sma(V,p).
  function vwma(src,p){ var s=st('vwma'); var pv=src*(V[bar]||0); var sp=smaLocal(s,'p',pv,p),sv=smaLocal(s,'v',(V[bar]||0),p); return (sp==null||sv==null||!sv)?null:sp/sv; }

  // ───────── اسیلاتورها (معادلِ خط ۸۰–۸۵ namascript.js) ─────────
  function wpr(p){ p=p||14; var s=st('wpr'); var hh=highLocal(s,'h',H[bar],p),ll=lowLocal(s,'l',L[bar],p); if(hh==null||ll==null)return null; return (hh===ll)?-50:(hh-C[bar])/(hh-ll)*-100; }
  // cmo — جمعِ p-دورهٔ up/dn روی change(close,1).
  function cmo(src,p){ p=p||9; var s=st('cmo'); var pc=s.pc; s.pc=src; var ch=(pc==null||isna(src))?0:src-pc; var up=ch>0?ch:0,dn=ch<0?-ch:0; var su=sumLocal(s,'u',up,p),sd=sumLocal(s,'d',dn,p); if(su==null||sd==null)return null; var t=su+sd; return t?100*(su-sd)/t:0; }
  // tsi — double-smoothed momentum: 100 * ema(ema(nz(mom),lo),sh) / ema(ema(|nz(mom)|,lo),sh).
  // mom = change(close,1) ⇒ در کندلِ ۰ null است. نکتهٔ سازگاری با namascript.js: آنجا nz(m) از طریقِ
  // un() پیاده شده که برای ورودیِ null اصلاً callback را صدا نمی‌زند، پس nz(m) عملاً همان m است (null
  // باقی می‌ماند). بنابراین در کندلِ ۰ مقدارِ null می‌دهیم تا emaChain از کندلِ ۱ seed شود (هم‌مقدار).
  function tsi(src,sh,lo){ sh=sh||13;lo=lo||25; var s=st('tsi'); var pc=s.pc; s.pc=src; var m=(pc==null||isna(src))?null:src-pc; var am=(m==null)?null:Math.abs(m); var d1=emaChain(s,'d1',m,lo),dbl=emaChain(s,'d2',d1,sh); var a1=emaChain(s,'a1',am,lo),ab=emaChain(s,'a2',a1,sh); return (dbl==null||ab==null||!ab)?null:100*dbl/ab; }
  // ao — Awesome Oscillator: sma(hl2,5) - sma(hl2,34).
  function ao(){ var s=st('ao'); var mp=(H[bar]+L[bar])/2; var f=smaLocal(s,'f',mp,5),sl=smaLocal(s,'s',mp,34); return (f==null||sl==null)?null:f-sl; }
  // mfi — Money Flow Index؛ جمعِ p-دورهٔ جریانِ مثبت/منفی روی typical price.
  function mfi(p){ p=p||14; var s=st('mfi'); var tp=(H[bar]+L[bar]+C[bar])/3,rmf=tp*(V[bar]||0); var ptp=s.ptp; s.ptp=tp; var pos=0,neg=0; if(ptp!=null){ if(tp>ptp)pos=rmf; else if(tp<ptp)neg=rmf; } var sp=sumLocal(s,'p',pos,p),sn=sumLocal(s,'n',neg,p); if(sp==null||sn==null)return null; return sn?100-100/(1+sp/sn):100; }
  // stochrsi → {k,d}: stoch روی rsi، سپس k=sma(raw,k)، d=sma(k,dd).
  function stochrsi(src,p,k,dd){ p=p||14;k=k||3;dd=dd||3; var s=st('stochrsi'); var r=rsiLocal(s,'r',src,p); var hh=highChain(s,'h',r,p),ll=lowChain(s,'l',r,p); var raw=null; if(hh!=null&&ll!=null)raw=(hh===ll)?0:100*(r-ll)/(hh-ll); var ks=smaLocal(s,'k',raw,k); var dv=smaLocal(s,'d',ks,dd); return {k:ks,d:dv}; }

  // ───────── روند/جهت (معادلِ خط ۸۷–۹۰ namascript.js) ─────────
  // dmi(p,sm) → {plus,minus,adx}: +DM/-DM روی RMA، DX سپس RMA(sm) برای ADX.
  function dmi(p,sm){ p=p||14;sm=sm||14; var s=st('dmi'); var ph=s.ph,pl=s.pl,pc=s.pc; s.ph=H[bar];s.pl=L[bar];s.pc=C[bar]; var pdm=0,ndm=0,trv; if(ph==null){ trv=H[bar]-L[bar]; } else { var up=H[bar]-ph,dw=pl-L[bar]; pdm=(up>dw&&up>0)?up:0; ndm=(dw>up&&dw>0)?dw:0; trv=Math.max(H[bar]-L[bar],Math.abs(H[bar]-pc),Math.abs(L[bar]-pc)); } var atrr=rmaLocal(s,'tr',trv,p),sp=rmaLocal(s,'sp',pdm,p),sn=rmaLocal(s,'sn',ndm,p); var pdi=null,ndi=null,dx=null; if(atrr!=null&&sp!=null&&sn!=null&&atrr){ pdi=100*sp/atrr; ndi=100*sn/atrr; var su=pdi+ndi; dx=su?100*Math.abs(pdi-ndi)/su:0; } var adx=rmaLocal(s,'dx',dx,sm); return {plus:pdi,minus:ndi,adx:adx}; }
  function adxf(p,sm){ return dmi(p,sm).adx; }
  // aroon(p) → {up,down}: موقعیتِ بالاترین/پایین‌ترین در پنجرهٔ p+1 کندل.
  function aroon(p){ p=p||14; var s=st('aroon'); s.bh=s.bh||[]; s.bl=s.bl||[]; s.bh.push(H[bar]); s.bl.push(L[bar]); if(s.bh.length>p+1){s.bh.shift();s.bl.shift();} if(s.bh.length<p+1)return {up:null,down:null}; var hi=-Infinity,lo=Infinity,hb=0,lb=0,len=s.bh.length; for(var j=0;j<=p;j++){ var idx=len-1-j; if(s.bh[idx]>hi){hi=s.bh[idx];hb=j;} if(s.bl[idx]<lo){lo=s.bl[idx];lb=j;} } return {up:100*(p-hb)/p,down:100*(p-lb)/p}; }
  // sar — Parabolic SAR؛ init (trend=1, ep=H[0], sv=L[0])، خروجیِ کندلِ ۰ = null.
  function sarf(stp,inc,mx){ stp=stp||0.02;inc=inc||0.02;mx=mx||0.2; var s=st('sar'); if(!s.init){ s.init=1; s.trend=1; s.af=stp; s.ep=H[bar]; s.sv=L[bar]; return null; } s.sv=s.sv+s.af*(s.ep-s.sv); if(s.trend>0){ if(L[bar]<s.sv){s.trend=-1;s.sv=s.ep;s.ep=L[bar];s.af=stp;} else if(H[bar]>s.ep){s.ep=H[bar];s.af=Math.min(mx,s.af+inc);} } else { if(H[bar]>s.sv){s.trend=1;s.sv=s.ep;s.ep=H[bar];s.af=stp;} else if(L[bar]<s.ep){s.ep=L[bar];s.af=Math.min(mx,s.af+inc);} } return s.sv; }

  // ───────── نوسان/باند (معادلِ خط ۹۳–۹۶ namascript.js) ─────────
  // bbw — پهنای باند بولینگر: (upper-lower)*100/mid.
  function bbw(src,p,m){ p=p||20;m=m||2; var s=st('bbw'); var mid=smaLocal(s,'m',src,p),sd=stdevLocal(s,'s',src,p); if(mid==null||sd==null)return null; var up=mid+sd*m,lo=mid-sd*m; return mid?(up-lo)*100/mid:null; }
  // kc — کانال کلتنر: ema(src,p) ± m*rma(range,p) (range = tr یا high-low).
  function kc(src,p,m,useTR){ p=p||20;m=m||2; var s=st('kc'); var mid=emaLocal(s,'m',src,p); var rng=(useTR===false)?(H[bar]-L[bar]):trLocal(s); var ra=rmaLocal(s,'r',rng,p); if(mid==null||ra==null)return {mid:mid,upper:null,lower:null}; return {mid:mid,upper:mid+ra*m,lower:mid-ra*m}; }
  function kcw(src,p,m,useTR){ var k=kc(src,p,m,useTR); if(k.mid==null||k.upper==null)return null; return k.mid?(k.upper-k.lower)*100/k.mid:null; }
  // donchian(p) → {upper,lower,basis}: highest(high,p)/lowest(low,p).
  function donchian(p){ p=p||20; var s=st('donchian'); var u=highLocal(s,'u',H[bar],p),l=lowLocal(s,'l',L[bar],p); if(u==null||l==null)return {upper:u,lower:l,basis:null}; return {upper:u,lower:l,basis:(u+l)/2}; }

  // ───────── همبستگی/میانه (معادلِ خط ۱۰۲/۱۰۳ namascript.js) ─────────
  function correlation(a,b,p){ var s=st('correlation'); s.ba=s.ba||[]; s.bb=s.bb||[]; s.ba.push(a); s.bb.push(b); if(s.ba.length>p){s.ba.shift();s.bb.shift();} if(s.ba.length<p)return null; var sa=0,sb=0,saa=0,sbb=0,sab=0; for(var j=0;j<p;j++){ var x=s.ba[j],y=s.bb[j]; if(isna(x)||isna(y))return null; sa+=x;sb+=y;saa+=x*x;sbb+=y*y;sab+=x*y; } var dn=Math.sqrt((p*saa-sa*sa)*(p*sbb-sb*sb)); return dn?(p*sab-sa*sb)/dn:null; }
  function medianf(src,p){ var s=st('median'); s.b=s.b||[]; s.b.push(src); if(s.b.length>p)s.b.shift(); if(s.b.length<p)return null; var w=[]; for(var j=0;j<p;j++){ if(isna(s.b[j]))return null; w.push(s.b[j]); } w.sort(function(x,y){return x-y;}); return w[Math.floor(p/2)]; }

  // ───────── حجم (معادلِ خط ۱۰۴–۱۰۶ namascript.js) ─────────
  // obv — On-Balance Volume تجمعی؛ مقایسهٔ close با کندلِ قبل.
  function obv(){ var s=st('obv'); if(s.r==null)s.r=0; var pc=s.pc; s.pc=C[bar]; if(pc!=null){ if(C[bar]>pc)s.r+=V[bar]||0; else if(C[bar]<pc)s.r-=V[bar]||0; } return s.r; }
  // ad — Accumulation/Distribution تجمعی (money-flow multiplier × volume).
  function adf(){ var s=st('ad'); if(s.r==null)s.r=0; var rng=H[bar]-L[bar],mfm=rng?((C[bar]-L[bar])-(H[bar]-C[bar]))/rng:0; s.r+=mfm*(V[bar]||0); return s.r; }
  // cmf — Chaikin Money Flow: sum(mfv,p)/sum(volume,p).
  function cmf(p){ p=p||20; var s=st('cmf'); var rng=H[bar]-L[bar],mfv=(rng?((C[bar]-L[bar])-(H[bar]-C[bar]))/rng:0)*(V[bar]||0); var sm=sumLocal(s,'m',mfv,p),sv=sumLocal(s,'v',(V[bar]||0),p); return (sm==null||sv==null||!sv)?null:sm/sv; }

  // emaChain — دقیقاً معادلِ ema در namascript.js (خط ۳۱): nullها رد می‌شوند (pr حفظ می‌ماند)
  // و خروجی با گیتِ «ایندکسِ مطلقِ کندل» (bar>=p-1) باز می‌شود. این رفتار با شمارش (count-gate)
  // فرق دارد و فقط وقتی مهم می‌شود که ورودی nullِ ابتدایی داشته باشد (یعنی در زنجیرهٔ ema(ema(...))).
  // مرکّب‌های dema/tema/trix/tsi از این استفاده می‌کنند تا با موتورِ vectorized هم‌مقدار باشند.
  function emaChain(s,key,src,p){ var c=s[key]||(s[key]={}); if(!isna(src)){ var k=2/(p+1); c.pr=(c.pr==null)?src:src*k+c.pr*(1-k); } return (bar>=p-1&&c.pr!=null)?c.pr:null; }
  // stdevLocal — keyed نسخهٔ stdev برای مرکّب‌ها.
  function stdevLocal(s,key,src,p){ var c=s[key]||(s[key]={b:[]}); c.b.push(src); if(c.b.length>p)c.b.shift(); if(c.b.length<p)return null; var m=0,i; for(i=0;i<p;i++){ if(isna(c.b[i]))return null; m+=c.b[i]; } m/=p; var v=0; for(i=0;i<p;i++){ var dd=c.b[i]-m; v+=dd*dd; } return Math.sqrt(v/p); }

  var ta={ sma:sma,ema:ema,rma:rma,smma:rma,wma:wma,hma:hma,swma:swma,alma:alma,linreg:linreg,dema:dema,tema:tema,trix:trix,vwma:vwma,stdev:stdev,dev:stdev,variance:variance,highest:highest,lowest:lowest,highestbars:highestbars,lowestbars:lowestbars,sum:sumf,cum:cumf,median:medianf,correlation:correlation,change:change,mom:mom,roc:roc,tr:trf,atr:atr,rsi:rsi,stochrsi:stochrsi,wpr:wpr,cmo:cmo,tsi:tsi,ao:ao,mfi:mfi,cci:cci,macd:macd,bb:bb,bbw:bbw,kc:kc,kcw:kcw,donchian:donchian,stoch:stoch,supertrend:supertrend,sar:sarf,dmi:dmi,adx:adxf,aroon:aroon,vwap:vwap,ichimoku:ichimoku,obv:obv,ad:adf,cmf:cmf,pivothigh:pivothigh,pivotlow:pivotlow,crossover:crossover,crossunder:crossunder,cross:crossf,rising:rising,falling:falling,barssince:barssince,valuewhen:valuewhen };

  // ───────── math.* (اسکالر؛ na را پخش می‌کند) ─────────
  function m1(f){ return function(a){ return isna(a)?null:f(a); }; }
  function m2(f){ return function(a,b){ return (isna(a)||isna(b))?null:f(a,b); }; }
  var math={ abs:m1(Math.abs),max:m2(Math.max),min:m2(Math.min),round:m1(Math.round),sqrt:m1(Math.sqrt),pow:m2(Math.pow),avg:m2(function(a,b){return (a+b)/2;}),floor:m1(Math.floor),ceil:m1(Math.ceil),sign:m1(Math.sign),exp:m1(Math.exp),log:m1(Math.log),log10:m1(function(x){return Math.log(x)/Math.LN10;}),sin:m1(Math.sin),cos:m1(Math.cos),tan:m1(Math.tan),asin:m1(Math.asin),acos:m1(Math.acos),atan:m1(Math.atan),todegrees:m1(function(x){return x*180/Math.PI;}),toradians:m1(function(x){return x*Math.PI/180;}),round_to_mintick:m1(function(x){return Math.round(x*1e5)/1e5;}),sum:sumf,pi:Math.PI,e:Math.E,phi:1.618033988749895 };

  // ───────── str.* (اسکالر) ─────────
  var str={ tostring:function(a){return String(disp(a));}, tonumber:function(a){return Number(a);}, length:function(s){return s==null?0:String(s).length;}, contains:function(s,t){return String(s).indexOf(t)>=0;}, replace_all:function(s,t,r){return String(s).split(t).join(r);}, split:function(s,d){return String(s).split(d);}, upper:function(s){return String(s).toUpperCase();}, lower:function(s){return String(s).toLowerCase();}, startswith:function(s,t){return String(s).indexOf(t)===0;}, endswith:function(s,t){var x=String(s),y=String(t);return x.indexOf(y,x.length-y.length)!==-1;}, format:function(f){var args=Array.prototype.slice.call(arguments,1),k=0;return String(f).replace(/\\{\\d+([^}]*)?\\}/g,function(){var v=args[k++];return String(disp(v));});} };

  // ───────── input.* (یک‌بار اعلام؛ مقدارِ کاربر override می‌کند) ─────────
  var inputSeen={};
  function input(def,title,type,opts){ var key=title||('input'+inputs.length); if(!inputSeen[key]){inputSeen[key]=1;var decl={key:key,type:type||(typeof def==='boolean'?'bool':'float'),def:def};if(opts){if(opts.min!=null)decl.min=opts.min;if(opts.max!=null)decl.max=opts.max;if(opts.step!=null)decl.step=opts.step;if(opts.options)decl.options=opts.options;}inputs.push(decl);} var v=IN[key]; return (v===undefined||v===null)?def:v; }
  input.int=function(dd,t,mn,mx,sp){return input(dd,t,'int',{min:mn,max:mx,step:sp});};
  input.float=function(dd,t,mn,mx,sp){return input(dd,t,'float',{min:mn,max:mx,step:sp});};
  input.bool=function(dd,t){return input(dd,t,'bool');};
  input.string=function(dd,t,opts){return input(dd,t,'string',{options:opts});};
  input.color=function(dd,t){return input(dd,t,'color');};
  input.source=function(dd,t){ var key=t||('input'+inputs.length); if(!inputSeen[key]){inputSeen[key]=1;inputs.push({key:key,type:'source',def:'close'});} return dd; };

  // ───────── رنگ/شکل ─────────
  function colorNew(c,t){ if(t==null)return c; var a=Math.max(0,Math.min(100,t)); return hexToRgba(c,((100-a)/100)); }
  var color={blue:'#3b82f6',red:'#ef4444',green:'#22c55e',orange:'#f59e0b',purple:'#a855f7',gray:'#94a3b8',white:'#e5e7eb',yellow:'#eab308',aqua:'#22d3ee',teal:'#14b8a6',black:'#000000',silver:'#cbd5e1',lime:'#84cc16',maroon:'#7f1d1d',navy:'#1e3a8a',fuchsia:'#d946ef',
    new:colorNew, rgb:function(r,g,b,t){ var a=(t==null?0:Math.max(0,Math.min(100,t))); return 'rgba('+(r|0)+','+(g|0)+','+(b|0)+','+((100-a)/100)+')'; }};
  var shape={up:'up',down:'down',circle:'circle',cross:'cross',flag:'flag',triangleup:'up',triangledown:'down',arrowup:'up',arrowdown:'down',labelup:'up',labeldown:'down'};

  // ───────── خروجی‌های رسم (per-bar: روی کندلِ جاری جمع می‌شوند) ─────────
  // هر plot یک سطلِ نام‌دار است؛ در هر کندل یک نقطه اضافه می‌شود (کلید = نام، تا چند plot از هم جدا بمانند).
  var plotMap={};
  function plot(v,name,col,w,style){ name=name||('plot'+Object.keys(plotMap).length); var p=plotMap[name]; if(!p){ p={name:name,color:col||'#3b82f6',width:w||2,style:style||0,data:[]}; plotMap[name]=p; plots.push(p); } if(col)p.color=col; if(!isna(v))p.data.push({time:T[bar],value:v}); }
  var shapeMap={};
  function plotshape(cond,name,shp,col){ name=name||'shape'; shp=shp||'up'; var key=name+'|'+shp; var sh=shapeMap[key]; if(!sh){ sh={name:name,color:col||'#22c55e',shape:shp,points:[]}; shapeMap[key]=sh; shapes.push(sh); } if(col)sh.color=col; if(cond)sh.points.push({time:T[bar],price:(shp==='down'?H[bar]:L[bar]),shape:shp}); }
  function plotchar(cond,name,col){ plotshape(cond,name,'circle',col); }
  var arrowUp=null,arrowDn=null;
  function plotarrow(v,name,cu,cd){ if(v>0){ if(!arrowUp){arrowUp={name:(name||'فلش')+'↑',color:cu||'#22c55e',shape:'up',points:[]};shapes.push(arrowUp);} arrowUp.points.push({time:T[bar],price:L[bar],shape:'up'}); } else if(v<0){ if(!arrowDn){arrowDn={name:(name||'فلش')+'↓',color:cd||'#ef4444',shape:'down',points:[]};shapes.push(arrowDn);} arrowDn.points.push({time:T[bar],price:H[bar],shape:'down'}); } }
  var candleMap={};
  function plotcandle(o,h,l,c,name,col){ name=name||'کندل'; var cp=candleMap[name]; if(!cp){ cp={name:name,color:col||null,data:[]}; candleMap[name]=cp; candleplots.push(cp); } if(!(isna(o)||isna(h)||isna(l)||isna(c)))cp.data.push({time:T[bar],open:o,high:h,low:l,close:c}); }
  var hlineSeen={};
  function hline(p,name,col){ var k=p+'|'+(name||''); if(hlineSeen[k])return; hlineSeen[k]=1; hlines.push({price:p,title:name||'',color:col||'#94a3b8'}); }
  var bgMap={};
  function bgcolor(cond,col){ col=col||'rgba(59,130,246,.12)'; var b=bgMap[col]; if(!b){ b={color:col,bars:[]}; bgMap[col]=b; bgs.push(b); } if(cond)b.bars.push(T[bar]); }
  var fillState=null;
  function fill(a,b,col){ if(!fillState){ fillState={color:col||'rgba(59,130,246,.12)',a:[],b:[]}; fills.push(fillState); } if(col)fillState.color=col; if(!isna(a))fillState.a.push({time:T[bar],value:a}); if(!isna(b))fillState.b.push({time:T[bar],value:b}); }
  var barcolorState=null;
  function barcolor(col,cond){ if(cond===undefined){ if(!barcolorState||!barcolorState.all){ barcolorState={color:col,all:true}; barcolors.push(barcolorState); } return; } if(!barcolorState||barcolorState.all){ barcolorState={color:col,bars:[]}; barcolors.push(barcolorState); } barcolorState.color=col; if(cond)barcolorState.bars.push(T[bar]); }
  // riskreward(entry,sl,tp1[,tp2,tp3]) — آخرین مقادیرِ معتبر را در پایانِ اجرا به جعبهٔ سود/ضرر تبدیل می‌کنیم
  var rrLast=null;
  function riskreward(entry,sl,tp1,tp2,tp3){ if(!isna(entry)&&!isna(sl)&&!isna(tp1)){ rrLast={entry:entry,sl:sl,tp1:tp1,tp2:tp2,tp3:tp3,bar:bar}; } }
  // label.new(cond, src, text, color) — سازگار با امضای ساده‌شدهٔ نمااسکریپت
  var label={ new:function(cond,src,txt,col){ if(cond&&!isna(src))labels.push({time:T[bar],price:src,text:txt||'',color:col||'#e5e7eb'}); } };
  // line.new / box.new / table — آبجکت‌های رسمِ create-once (مختصات با xtime نگاشت می‌شوند)
  var line={ new:function(x1,y1,x2,y2,col,w,style){ lines.push({x1:xtime(x1),y1:y1,x2:xtime(x2),y2:y2,color:col||'#3b82f6',width:w||1,style:style||0}); return lines.length-1; }, set_xy1:function(){}, set_xy2:function(){}, set_color:function(){}, set_width:function(){}, delete:function(id){ if(id!=null&&lines[id])lines[id]=null; } };
  var box={ new:function(l,t,r,bm,col,bg){ boxes.push({left:xtime(l),top:t,right:xtime(r),bottom:bm,color:col||'#3b82f6',bg:bg||'rgba(59,130,246,.08)'}); return boxes.length-1; }, set_top:function(){}, set_bottom:function(){}, delete:function(id){ if(id!=null&&boxes[id])boxes[id]=null; } };
  function table(){}
  table.new=function(){ var t={cells:[]}; tables.push(t); return t; };
  table.cell=function(t,c,r,txt,col){ if(t&&t.cells)t.cells.push({col:c|0,row:r|0,text:String(disp(txt)),color:col||null}); };
  table.cell_set_text=function(t,c,r,txt){ if(t&&t.cells)t.cells.push({col:c|0,row:r|0,text:String(disp(txt))}); };
  // alert(msg) / alert(cond, msg) — کندلِ جاری اگر شرط برقرار باشد ثبت می‌شود
  var alertMap={};
  function alert(a,b){ var msg, cond; if(typeof a==='string'){ msg=a; cond=true; } else { cond=a; msg=b||'هشدار'; } if(!cond)return; var al=alertMap[msg]; if(!al){ al={msg:String(msg),bars:[],dynamic:true}; alertMap[msg]=al; alerts.push(al); } al.bars.push(T[bar]); }
  function alertcondition(cond,msg){ msg=msg||'هشدار'; var al=alertMap['ac:'+msg]; if(!al){ al={msg:msg,bars:[]}; alertMap['ac:'+msg]=al; alerts.push(al); } if(cond)al.bars.push(T[bar]); }

  // ───────── strategy.* — سیگنال‌های هر کندل ثبت و در پایان شبیه‌سازی می‌شوند ─────────
  // strategy هم به‌صورتِ تابعِ اعلان (strategy('عنوان', ...)) و هم به‌صورتِ آبجکت با متدها قابلِ استفاده است.
  function strategy(){ strat.enabled=true; }
  strategy.entry=function(dir,cond){ strat.enabled=true; if(cond){ if(dir==='long'||dir==='buy')strat.longBars[bar]=1; else strat.shortBars[bar]=1; } };
  strategy.exit=function(dir,cond){ if(cond){ if(dir==='long')strat.exitLBars[bar]=1; else strat.exitSBars[bar]=1; } };
  strategy.close=function(cond){ if(cond){ strat.exitLBars[bar]=1; strat.exitSBars[bar]=1; } };
  strategy.long='long'; strategy.short='short';
  function study(){} function indicator(){}
  function runtime_error(msg){ throw new Error(String(msg)); }

  // ───────── جدولِ توابعِ ثابت (بدونِ وابستگی به کندل) که به بدنه تزریق می‌شوند ─────────
  var request={ security:function(){ return null; } }; // در موتورِ bar فعلاً HTF پشتیبانی نمی‌شود (graceful)

  // ── پیش‌پردازنده: نحوِ Pine را به فراخوانیِ موتورِ bar تبدیل می‌کند ──
  // var/varip [type] x = EXPR  → x = __setinit("x",(EXPR))   (یک‌بار مقداردهی، در ادامه EXPR نادیده گرفته می‌شود)
  // x := EXPR                  → x = (EXPR)                  (انتسابِ درون‌کندلی روی همان binding)
  // IDENT[n] / dotted[n]       → __h("IDENT", n)             (history operator)
  // هر سطر در حلقهٔ کندل اجرا می‌شود؛ نامِ varها به‌صورتِ پراپرتیِ __ctx از پیش seed می‌شود
  // پس 'x = ...' درونِ with(__ctx) روی همان slot می‌نشیند و بینِ کندل‌ها پایدار می‌ماند.
  var PERSIST_NAMES = {};   // نامِ varهایی که باید پایدار بمانند (برای seed در ctx)
  function preprocess(src){
    src = String(src);
    // ۱) var/varip [type] name = EXPR (تا انتهای سطر) → name = __setinit("name",(EXPR))
    src = src.replace(
      /(^|[\\n;{}])\\s*(?:var|varip)\\s+(?:(?:int|float|bool|string|color)\\s+)?([A-Za-z_$][\\w$]*)\\s*=\\s*([^\\n;]*)/g,
      function(_, pre, nm, expr){ PERSIST_NAMES[nm]=1; return pre+' '+nm+'=__setinit("'+nm+'",('+(expr.trim()||'null')+'))'; }
    );
    // ۲) انتساب := → = (روی binding که با var ساخته شده، یا متغیرِ موقتِ همان کندل)
    src = src.replace(/:=/g, '=');
    // ۳) history: IDENT یا dotted با [اندیس عدد/شناسه] → __h("IDENT", idx)
    src = src.replace(/([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\[\\s*([0-9]+|[A-Za-z_$][\\w$]*)\\s*\\]/g, '__h("$1",$2)');
    return src;
  }

  // وضعیتِ متغیرهای پایدار (var/varip)
  var PERSIST = {};       // مقدارِ فعلیِ هر varِ پایدار
  var PERSIST_INIT = {};  // آیا قبلاً (یک‌بار) مقداردهیِ اولیه شده؟
  // __setinit("x", EXPR): فقط در اولین برخورد EXPR را به‌عنوانِ مقدارِ اولیه می‌نشاند؛
  // در کندل‌های بعدی EXPR نادیده گرفته می‌شود و مقدارِ پایدارِ فعلی برمی‌گردد ⇒ معناشناسیِ Pine's var.
  function __setinit(nm,val){ if(!PERSIST_INIT[nm]){ PERSIST[nm]=isna(val)?null:val; PERSIST_INIT[nm]=1; } return PERSIST[nm]; }

  // history: نام را به سریِ تاریخچه‌دار نگاشت می‌کنیم (close/high/... و varها و plot-source)
  // سری‌های پایه را یک‌بار با مقادیرِ کامل پر می‌کنیم؛ varها از PERSIST_HIST خوانده می‌شوند.
  var BASE = { open:O, high:H, low:L, close:C, volume:V, time:T };
  var DERIVED = {};
  function buildDerived(name){
    if(DERIVED[name])return DERIVED[name];
    var a=new Array(N);
    for(var i=0;i<N;i++){ if(name==='hl2')a[i]=(H[i]+L[i])/2; else if(name==='hlc3')a[i]=(H[i]+L[i]+C[i])/3; else if(name==='ohlc4')a[i]=(O[i]+H[i]+L[i]+C[i])/4; else if(name==='hlcc4')a[i]=(H[i]+L[i]+C[i]+C[i])/4; else if(name==='bar_index')a[i]=i; else a[i]=null; }
    DERIVED[name]=a; return a;
  }
  var PERSIST_HIST = {};   // تاریخچهٔ varها برای close[1]-مانند روی متغیرِ پایدار
  function __h(name, n){
    n=(n==null?0:n)|0;
    if(BASE[name]){ var j=bar-n; return (j>=0&&j<N)?BASE[name][j]:null; }
    if(name==='hl2'||name==='hlc3'||name==='ohlc4'||name==='hlcc4'||name==='bar_index'){ var arr=buildDerived(name),k=bar-n; return (k>=0&&k<N)?arr[k]:null; }
    if(name in PERSIST_HIST){ var h=PERSIST_HIST[name],q=bar-n; return (q>=0&&q<N&&q<h.length)?h[q]:null; }
    // ناشناخته: null امن
    return null;
  }

  try {
    var __SRC = preprocess(d.source);
    // بدنهٔ کاربر به‌صورتِ تابع؛ هر آرگومان یک نام/تابعِ تزریقی است.
    // متغیرهای OHLCV درونِ حلقه برای هر کندل به اسکالر مقدار می‌گیرند.
    function iff(c,a,b){ return c?a:b; }
    // هر فراخوانیِ ta.* یک call-site دارد؛ ما با شمارنده‌ای که قبل از هر فراخوانی increment می‌شود
    // به‌صورتِ تقریبی به آن state می‌دهیم. برای دقتِ بیشتر شمارنده در ابتدای هر کندل ری‌ست می‌شود.
    var __SITE=0;
    function siteWrap(fn){ return function(){ TA_STATE.__site=(++__SITE); var r=fn.apply(null,arguments); return r; }; }
    // پیچشِ همهٔ توابعِ ta با شناسهٔ call-site پایدار بینِ کندل‌ها (ترتیبِ فراخوانی در هر کندل یکسان است)
    var taW={}; for(var tn in ta){ taW[tn]=siteWrap(ta[tn]); }

    var body = new Function(
      'ta','math','input','plot','plotshape','plotchar','plotarrow','plotcandle','hline','bgcolor','fill','riskreward','barcolor','label','alertcondition','alert','line','box','table','strategy','study','indicator','runtime_error',
      'crossover','crossunder','cross','nz','na','iff','request','int','float','bool','color','shape','str','__h','__setinit','__ctx',
      'with(__ctx){ '+__SRC+' \\n}'
    );

    // ── حلقهٔ کندل: قلبِ موتورِ bar-by-bar ──
    var ctx = {};
    for(bar=0; bar<N; bar++){
      __SITE=0; // ابتدای هر کندل: شمارندهٔ call-site از صفر تا state هر ta.* پایدار بماند
      // built-inهای اسکالرِ کندلِ جاری
      ctx.open=O[bar]; ctx.high=H[bar]; ctx.low=L[bar]; ctx.close=C[bar]; ctx.volume=V[bar]; ctx.time=T[bar];
      ctx.hl2=(H[bar]+L[bar])/2; ctx.hlc3=(H[bar]+L[bar]+C[bar])/3; ctx.ohlc4=(O[bar]+H[bar]+L[bar]+C[bar])/4; ctx.hlcc4=(H[bar]+L[bar]+C[bar]+C[bar])/4;
      ctx.bar_index=bar; ctx.last_bar_index=N-1; ctx.last_bar_time=T[N-1];
      // barstate.* — وضعیتِ واقعیِ کندل (تاریخی: همه confirmed جز کندلِ آخرِ زنده)
      ctx.barstate={ isfirst:(bar===0), islast:(bar===N-1), isnew:true, isconfirmed:(bar<N-1), isrealtime:false, ishistory:(bar<N-1) };
      // varهای پایدار را به‌صورتِ slot در ctx seed کن (با with(__ctx) انتساب روی همین slot می‌نشیند)
      for(var pk in PERSIST_NAMES){ ctx[pk]=PERSIST[pk]; }

      // اجرای بدنهٔ کاربر برای این کندل
      body(taW,math,input,plot,plotshape,plotchar,plotarrow,plotcandle,hline,bgcolor,fill,riskreward,barcolor,label,alertcondition,alert,line,box,table,strategy,study,indicator,runtime_error,
           crossover,crossunder,crossf,nz,naf,iff,request,toint,tofloat,tobool,color,shape,str,__h,__setinit,ctx);

      // پس از اجرا: مقدارِ نهاییِ هر varِ پایدار را از ctx بخوان و تاریخچه‌اش را ثبت کن
      for(var pk2 in PERSIST_NAMES){ var nv=ctx[pk2]; PERSIST[pk2]=(nv===undefined)?PERSIST[pk2]:nv; if(!PERSIST_HIST[pk2])PERSIST_HIST[pk2]=new Array(N); PERSIST_HIST[pk2][bar]=PERSIST[pk2]; }
    }

    // ── riskreward → zone (مثلِ موتورِ vectorized) ──
    if(rrLast){ var tps=[rrLast.tp1]; if(!isna(rrLast.tp2))tps.push(rrLast.tp2); if(!isna(rrLast.tp3))tps.push(rrLast.tp3); zones.push({entry:rrLast.entry,sl:rrLast.sl,tps:tps,tp:rrLast.tp1,fromTime:T[Math.max(0,N-60)],toTime:T[N-1]}); }

    // ── شبیه‌سازِ استراتژی (هم‌سان با namascript.js: تک‌واحد، ورود/خروج روی close) ──
    var stratResult=null;
    if(strat.enabled){
      var comm=+(IN.__comm||0), slip=+(IN.__slip||0), cost=comm+slip;
      var pos=0,entry=0,eq=0,peak=0,dd=0,wins=0,nT=0,gp=0,gl=0,trades=[];
      function rec(r,t,dir){ r-=cost; eq+=r; nT++; if(r>=0){wins++;gp+=r;}else gl+=-r; trades.push({t:t,r:r,dir:dir}); peak=Math.max(peak,eq); dd=Math.max(dd,peak-eq); }
      for(var i=1;i<N;i++){
        if(pos===0){ if(strat.longBars[i])
{pos=1;entry=C[i];} else if(strat.shortBars[i]){pos=-1;entry=C[i];} }
        else if(pos===1){ var ex=strat.shortBars[i]||strat.exitLBars[i]; if(ex){rec(C[i]-entry,T[i],1);pos=strat.shortBars[i]?-1:0;if(pos===-1)entry=C[i];} }
        else if(pos===-1){ var ex2=strat.longBars[i]||strat.exitSBars[i]; if(ex2){rec(entry-C[i],T[i],-1);pos=strat.longBars[i]?1:0;if(pos===1)entry=C[i];} }
      }
      var equity=[],run=0; trades.forEach(function(tr){run+=tr.r;equity.push({t:tr.t,e:run});});
      var wArr=trades.filter(function(t){return t.r>=0;}).map(function(t){return t.r;});
      var lArr=trades.filter(function(t){return t.r<0;}).map(function(t){return t.r;});
      var sum=function(a){return a.reduce(function(x,y){return x+y;},0);};
      var avgW=wArr.length?sum(wArr)/wArr.length:0, avgL=lArr.length?sum(lArr)/lArr.length:0;
      var maxW=wArr.length?Math.max.apply(null,wArr):0, maxL=lArr.length?Math.min.apply(null,lArr):0;
      var longN=trades.filter(function(t){return t.dir===1;}).length, shortN=nT-longN;
      var ws=0,ls=0,wsMax=0,lsMax=0; trades.forEach(function(t){ if(t.r>=0){ws++;ls=0;wsMax=Math.max(wsMax,ws);} else {ls++;ws=0;lsMax=Math.max(lsMax,ls);} });
      var avgTrade=nT?eq/nT:0, expectancy=nT?(wins/nT)*avgW+((nT-wins)/nT)*avgL:0;
      var mean=avgTrade, varc=0; trades.forEach(function(t){varc+=(t.r-mean)*(t.r-mean);}); var sd=nT>1?Math.sqrt(varc/(nT-1)):0; var sharpe=sd?(mean/sd)*Math.sqrt(Math.max(1,nT)):0;
      stratResult={trades:nT,net:eq,win:nT?Math.round(wins/nT*100):0,pf:gl?(gp/gl):(gp>0?99:0),dd:dd,equity:equity,list:trades.slice(-50),
        avgWin:avgW,avgLoss:avgL,maxWin:maxW,maxLoss:maxL,longN:longN,shortN:shortN,winStreak:wsMax,lossStreak:lsMax,avgTrade:avgTrade,expectancy:expectancy,sharpe:sharpe,grossProfit:gp,grossLoss:gl,wins:wins,losses:nT-wins};
    }

    self.postMessage({ok:true,plots:plots,shapes:shapes,hlines:hlines,bgs:bgs,labels:labels,alerts:alerts,inputs:inputs,zones:zones,fills:fills,lines:lines.filter(Boolean),boxes:boxes.filter(Boolean),tables:tables,barcolors:barcolors,candleplots:candleplots,strategy:stratResult});
  } catch (err) {
    self.postMessage({ok:false,error:String(err&&err.message||err)});
  }
};
// شمارندهٔ کندلِ جاری (سطحِ worker تا همهٔ کمک‌توابع ببینندش)
var bar=0;
`;

let _url = null;
function workerUrl() { if (!_url) _url = URL.createObjectURL(new Blob([WORKER_SRC], { type: 'application/javascript' })); return _url; }

// runScriptBar — جایگزینِ drop-in برای runScript با اجرای کندل‌به‌کندل (Pine bar-by-bar).
// امضا و خروجی دقیقاً مثلِ runScript است؛ پس هر جا runScript صدا زده می‌شود می‌توان این را گذاشت.
export function runScriptBar(source, candles, inputs = {}, timeoutMs = 5000) {
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
