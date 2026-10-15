// بازارنما — غنی‌سازیِ متادیتای نماد (کلاینت‌ساید، بدون تماسِ بک‌اند اضافه).
// خروجیِ خامِ chartSymbols() (آرایهٔ رشته‌ای) را به SymbolMeta[] تبدیل می‌کند تا
// مدالِ جستجوی #7 بتواند دسته‌بندی/توضیحِ فارسی/پرچم بدهد. بک‌اند تغییر نمی‌کند؛
// اگر بعداً سرور فیلدها را داد، فقط buildMeta با merge جایگزین می‌شود.
// ارزهای فیاتِ واقعیِ موجود روی حسابِ مَستر (میجرها + کراس‌ها + اگزوتیک‌های رایجِ OneRoyal).
const CCY = ['USD', 'EUR', 'GBP', 'JPY', 'CHF', 'CAD', 'AUD', 'NZD',
  'NOK', 'SEK', 'SGD', 'HKD', 'TRY', 'ZAR', 'MXN', 'CNH', 'DKK', 'PLN',
  'CNY', 'RUB', 'INR', 'KRW', 'BRL'];
const CCY_FA = {
  USD: 'دلار آمریکا', EUR: 'یورو', GBP: 'پوند انگلیس', JPY: 'ین ژاپن',
  CHF: 'فرانک سوئیس', CAD: 'دلار کانادا', AUD: 'دلار استرالیا', NZD: 'دلار نیوزیلند',
  NOK: 'کرون نروژ', SEK: 'کرون سوئد', SGD: 'دلار سنگاپور', HKD: 'دلار هنگ‌کنگ',
  TRY: 'لیر ترکیه', ZAR: 'راند آفریقای جنوبی', MXN: 'پزو مکزیک', CNH: 'یوان چین',
  DKK: 'کرون دانمارک', PLN: 'زلوتی لهستان',
  CNY: 'یوان چین', RUB: 'روبل روسیه', INR: 'روپیه هند', KRW: 'وون کره جنوبی', BRL: 'رئال برزیل',
};
// نامِ کاملِ انگلیسیِ استانداردِ ارزها (سبکِ سرتیترِ TradingView).
const CCY_EN = {
  USD: 'US Dollar', EUR: 'Euro', GBP: 'British Pound', JPY: 'Japanese Yen',
  CHF: 'Swiss Franc', CAD: 'Canadian Dollar', AUD: 'Australian Dollar', NZD: 'New Zealand Dollar',
  NOK: 'Norwegian Krone', SEK: 'Swedish Krona', SGD: 'Singapore Dollar', HKD: 'Hong Kong Dollar',
  TRY: 'Turkish Lira', ZAR: 'South African Rand', MXN: 'Mexican Peso', CNH: 'Chinese Yuan',
  DKK: 'Danish Krone', PLN: 'Polish Zloty',
  CNY: 'Chinese Yuan', RUB: 'Russian Ruble', INR: 'Indian Rupee', KRW: 'South Korean Won', BRL: 'Brazilian Real',
};
const METAL = { XAU: ['طلا', 'metal'], XAG: ['نقره', 'metal'], XPT: ['پلاتین', 'metal'], XPD: ['پالادیوم', 'metal'], XCU: ['مس', 'metal'] };
const METAL_EN = { XAU: 'Gold', XAG: 'Silver', XPT: 'Platinum', XPD: 'Palladium', XCU: 'Copper' };
const ENERGY = /^(OIL|WTI|BRENT|XTI|XBR|XNG|NGAS|NATGAS|UKOIL|USOIL)/;
// تفکیکِ نوعِ ابزارِ انرژی برای نامِ درست (WTI / برنت / گاز طبیعی) — نه برچسبِ عمومی.
const ENERGY_MAP = [
  { re: /^(XTI|WTI|USOIL|OIL)/, fa: 'نفت خام WTI', en: 'WTI Crude Oil', base: 'WTI' },
  { re: /^(XBR|UKOIL|BRENT)/, fa: 'نفت خام برنت', en: 'Brent Crude Oil', base: 'BRENT' },
  { re: /^(XNG|NGAS|NATGAS)/, fa: 'گاز طبیعی', en: 'Natural Gas', base: 'NGAS' },
];
const INDEX = /^(US30|US500|US100|NAS100|NAS|SPX|DJI|NDX|UK100|DE40|GER40|JP225|FRA40|HK50|AUS200|EU50)/;
const INDEX_FA = {
  US30: 'داوجونز ۳۰', US500: 'اس‌اند‌پی ۵۰۰', US100: 'نزدک ۱۰۰', NAS100: 'نزدک ۱۰۰',
  UK100: 'فوتسی ۱۰۰', DE40: 'دکس ۴۰', GER40: 'دکس ۴۰', JP225: 'نیکی ۲۲۵', FRA40: 'کک ۴۰',
  HK50: 'هنگ‌سنگ', AUS200: 'ASX 200', EU50: 'یوروستاکس ۵۰',
};
// نامِ کاملِ انگلیسیِ استانداردِ شاخص‌ها.
const INDEX_EN = {
  US30: 'Dow Jones 30', US500: 'S&P 500', US100: 'Nasdaq 100', NAS100: 'Nasdaq 100',
  UK100: 'FTSE 100', DE40: 'DAX 40', GER40: 'DAX 40', JP225: 'Nikkei 225', FRA40: 'CAC 40',
  HK50: 'Hang Seng', AUS200: 'ASX 200', EU50: 'Euro Stoxx 50',
};
const INDEX_CC = { US30: 'US', US500: 'US', US100: 'US', NAS100: 'US', UK100: 'GB', DE40: 'DE', GER40: 'DE', JP225: 'JP', FRA40: 'FR', HK50: 'HK', AUS200: 'AU', EU50: 'EU' };
// نام‌های نمایشیِ فارسیِ کریپتوهای موجود (پوششِ کاملِ لیستِ market-capِ فیدِ LBank).
const CRYPTO_FA = {
  BTC: 'بیت‌کوین', ETH: 'اتریوم', USDT: 'تتر', BNB: 'بایننس‌کوین', SOL: 'سولانا',
  XRP: 'ریپل', USDC: 'یواس‌دی‌کوین', ADA: 'کاردانو', DOGE: 'دوج‌کوین', TRX: 'ترون',
  TON: 'تون‌کوین', AVAX: 'آوالانچ', SHIB: 'شیبا اینو', LINK: 'چین‌لینک', DOT: 'پولکادات',
  BCH: 'بیت‌کوین‌کش', LTC: 'لایت‌کوین', NEAR: 'نیر', MATIC: 'پالیگان', UNI: 'یونی‌سواپ',
  ICP: 'اینترنت‌کامپیوتر', APT: 'اپتاس', XLM: 'استلار', ETC: 'اتریوم‌کلاسیک', FIL: 'فایل‌کوین',
  ATOM: 'کازماس', ARB: 'آربیتروم', OP: 'اپتیمیزم', INJ: 'اینجکتیو', SUI: 'سویی',
  PEPE: 'پپه', TIA: 'سلستیا', RNDR: 'رندر', IMX: 'ایمیوتبل‌ایکس', HBAR: 'هدرا',
  VET: 'وی‌چین', GRT: 'گراف', SEI: 'سِی', FTM: 'فانتوم', AAVE: 'آوه',
  ALGO: 'الگورند', FLOW: 'فلو', SAND: 'سندباکس', MANA: 'دیسنترالند', AXS: 'اکسی‌اینفینیتی',
  EGLD: 'مولتی‌ورس‌ایکس', XTZ: 'تِزوس', CHZ: 'چیلیز',
};
// نامِ کاملِ انگلیسیِ استانداردِ کریپتوها (برای پنلِ جزئیات/سرتیتر).
const CRYPTO_EN = {
  BTC: 'Bitcoin', ETH: 'Ethereum', USDT: 'Tether', BNB: 'BNB', SOL: 'Solana',
  XRP: 'XRP', USDC: 'USD Coin', ADA: 'Cardano', DOGE: 'Dogecoin', TRX: 'TRON',
  TON: 'Toncoin', AVAX: 'Avalanche', SHIB: 'Shiba Inu', LINK: 'Chainlink', DOT: 'Polkadot',
  BCH: 'Bitcoin Cash', LTC: 'Litecoin', NEAR: 'NEAR Protocol', MATIC: 'Polygon', UNI: 'Uniswap',
  ICP: 'Internet Computer', APT: 'Aptos', XLM: 'Stellar', ETC: 'Ethereum Classic', FIL: 'Filecoin',
  ATOM: 'Cosmos', ARB: 'Arbitrum', OP: 'Optimism', INJ: 'Injective', SUI: 'Sui',
  PEPE: 'Pepe', TIA: 'Celestia', RNDR: 'Render', IMX: 'Immutable', HBAR: 'Hedera',
  VET: 'VeChain', GRT: 'The Graph', SEI: 'Sei', FTM: 'Fantom', AAVE: 'Aave',
  ALGO: 'Algorand', FLOW: 'Flow', SAND: 'The Sandbox', MANA: 'Decentraland', AXS: 'Axie Infinity',
  EGLD: 'MultiversX', XTZ: 'Tezos', CHZ: 'Chiliz',
};
// —— پوششِ تکمیلیِ نمادها (نام‌های واقعیِ کاملِ نمادهای پرتقاضایِ غایب) —— فقط اضافه، بدونِ حذف.
// دستهٔ درست از همان classifyRaw می‌آید؛ این‌ها فقط نامِ نمایشی را کامل می‌کنند.
Object.assign(CRYPTO_FA, {
  WIF: 'داگ‌ویف‌هت', BONK: 'بونک', FLOKI: 'فلوکی', JUP: 'ژوپیتر', PYTH: 'پیث',
  STX: 'استکس', KAS: 'کاسپا', ORDI: 'اوردی', ENS: 'نامِ اتریوم', DYDX: 'دی‌وای‌دی‌ایکس',
  GALA: 'گالا', ENA: 'اتنا', ONDO: 'اوندو', JASMY: 'جاسمی', LDO: 'لیدو',
  MKR: 'میکر', RUNE: 'تورچین', FET: 'فچ‌ای‌آی', TAO: 'بیت‌تنسور', WLD: 'ورلدکوین',
  XMR: 'مونرو', ZEC: 'زی‌کش', DASH: 'دش', NEO: 'نئو', IOTA: 'آیوتا',
  THETA: 'تتا', EOS: 'ایاس', CAKE: 'پنکیک‌سواپ', CRV: 'کرو', COMP: 'کامپاند',
  SNX: 'سینتتیکس', GMX: 'جی‌ام‌ایکس', PENDLE: 'پندل', APE: 'ایپ‌کوین', LRC: 'لوپرینگ',
  KAVA: 'کاوا', ROSE: 'اویسیس', ONE: 'هارمونی', ZIL: 'زیلیکا', ANKR: 'انکر',
  LUNC: 'لوناکلاسیک', BTT: 'بیت‌تورنت', WIN: 'وین‌کوین', XEC: 'ای‌کش', CFX: 'کانفلاکس',
  STRK: 'استارک‌نت', W: 'ورم‌هول', TRB: 'تلور', GMT: 'استپن', MASK: 'مسک',
  AR: 'آرویو',
});
Object.assign(CRYPTO_EN, {
  WIF: 'dogwifhat', BONK: 'Bonk', FLOKI: 'FLOKI', JUP: 'Jupiter', PYTH: 'Pyth Network',
  STX: 'Stacks', KAS: 'Kaspa', ORDI: 'ORDI', ENS: 'Ethereum Name Service', DYDX: 'dYdX',
  GALA: 'Gala', ENA: 'Ethena', ONDO: 'Ondo', JASMY: 'JasmyCoin', LDO: 'Lido DAO',
  MKR: 'Maker', RUNE: 'THORChain', FET: 'Fetch.ai', TAO: 'Bittensor', WLD: 'Worldcoin',
  XMR: 'Monero', ZEC: 'Zcash', DASH: 'Dash', NEO: 'Neo', IOTA: 'IOTA',
  THETA: 'Theta Network', EOS: 'EOS', CAKE: 'PancakeSwap', CRV: 'Curve DAO', COMP: 'Compound',
  SNX: 'Synthetix', GMX: 'GMX', PENDLE: 'Pendle', APE: 'ApeCoin', LRC: 'Loopring',
  KAVA: 'Kava', ROSE: 'Oasis Network', ONE: 'Harmony', ZIL: 'Zilliqa', ANKR: 'Ankr',
  LUNC: 'Terra Classic', BTT: 'BitTorrent', WIN: 'WINkLink', XEC: 'eCash', CFX: 'Conflux',
  STRK: 'Starknet', W: 'Wormhole', TRB: 'Tellor', GMT: 'STEPN', MASK: 'Mask Network',
  AR: 'Arweave',
});
// دستهٔ کیوریت‌شدهٔ کوین‌های سرشناسِ کاتالوگِ زنده (LBank/TV) که نامِ فارسی نداشتند و desc خام (تیکر) می‌گرفتند.
// فقط کوین‌های شناخته‌شده با نامِ استانداردِ فارسی اضافه شدند؛ میم‌کوین‌های مبهم و توکن‌های سهامِ آن‌چین (…ON/…X) عمداً کنار گذاشته شدند.
Object.assign(CRYPTO_FA, {
  QNT: 'کوانت', OKB: 'اوکی‌بی', GT: 'گیت‌توکن', BGB: 'بیت‌گت', MNT: 'مانتل',
  CRO: 'کرونوس', KSM: 'کوساما', CELO: 'سلو', FLR: 'فلر', GLMR: 'مون‌بیم',
  MOVR: 'مون‌ریور', EIGEN: 'ایگن‌لیر', ETHFI: 'اتر‌فای', ENJ: 'انجین', HOT: 'هولو',
  IOTX: 'آیوتکس', QTUM: 'کوانتوم', RVN: 'ریون‌کوین', DGB: 'دیجی‌بایت', SUSHI: 'سوشی‌سواپ',
  YFI: 'یرن‌فایننس', CVX: 'کانوکس', '1INCH': 'وان‌اینچ', BLUR: 'بلور', MANTA: 'مانتا',
  PAXG: 'پکس‌گلد', WBTC: 'بیت‌کوینِ رَپ‌شده', XAUT: 'تتر‌گلد', NEXO: 'نکسو', STORJ: 'استورج',
  BERA: 'براچین', OSMO: 'اوسموسیس', JTO: 'جیتو', RAY: 'ریدیوم', ILV: 'ایلوویوم',
  YGG: 'ییلد‌گیلد', PEOPLE: 'کانستیتوشن‌دائو', BOME: 'بوک‌آف‌میم', TURBO: 'توربو', SAFE: 'سیف',
});
Object.assign(CRYPTO_EN, {
  QNT: 'Quant', OKB: 'OKB', GT: 'GateToken', BGB: 'Bitget Token', MNT: 'Mantle',
  CRO: 'Cronos', KSM: 'Kusama', CELO: 'Celo', FLR: 'Flare', GLMR: 'Moonbeam',
  MOVR: 'Moonriver', EIGEN: 'EigenLayer', ETHFI: 'Ether.fi', ENJ: 'Enjin Coin', HOT: 'Holo',
  IOTX: 'IoTeX', QTUM: 'Qtum', RVN: 'Ravencoin', DGB: 'DigiByte', SUSHI: 'SushiSwap',
  YFI: 'yearn.finance', CVX: 'Convex Finance', '1INCH': '1inch', BLUR: 'Blur', MANTA: 'Manta Network',
  PAXG: 'PAX Gold', WBTC: 'Wrapped Bitcoin', XAUT: 'Tether Gold', NEXO: 'Nexo', STORJ: 'Storj',
  BERA: 'Berachain', OSMO: 'Osmosis', JTO: 'Jito', RAY: 'Raydium', ILV: 'Illuvium',
  YGG: 'Yield Guild Games', PEOPLE: 'ConstitutionDAO', BOME: 'Book of Meme', TURBO: 'Turbo', SAFE: 'Safe',
});
// دستهٔ دومِ کیوریت‌شده (Loop #36) — آلت‌کوین‌های سرشناسِ کاتالوگ که هنوز نامِ فارسی نداشتند.
Object.assign(CRYPTO_FA, {
  NMR: 'نومریر', UMA: 'یو‌ام‌ای', MAGIC: 'مجیک', SSV: 'اس‌اس‌وی', WING: 'وینگ',
  BIGTIME: 'بیگ‌تایم', CHEQ: 'چک‌دی', PUFFER: 'پافر', MITO: 'میتوسیس', LUNA: 'ترا',
  FTT: 'اف‌تی‌تی', LINEA: 'لینِا', STG: 'استارگیت', HIGH: 'های‌استریت', JST: 'جاست',
  CETUS: 'ستوس', CKB: 'نروس', DEXE: 'دکسی', MLN: 'انزایم', AUDIO: 'آدیوس',
  HFT: 'هش‌فلو', DODO: 'دودو', TWT: 'تراست‌ولت', CELR: 'سلر', TLM: 'الین‌ورلدز',
  GAS: 'گس', ASTR: 'استار', ALICE: 'آلیس', CORE: 'کور', TAIKO: 'تایکو',
  XVS: 'ونوس', SYN: 'سیناپس', METIS: 'متیس', ORCA: 'اورکا', MORPHO: 'مورفو',
  SCRT: 'سکرت', MOODENG: 'مودنگ', RPL: 'راکت‌پول', ARKM: 'آرکهام', WOO: 'وو',
});
Object.assign(CRYPTO_EN, {
  NMR: 'Numeraire', UMA: 'UMA', MAGIC: 'Magic', SSV: 'ssv.network', WING: 'Wing Finance',
  BIGTIME: 'Big Time', CHEQ: 'Cheqd', PUFFER: 'Puffer Finance', MITO: 'Mitosis', LUNA: 'Terra',
  FTT: 'FTX Token', LINEA: 'Linea', STG: 'Stargate Finance', HIGH: 'Highstreet', JST: 'JUST',
  CETUS: 'Cetus Protocol', CKB: 'Nervos Network', DEXE: 'DeXe', MLN: 'Enzyme', AUDIO: 'Audius',
  HFT: 'Hashflow', DODO: 'DODO', TWT: 'Trust Wallet Token', CELR: 'Celer Network', TLM: 'Alien Worlds',
  GAS: 'Gas', ASTR: 'Astar', ALICE: 'My Neighbor Alice', CORE: 'Core', TAIKO: 'Taiko',
  XVS: 'Venus', SYN: 'Synapse', METIS: 'Metis', ORCA: 'Orca', MORPHO: 'Morpho',
  SCRT: 'Secret', MOODENG: 'Moo Deng', RPL: 'Rocket Pool', ARKM: 'Arkham', WOO: 'WOO Network',
});
// دستهٔ سومِ کیوریت‌شده (Loop #37) — فن‌توکن‌های باشگاهی/F1 (لوگوی برند دارند) + میم‌های سرشناسِ تلگرامی + چند آلتِ مطرح.
Object.assign(CRYPTO_FA, {
  BAR: 'بارسلونا', ASR: 'آ‌اس‌رم', ATM: 'اتلتیکو‌مادرید', LAZIO: 'لاتزیو', JUV: 'یوونتوس',
  PSG: 'پاری‌سن‌ژرمن', SANTOS: 'سانتوس', ALPINE: 'آلپاین', NOT: 'نات‌کوین', HMSTR: 'همستر‌کامبت',
  RENDER: 'رندر', VIRTUAL: 'ویرچوالز', PLUME: 'پلوم', KAIA: 'کایا', HEMI: 'همی',
  MANTRA: 'مانترا', MEW: 'میو', MAV: 'ماوریک', ID: 'اسپیس‌آیدی', POND: 'مارلین',
  RSR: 'ریزرو', USDD: 'یو‌اس‌دی‌دی', IOST: 'آی‌او‌اس‌تی', LPT: 'لایوپیر', JOE: 'تریدر‌جو',
  RIF: 'روتستاک', FUN: 'فان‌توکن', KAITO: 'کایتو', REZ: 'رنزو', MINA: 'مینا',
});
Object.assign(CRYPTO_EN, {
  BAR: 'FC Barcelona', ASR: 'AS Roma', ATM: 'Atlético Madrid', LAZIO: 'S.S. Lazio', JUV: 'Juventus',
  PSG: 'Paris Saint-Germain', SANTOS: 'Santos FC', ALPINE: 'Alpine F1 Team', NOT: 'Notcoin', HMSTR: 'Hamster Kombat',
  RENDER: 'Render', VIRTUAL: 'Virtuals Protocol', PLUME: 'Plume', KAIA: 'Kaia', HEMI: 'Hemi',
  MANTRA: 'MANTRA', MEW: 'cat in a dogs world', MAV: 'Maverick Protocol', ID: 'SPACE ID', POND: 'Marlin',
  RSR: 'Reserve Rights', USDD: 'USDD', IOST: 'IOST', LPT: 'Livepeer', JOE: 'Trader Joe',
  RIF: 'Rootstock', FUN: 'FUN Token', KAITO: 'Kaito', REZ: 'Renzo', MINA: 'Mina Protocol',
});
// اعشارِ استانداردِ فلزات (اونسِ ترویِ TradingView): طلا/پلاتین/پالادیوم ۲، نقره ۳، مس ۴.
const METAL_DIGITS = { XAU: 2, XAG: 3, XPT: 2, XPD: 2, XCU: 4 };
// اعشارِ ثابتِ کریپتوهای گران/میان‌قیمت (وقتی قیمتِ زنده در دست نیست، مثلِ مدالِ جستجو).
// کوین‌های ریزقیمت (SHIB/PEPE/…) عمداً این‌جا نیستند تا در زمانِ اجرا بر پایهٔ بزرگیِ قیمت
// تعیین شوند (سبکِ نردبانِ TV)؛ نگاشتِ ثابت فقط پیش‌فرضِ منطقیِ بدونِ قیمت است.
const CRYPTO_DIGITS = {
  // گران‌ها → ۲ رقم عینِ TV (BTCUSD/ETHUSD)
  BTC: 2, ETH: 2, BNB: 2, SOL: 2, LTC: 2, BCH: 2, AVAX: 2, LINK: 2, DOT: 2, ATOM: 2,
  NEAR: 2, AAVE: 2, ICP: 2, EGLD: 2, TIA: 2, INJ: 2, ETC: 2, TON: 2, APT: 2, SUI: 2,
  UNI: 2, FIL: 2, OP: 2, ARB: 2, IMX: 2, MKR: 2, TAO: 2, XMR: 2, ZEC: 2, DASH: 2,
  NEO: 2, LDO: 2, RUNE: 2, GMX: 2, ORDI: 2, WLD: 2, KAS: 2, THETA: 2, COMP: 2, RNDR: 2,
  // میان‌قیمت‌ها → ۳ رقم
  XTZ: 3, ALGO: 3, FTM: 3, SEI: 3, PENDLE: 3, KAVA: 3, EOS: 3, STX: 3, CFX: 3, PYTH: 3,
  ENA: 3, W: 3, STRK: 3, JUP: 3, DYDX: 3, FET: 3, CAKE: 3, GALA: 3, MASK: 3, APE: 3, TRB: 3,
  // ریزها → ۴ رقم (کف)؛ در زمانِ اجرا با قیمت دقیق‌تر می‌شوند
  ADA: 4, XRP: 4, TRX: 4, DOGE: 4, MATIC: 4, XLM: 4, HBAR: 4, VET: 5, GRT: 4, SAND: 4,
  MANA: 4, AXS: 4, FLOW: 4, CHZ: 5, CRV: 4, SNX: 4, LRC: 5, ANKR: 5, ZIL: 5, ONE: 5,
  IOTA: 4, JASMY: 6, ONDO: 4, WIF: 3, ROSE: 5,
  // میکروها → ۸ رقم
  SHIB: 8, PEPE: 8, BONK: 8, FLOKI: 6, LUNC: 6, BTT: 8, WIN: 8, XEC: 6,
};
// محبوب‌ها برای رتبهٔ پیش‌فرض
const POPULAR = new Set(['EURUSD', 'GBPUSD', 'USDJPY', 'XAUUSD', 'XAGUSD', 'XTIUSD', 'BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'XRPUSDT', 'BTCUSD', 'ETHUSD', 'US30', 'US500', 'NAS100', 'DE40', 'AUDUSD', 'USDCAD', 'USDCHF', 'GBPJPY']);

// —— متادیتای تکمیلی برای پنلِ جزئیات (#۸): جلسهٔ معاملاتی + منطقهٔ زمانی + اندازهٔ قرارداد/تیک ——
// جلسهٔ معاملاتیِ پیش‌فرضِ هر دسته (فارکس/کریپتو/فلز/انرژی ۲۴ساعته‌اند؛ شاخص‌ها ساعتِ بورسِ خود).
const SESSION = {
  forex: { session: '۲۴ ساعته · یکشنبه ۲۲:۰۰ تا جمعه ۲۲:۰۰', tz: 'UTC' },
  crypto: { session: '۲۴/۷ · بدونِ تعطیلی', tz: 'UTC' },
  metal: { session: '۲۴ ساعته · یکشنبه ۲۳:۰۰ تا جمعه ۲۲:۰۰', tz: 'UTC' },
  energy: { session: '۲۴ ساعته · یکشنبه ۲۳:۰۰ تا جمعه ۲۲:۰۰', tz: 'America/New_York' },
  other: { session: '—', tz: 'UTC' },
};
// جلسهٔ بورسِ هر شاخص (ساعتِ محلیِ همان بورس) بر پایهٔ کشورِ INDEX_CC.
const INDEX_SESSION = {
  US: { session: '۰۹:۳۰ تا ۱۶:۰۰', tz: 'America/New_York' },
  GB: { session: '۰۸:۰۰ تا ۱۶:۳۰', tz: 'Europe/London' },
  DE: { session: '۰۹:۰۰ تا ۱۷:۳۰', tz: 'Europe/Berlin' },
  FR: { session: '۰۹:۰۰ تا ۱۷:۳۰', tz: 'Europe/Paris' },
  JP: { session: '۰۹:۰۰ تا ۱۵:۰۰', tz: 'Asia/Tokyo' },
  HK: { session: '۰۹:۳۰ تا ۱۶:۰۰', tz: 'Asia/Hong_Kong' },
  AU: { session: '۱۰:۰۰ تا ۱۶:۰۰', tz: 'Australia/Sydney' },
  EU: { session: '۰۹:۰۰ تا ۱۷:۳۰', tz: 'Europe/Berlin' },
};
// اندازهٔ قرارداد + کمینه‌تیکِ هر فلز (اونسِ ترویِ استاندارد).
const METAL_SPEC = {
  XAU: { contract: '۱۰۰ اونس', tick: 0.01 },
  XAG: { contract: '۵٬۰۰۰ اونس', tick: 0.001 },
  XPT: { contract: '۵۰ اونس', tick: 0.1 },
  XPD: { contract: '۱۰۰ اونس', tick: 0.01 },
};

function up(s) { return String(s || '').toUpperCase(); }
// تبدیلِ مطمئنِ mid (رشته/عدد) به عدد یا null.
function toNum(v) { const n = typeof v === 'number' ? v : parseFloat(v); return Number.isFinite(n) ? n : null; }
// اعشارِ کریپتو بر پایهٔ بزرگیِ قیمت (سبکِ نردبانِ TradingView): هرچه ارزان‌تر، ریزتر.
function magnitudeDigits(n) {
  const x = Math.abs(n);
  if (x >= 1) return 2;
  if (x >= 0.1) return 4;
  if (x >= 0.001) return 5;
  if (x > 0) return 8;
  return 2;
}
// اعشارِ ثابتِ دستهٔ نماد (مستقل از قیمت). کریپتو/سایر ⇒ null (یعنی «به قیمت واگذار شود»).
function fixedDigits(m) {
  if (!m) return null;
  if (m.cat === 'forex') return m.quote === 'JPY' ? 3 : 5;
  if (m.cat === 'metal') return METAL_DIGITS[m.base] != null ? METAL_DIGITS[m.base] : 2;
  if (m.cat === 'energy') return m.base === 'NGAS' ? 3 : 2;
  if (m.cat === 'index') return 2;
  return null; // crypto / other → مبتنی بر بزرگیِ قیمت یا نگاشتِ ثابتِ کریپتو
}
/**
 * منبعِ واحدِ حقیقتِ «اعشارِ درستِ هر نماد» برای فرمتِ قیمت (مورد ۷۶ و ۱۵۰).
 * فارکس ۵ (ین ۳)، طلا/پلاتین/پالادیوم ۲، نقره ۳، مس ۴، نفت ۲، گاز ۳، شاخص ۲،
 * کریپتو: اگر قیمتِ زنده بدهی بر پایهٔ بزرگیِ آن (بهترین حالت)، وگرنه نگاشتِ ثابتِ کریپتو، وگرنه ۲.
 * @param {string} symbol نمادِ خام (EURUSD, XAUUSD, BTCUSDT, PEPEUSDT, …)
 * @param {number|string} [mid] قیمتِ زنده (اختیاری) — برای دقتِ per-symbolِ کریپتو
 * @returns {number} تعدادِ ارقامِ اعشار
 */
export function priceDigits(symbol, mid) {
  const m = classifyRaw(symbol);
  const fx = fixedDigits(m);
  if (fx != null) return fx;
  // کریپتو/سایر: قیمتِ زنده اولویتِ اول (سبکِ نردبانِ TV)
  const n = toNum(mid);
  if (n != null) return magnitudeDigits(n);
  if (m.cat === 'crypto' && CRYPTO_DIGITS[m.base] != null) return CRYPTO_DIGITS[m.base];
  return 2;
}
/**
 * payloadِ آمادهٔ priceFormat برای lightweight-charts (type:'price') بر پایهٔ اعشارِ درستِ نماد.
 * @param {string} symbol نماد
 * @param {number|string} [mid] قیمتِ زنده (اختیاری)
 * @returns {{type:'price',precision:number,minMove:number}}
 */
export function priceFormatFor(symbol, mid) {
  const d = priceDigits(symbol, mid);
  // minMove دقیق (بدونِ خطای شناور مثلِ Math.pow(10,-5)=0.0000099…): 10^-d از رشتهٔ نمایی.
  return { type: 'price', precision: d, minMove: d > 0 ? parseFloat('1e-' + d) : 1 };
}

// نامِ کاملِ انگلیسیِ استانداردِ نماد (سبکِ سرتیترِ TradingView: «Euro / US Dollar»). نبودِ نگاشت ⇐ خودِ نماد.
function nameEnOf(m) {
  if (m.cat === 'forex') return `${CCY_EN[m.base] || m.base} / ${CCY_EN[m.quote] || m.quote}`;
  if (m.cat === 'metal') return (METAL_EN[m.base] || m.base) + (m.quote && CCY_EN[m.quote] ? ' / ' + CCY_EN[m.quote] : '');
  if (m.cat === 'index') return INDEX_EN[m.indexKey] || m.symbol;
  if (m.cat === 'energy') return m.enName || 'Energy';
  if (m.cat === 'crypto') return (CRYPTO_EN[m.base] || m.base) + (m.quote ? ' / ' + m.quote : '');
  return m.symbol;
}

// افزودنِ فیلدهای تکمیلی (name/nameEn/session/tz/contract/tick/pip) روی متادیتای پایه — فقط اضافه، بدون تغییرِ فیلدهای موجود.
function enrich(m) {
  // نامِ کاملِ خوانا برای پنلِ جزئیات (همان توضیحِ فارسیِ ساخته‌شده).
  m.name = m.desc;
  // نامِ کاملِ انگلیسیِ استاندارد (برای سرتیترِ دووجهیِ فارسی/انگلیسیِ سبکِ TradingView).
  m.nameEn = nameEnOf(m);
  // جلسهٔ معاملاتی + منطقهٔ زمانی
  const sess = m.cat === 'index' ? (INDEX_SESSION[m.country] || INDEX_SESSION.US) : (SESSION[m.cat] || SESSION.other);
  m.session = sess.session;
  m.tz = sess.tz;
  // اندازهٔ قرارداد + کمینه‌تیک (مقادیرِ رایجِ بروکرها؛ نمایشی برای پنلِ مشخصات)
  if (m.cat === 'forex') {
    m.contract = '۱۰۰٬۰۰۰ ' + (m.base || '');
    m.tick = m.quote === 'JPY' ? 0.001 : 0.00001;
    m.pip = m.quote === 'JPY' ? 0.01 : 0.0001;
  } else if (m.cat === 'metal') {
    const spec = METAL_SPEC[m.base] || { contract: '—', tick: 0.01 };
    m.contract = spec.contract; m.tick = spec.tick;
  } else if (m.cat === 'index') {
    m.contract = '۱ واحد به‌ازای هر پوینت'; m.tick = 0.1;
  } else if (m.cat === 'energy') {
    // گاز طبیعی: قراردادِ ۱۰٬۰۰۰ MMBtu؛ نفت (WTI/برنت): ۱٬۰۰۰ بشکه (هم‌ترازِ instruments.py).
    const isGas = m.base === 'NGAS';
    m.contract = isGas ? '۱۰٬۰۰۰ MMBtu' : '۱٬۰۰۰ بشکه';
    m.tick = isGas ? 0.001 : 0.01;
  } else if (m.cat === 'crypto') {
    m.contract = '۱ ' + (m.base || '');
    // تیکِ کریپتو هم‌ترازِ اعشارِ ثابتِ همان کوین (نبودِ نگاشت ⇒ ۲ رقم).
    const cd = CRYPTO_DIGITS[m.base] != null ? CRYPTO_DIGITS[m.base] : 2;
    m.tick = parseFloat('1e-' + cd);
  } else {
    m.contract = '—'; m.tick = 0.01;
  }
  // اعشارِ درستِ نماد برای فرمتِ قیمت (مورد ۱۵۰) — بدونِ قیمتِ زنده، پیش‌فرضِ منطقیِ دسته/کوین.
  m.digits = priceDigits(m.symbol);
  return m;
}

export function classify(symRaw) {
  return enrich(classifyRaw(symRaw));
}

// ── سهام/ETFِ TV-دارای‌لوگو (هم‌ترازِ STOCKS_TOP بک‌اند + STOCK_LOGO در SymbolLogo). ──
const STOCKS = new Set([
  'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'META', 'TSLA', 'AVGO', 'NFLX', 'AMD',
  'INTC', 'CRM', 'ORCL', 'ADBE', 'CSCO', 'QCOM', 'TXN', 'AMAT', 'IBM', 'MU',
  'ARM', 'PLTR', 'SMCI', 'DELL', 'UBER', 'PYPL', 'DIS', 'BABA', 'KO', 'PEP',
  'MCD', 'NKE', 'V', 'MA', 'JPM', 'BAC', 'WMT', 'COST', 'PFE', 'BA',
  'GS', 'SBUX', 'COIN', 'SHOP', 'ABNB', 'MSTR', 'HOOD',
  'SPY', 'QQQ', 'IWM', 'DIA', 'GLD',
]);
// نامِ نمایشیِ سهام (برای desc در لیست/سرچ) — انگلیسی، شناخته‌شده.
const STOCK_FA = {
  AAPL: 'Apple', MSFT: 'Microsoft', GOOGL: 'Alphabet (Google)', AMZN: 'Amazon', NVDA: 'NVIDIA',
  META: 'Meta Platforms', TSLA: 'Tesla', AVGO: 'Broadcom', NFLX: 'Netflix', AMD: 'AMD',
  INTC: 'Intel', CRM: 'Salesforce', ORCL: 'Oracle', ADBE: 'Adobe', CSCO: 'Cisco',
  QCOM: 'Qualcomm', TXN: 'Texas Instruments', AMAT: 'Applied Materials', IBM: 'IBM', MU: 'Micron',
  ARM: 'Arm Holdings', PLTR: 'Palantir', SMCI: 'Super Micro', DELL: 'Dell', UBER: 'Uber',
  PYPL: 'PayPal', DIS: 'Disney', BABA: 'Alibaba', KO: 'Coca-Cola', PEP: 'PepsiCo',
  MCD: "McDonald's", NKE: 'Nike', V: 'Visa', MA: 'Mastercard', JPM: 'JPMorgan Chase',
  BAC: 'Bank of America', WMT: 'Walmart', COST: 'Costco', PFE: 'Pfizer', BA: 'Boeing',
  GS: 'Goldman Sachs', SBUX: 'Starbucks', COIN: 'Coinbase', SHOP: 'Shopify', ABNB: 'Airbnb',
  MSTR: 'Strategy (MicroStrategy)', HOOD: 'Robinhood',
  SPY: 'SPDR S&P 500 ETF', QQQ: 'Invesco QQQ ETF', IWM: 'iShares Russell 2000 ETF',
  DIA: 'SPDR Dow Jones ETF', GLD: 'SPDR Gold Shares',
};

function classifyRaw(symRaw) {
  const sym = up(symRaw);
  const clean = sym.replace(/[^A-Z0-9]/g, '');
  // فلز در برابر دلار
  for (const m of Object.keys(METAL)) {
    if (clean.startsWith(m)) {
      const q = clean.slice(m.length, m.length + 3);
      return {
        symbol: sym, cat: 'metal', base: m, quote: q || 'USD',
        desc: `${METAL[m][0]}${q && CCY_FA[q] ? (' / ' + CCY_FA[q]) : ''}`,
      };
    }
  }
  // انرژی — نامِ درستِ ابزار (WTI / برنت / گاز طبیعی) به‌جای برچسبِ عمومی؛ همه USD-مظنه‌اند.
  if (ENERGY.test(clean)) {
    const e = ENERGY_MAP.find((x) => x.re.test(clean)) || { fa: 'نفت/انرژی', en: 'Energy', base: 'OIL' };
    return { symbol: sym, cat: 'energy', base: e.base, quote: 'USD', desc: e.fa, enName: e.en, country: 'US' };
  }
  // شاخص
  if (INDEX.test(clean)) {
    const key = Object.keys(INDEX_FA).find((k) => clean.startsWith(k)) || clean.slice(0, 5);
    return { symbol: sym, cat: 'index', indexKey: key, desc: INDEX_FA[key] || sym, country: INDEX_CC[key] || 'US' };
  }
  // جفت‌ارزِ فیات
  if (clean.length >= 6) {
    const a = clean.slice(0, 3), b = clean.slice(3, 6);
    if (CCY.includes(a) && CCY.includes(b)) {
      return { symbol: sym, cat: 'forex', base: a, quote: b, desc: `${CCY_FA[a]} / ${CCY_FA[b]}` };
    }
  }
  // کریپتو (پسوندِ USDT/USDC/USD/BTC/ETH)
  const base = clean.replace(/(USDT|USDC|USD|BTC|ETH)$/, '');
  if (base && base !== clean) {
    const quote = clean.slice(base.length);
    return { symbol: sym, cat: 'crypto', base, quote, desc: CRYPTO_FA[base] ? `${CRYPTO_FA[base]} (${base})` : base };
  }
  // سهام/ETF (تیکرِ آلفا؛ فقط مجموعهٔ TV-دارای‌لوگو که فیدِ قیمت/کندل دارد) — قبل از fallbackِ «other».
  if (STOCKS.has(clean)) {
    return { symbol: sym, cat: 'stock', base: clean, quote: 'USD', desc: STOCK_FA[clean] || clean, enName: clean, country: 'US' };
  }
  return { symbol: sym, cat: 'other', desc: sym };
}

export function buildMeta(list) {
  return (list || []).map((s) => { const m = classify(s); m.popular = POPULAR.has(up(s).replace(/[^A-Z0-9]/g, '')); return m; });
}

// ————————————————————————————————————————————————————————————————————————
// «جهانِ نمادِ تمیز» (مورد ۶۰/۱۴۹): فقط نمادهایی که لوگوی «واقعی» دارند بمانند و
// سهام‌های تصادفیِ بی‌لوگو (مونوگرامِ خاکستری) از واچ‌لیست/سرچ حذف شوند.
// تعریفِ تمیز — هم‌ترازِ گلیف‌های SymbolLogo.jsx (بدونِ importِ متقابل تا کوپلینگ نشود):
//   • فارکس: هر دو طرفِ جفت از ۸ ارزِ اصلی (میجر/مینور/کراس) که پرچمِ واقعی دارند.
//   • فلز: طلا/نقره/پلاتین/پالادیوم (شمشِ واقعی) — مس (XCU) لوگو ندارد ⇒ حذف.
//   • شاخص: کلیدِ شناخته‌شدهٔ INDEX_FA (بَجِ برنددار).
//   • انرژی: WTI/برنت/گاز طبیعی (بَجِ برنددار).
//   • کریپتو: تنها کوین‌های تاپِ فهرست‌شده (آیکونِ واقعی) — بقیه حذف.
// ۸ ارزِ اصلی که در SymbolLogo پرچمِ واقعیِ SVG دارند (میجرها). خارج از این ⇒ اگزوتیک/بی‌پرچم.
const CORE_CCY = new Set(['USD', 'EUR', 'GBP', 'JPY', 'CHF', 'CAD', 'AUD', 'NZD']);
// فلزاتِ دارای شمشِ واقعی (مس بیرون است).
const CLEAN_METAL = new Set(['XAU', 'XAG', 'XPT', 'XPD']);
// کوین‌های تاپِ دارای لوگوی واقعی = همان فهرستِ کیوریت‌شدهٔ نام‌های کامل (CRYPTO_FA).
const CLEAN_CRYPTO = new Set(Object.keys(CRYPTO_FA));

/**
 * آیا نماد در «جهانِ نمادِ تمیز» است؟ (لوگوی واقعی دارد)
 * برای فیلترِ حذفِ سهام‌های تصادفیِ بی‌لوگو در RightPanel/واچ‌لیست و SymbolSearch.
 * @param {string} symbol نمادِ خام (EURUSD, XAUUSD, BTCUSDT, AAPL, …)
 * @returns {boolean} true = بماند (لوگوی واقعی)، false = حذف (بی‌لوگو)
 */
export function isCleanSymbol(symbol) {
  const m = classifyRaw(symbol);
  switch (m.cat) {
    case 'forex': return CORE_CCY.has(m.base) && CORE_CCY.has(m.quote);
    case 'metal': return CLEAN_METAL.has(m.base);
    case 'index': return INDEX_FA[m.indexKey] != null;
    case 'energy': return true;
    // کریپتو: بک‌اند دقیقاً «۱۰۰ کوینِ برترِ کیوریت‌شده» را برمی‌گرداند، پس همه را نگه می‌داریم
    //   (کوین‌های خارج از CRYPTO_FA بَجِ رنگی/تیکر می‌گیرند تا هیچ‌کدام از ۱۰۰ تای برتر حذف نشوند).
    case 'crypto': return true;
    case 'stock': return true; // سهام/ETFِ TV-دارای‌لوگو (فیدِ finnhub + yfinance)
    default: return false; // 'other' = سهامِ تصادفیِ بی‌لوگو
  }
}

/**
 * فیلترِ آرایهٔ نمادهای خام (رشته‌ای) به جهانِ تمیز. ترتیبِ ورودی حفظ می‌شود.
 * مصرف در RightPanel/Watchlist: `filterClean(watch)` و `filterClean(symbols)`.
 * @param {string[]} list
 * @returns {string[]}
 */
export function filterClean(list) {
  return (list || []).filter(isCleanSymbol);
}

/**
 * فیلترِ آرایهٔ متادیتا (خروجیِ buildMeta) به جهانِ تمیز — برای metaListِ SymbolSearch.
 * @param {Array<{symbol:string}>} metaList
 * @returns {Array<{symbol:string}>}
 */
export function filterCleanMeta(metaList) {
  return (metaList || []).filter((m) => m && isCleanSymbol(m.symbol));
}

export const CATEGORIES = [
  { id: 'all', label: 'همه' },
  { id: 'forex', label: 'فارکس' },
  { id: 'crypto', label: 'کریپتو' },
  { id: 'metal', label: 'فلزات' },
  { id: 'index', label: 'شاخص‌ها' },
  { id: 'energy', label: 'انرژی' },
];

// برچسبِ فارسیِ دسته برای نمایش در ردیفِ نتیجه
export const CAT_FA = { forex: 'فارکس', crypto: 'کریپتو', metal: 'فلز', index: 'شاخص', energy: 'انرژی', other: 'سایر' };
