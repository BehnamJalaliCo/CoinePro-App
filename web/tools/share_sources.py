#!/usr/bin/env python3
"""The phone's Kotlin sources, prepared for the browser build — web/build.gradle.kts `shareSources`.

The web terminal compiles the Android modules' own `src/main/kotlin`. Three things in them assume a
JVM and cannot be met by a stand-in class alone, so this copies the sources into the build directory
and makes exactly these edits, nothing else:

1. **Wire classes.** Gson reads fields by reflection; a Wasm binary has none. Every class the phone
   sends or receives over HTTP — the return and `@Body` types of its Retrofit services, what it hands
   to `Gson.fromJson`, and everything those reach through their constructors — is marked
   `@Serializable`. `@SerializedName` becomes `@SerialName`/`@JsonNames`; every other field gets its
   `LOWER_CASE_WITH_UNDERSCORES` name (the one policy `NetworkFactory` configures) with its own name
   as an alternate. A non-null field with no default gets the zero Gson would have left in it.
2. **Retrofit services.** Retrofit builds a service from an interface by reflection. Each service
   interface gets a `<Name>Web` implementation written into the same file, and
   `.create(Name::class.java)` becomes `.let { NameWeb(it) }`.
3. **Blocking I/O.** A page cannot block. `override fun intercept` becomes `suspend`, and OkHttp's
   `.execute()` becomes the stand-in's suspending `.await()`.

Usage: share_sources.py <repo> <out> <module,…> <excluded relative path,…>
"""

import json
import os
import re
import shutil
import sys

# ── Lexing ───────────────────────────────────────────────────────────────────────────────────────


def skip_string(text, i):
    """i at an opening quote; returns the index after the literal."""
    if text.startswith('"""', i):
        j = text.find('"""', i + 3)
        return len(text) if j < 0 else j + 3
    q = text[i]
    j = i + 1
    while j < len(text):
        c = text[j]
        if c == '\\':
            j += 2
            continue
        if c == q:
            return j + 1
        if q == '"' and c == '$' and j + 1 < len(text) and text[j + 1] == '{':
            j = match(text, j + 1) + 1
            continue
        j += 1
    return j


def skip_comment(text, i):
    if text.startswith('//', i):
        j = text.find('\n', i)
        return len(text) if j < 0 else j
    if text.startswith('/*', i):
        depth = 0
        j = i
        while j < len(text):
            if text.startswith('/*', j):
                depth += 1
                j += 2
            elif text.startswith('*/', j):
                depth -= 1
                j += 2
                if depth == 0:
                    return j
            else:
                j += 1
        return j
    return i


REPO = '.'
GLYPHS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'glyphs.json'), encoding='utf-8'))['map']


def rel_is_data(key):
    """Files whose non-Latin literals are wire data rather than words on screen."""
    return key[1].endswith('CommunityModels.kt')

PAIRS = {'(': ')', '[': ']', '{': '}', '<': '>'}


def match(text, i):
    """i at an opener; returns the index of its closer. `<` only nests with `<`."""
    opener = text[i]
    closer = PAIRS[opener]
    depth = 0
    j = i
    while j < len(text):
        c = text[j]
        if c in '"\'':
            j = skip_string(text, j)
            continue
        if text.startswith('//', j) or text.startswith('/*', j):
            j = skip_comment(text, j)
            continue
        if opener == '<':
            if c == '<':
                depth += 1
            elif c == '>' and text[j - 1] != '-':
                depth -= 1
                if depth == 0:
                    return j
            elif c in '({[':
                j = match(text, j)
        else:
            if c in '([{':
                if c == opener:
                    depth += 1
                else:
                    j = match(text, j)
            elif c == closer:
                depth -= 1
                if depth == 0:
                    return j
        j += 1
    return len(text) - 1


def split_top(text, sep=','):
    """Splits on `sep` outside brackets, strings and comments. Returns (start, end) spans."""
    spans = []
    start = 0
    j = 0
    while j < len(text):
        c = text[j]
        if c in '"\'':
            j = skip_string(text, j)
            continue
        if text.startswith('//', j) or text.startswith('/*', j):
            j = skip_comment(text, j)
            continue
        if c in '([{':
            j = match(text, j) + 1
            continue
        if c == '<' and re.match(r'<\s*[\w*?]', text[j:]) and j > 0 and re.match(r'[\w?]', text[j - 1]):
            j = match(text, j) + 1
            continue
        if c == sep:
            spans.append((start, j))
            start = j + 1
        j += 1
    spans.append((start, len(text)))
    return spans


def in_literals(text, mapping):
    """[text] with [mapping] applied inside string literals only — never to a comment or to code."""
    out = []
    j = 0
    while j < len(text):
        c = text[j]
        if c in '"\'':
            k = skip_string(text, j)
            literal = text[j:k]
            for missing, present in mapping.items():
                # The character itself, and the same character written as a `\\uXXXX` escape.
                literal = literal.replace(missing, present)
                if len(missing) == 1:
                    literal = re.sub(r'\\u(?i:%04x)' % ord(missing), lambda m: present, literal)
            out.append(literal)
            j = k
            continue
        if text.startswith('//', j) or text.startswith('/*', j):
            k = skip_comment(text, j)
            out.append(text[j:k])
            j = k
            continue
        out.append(c)
        j += 1
    return ''.join(out)


def strip_comments(text):
    out = []
    j = 0
    while j < len(text):
        c = text[j]
        if c in '"\'':
            k = skip_string(text, j)
            out.append(text[j:k])
            j = k
            continue
        if text.startswith('//', j) or text.startswith('/*', j):
            j = skip_comment(text, j)
            out.append(' ')
            continue
        out.append(c)
        j += 1
    return ''.join(out)


# ── Declarations ─────────────────────────────────────────────────────────────────────────────────

CLASS_RE = re.compile(
    r'(?m)^([ \t]*)((?:@[\w.]+(?:\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\))?[ \t]*\n?[ \t]*)*)'
    r'((?:(?:public|private|internal|protected|data|enum|sealed|open|abstract|inner|value|final)\s+)*)'
    r'(class|interface)\s+(\w+)'
)

BUILTINS = {
    'String', 'Int', 'Long', 'Double', 'Float', 'Boolean', 'Short', 'Byte', 'Char', 'Unit', 'Any', 'Nothing',
    'List', 'MutableList', 'ArrayList', 'Map', 'MutableMap', 'HashMap', 'LinkedHashMap', 'Set', 'MutableSet',
    'HashSet', 'LinkedHashSet', 'Collection', 'Iterable', 'Array', 'IntArray', 'LongArray', 'DoubleArray',
    'FloatArray', 'BooleanArray', 'ByteArray', 'Pair', 'Triple', 'Response', 'JsonElement', 'JsonObject',
    'JsonArray', 'JsonPrimitive', 'JsonNull', 'ResponseBody', 'RequestBody', 'MultipartBody', 'Part',
}


class Decl:
    def __init__(self, path, text, m):
        self.path = path
        self.indent = m.group(1)
        self.annotations = m.group(2)
        self.modifiers = m.group(3).split()
        self.kind = m.group(4)
        self.name = m.group(5)
        self.start = m.start()
        self.keyword_at = m.start(4)
        self.name_end = m.end(5)
        self.params_span = None  # (open, close) of the primary constructor
        self.body_span = None
        j = self.name_end
        j = skip_ws(text, j)
        if j < len(text) and text[j] == '<':
            j = match(text, j) + 1
        # `private constructor`, `@Inject constructor`, annotations
        k = skip_ws(text, j)
        cm = re.match(r'((?:@[\w.]+(?:\([^)]*\))?\s*)*(?:(?:private|internal|public|protected)\s+)?constructor\s*)', text[k:])
        if cm and 'constructor' in cm.group(1):
            k += len(cm.group(1))
        k = skip_ws(text, k)
        if k < len(text) and text[k] == '(' and self.kind == 'class':
            self.params_span = (k, match(text, k))
            k = self.params_span[1] + 1
        # supertypes, then a body
        e = k
        while e < len(text):
            c = text[e]
            if c in '"\'':
                e = skip_string(text, e)
                continue
            if text.startswith('//', e) or text.startswith('/*', e):
                e = skip_comment(text, e)
                continue
            if c in '(<':
                e = match(text, e) + 1
                continue
            if c == '{':
                self.body_span = (e, match(text, e))
                break
            if c in ')};=':
                break
            if c == '\n':
                before = text[:e].rstrip()
                nxt = skip_ws(text, e)
                if not before.endswith((':', ',')) and (nxt >= len(text) or text[nxt] not in ':,{'):
                    break
            e += 1

    @property
    def is_enum(self):
        return 'enum' in self.modifiers

    @property
    def visibility(self):
        for v in ('private', 'internal', 'public', 'protected'):
            if v in self.modifiers:
                return v
        return 'public'


def skip_ws(text, j):
    while j < len(text):
        if text[j] in ' \t\r\n':
            j += 1
        elif text.startswith('//', j) or text.startswith('/*', j):
            j = skip_comment(text, j)
        else:
            break
    return j


def declarations(path, text):
    out = []
    for m in CLASS_RE.finditer(text):
        # Not inside a string or a comment: cheap check on the line prefix.
        line_start = text.rfind('\n', 0, m.start(4)) + 1
        prefix = text[line_start:m.start(4)]
        if '//' in prefix or prefix.lstrip().startswith('*'):
            continue
        if 'fun ' in prefix or '=' in prefix:
            continue
        out.append(Decl(path, text, m))
    return out


def type_names(type_text):
    return set(re.findall(r'\b([A-Z]\w*)\b', strip_comments(type_text)))


class Param:
    """One primary-constructor parameter or one function parameter."""

    def __init__(self, raw):
        self.raw = raw
        clean = strip_comments(raw)
        self.annotations = re.findall(r'@([\w.]+)(\((?:[^()]|\([^()]*\))*\))?', clean.split(':')[0] if ':' in clean else clean)
        m = re.search(r'(?:^|\s)(?:(val|var)\s+)?(`?\w+`?)\s*:\s*', clean)
        self.binding = m.group(1) if m else None
        self.name = m.group(2) if m else None
        rest = clean[m.end():] if m else ''
        parts = split_top(rest, '=')
        self.type = rest[parts[0][0]:parts[0][1]].strip() if m else ''
        self.default = rest[parts[1][0]:].strip() if m and len(parts) > 1 else None


def params_of(text, span):
    inner = text[span[0] + 1:span[1]]
    out = []
    for s, e in split_top(inner):
        raw = inner[s:e]
        if strip_comments(raw).strip():
            out.append((span[0] + 1 + s, span[0] + 1 + e, Param(raw)))
    return out


# ── Wire classes ─────────────────────────────────────────────────────────────────────────────────


def gson_snake(name):
    out = []
    for c in name:
        if c.isupper() and out:
            out.append('_')
        out.append(c.lower())
    return ''.join(out)


ZERO = {
    'Int': '0', 'Long': '0L', 'Double': '0.0', 'Float': '0f', 'Boolean': 'false', 'String': '""',
    'Short': '0', 'Byte': '0',
}


def zero_for(type_text):
    t = type_text.strip()
    if t.endswith('?'):
        return None
    if t in ZERO:
        return ZERO[t]
    base = t.split('<')[0].strip()
    if base in ('List', 'Collection', 'Iterable'):
        return 'emptyList()'
    if base in ('Map',):
        return 'emptyMap()'
    if base in ('Set',):
        return 'emptySet()'
    return None


def serialized_name(annotations):
    for name, args in annotations:
        if name.split('.')[-1] == 'SerializedName' and args:
            strings = re.findall(r'"((?:[^"\\]|\\.)*)"', args)
            if strings:
                return strings[0], strings[1:]
    return None


def transform_param(raw, param):
    """The parameter text with its wire names in kotlinx form, and the Gson zero where it had none."""
    names = serialized_name(param.annotations)
    text = re.sub(r'@(?:com\.google\.gson\.annotations\.)?SerializedName\((?:[^()]|\([^()]*\))*\)\s*', '', raw)
    bare = param.name.strip('`')
    if names:
        serial, alternates = names
    else:
        serial, alternates = gson_snake(bare), []
    if serial != bare and bare not in alternates:
        alternates = alternates + [bare]
    note = ''
    if serial != bare:
        note += '@kotlinx.serialization.SerialName("%s") ' % serial
    if alternates:
        note += '@kotlinx.serialization.json.JsonNames(%s) ' % ', '.join('"%s"' % a for a in alternates)
    if note:
        m = re.search(r'(?:(?:override|private|internal|public|protected)\s+)*(?:val|var)\s+`?\w', text)
        if m:
            text = text[:m.start()] + note + text[m.start():]
    if param.type.strip().rstrip('?') == 'Any':
        m = re.search(r'(?:(?:override|private|internal|public|protected)\s+)*(?:val|var)\s+`?\w', text)
        if m:
            text = text[:m.start()] + '@kotlinx.serialization.Serializable(with = com.coinepro.web.wire.AnyValueSerializer::class) ' + text[m.start():]
    if param.default is None:
        zero = zero_for(param.type)
        if zero is not None:
            text = text.rstrip()
            text += ' = ' + zero
            if raw.endswith('\n') or re.search(r'\s$', raw):
                text += raw[len(raw.rstrip()):]
    return text


def transform_enum_body(text, decl):
    s, e = decl.body_span
    body = text[s:e + 1]
    body = re.sub(r'@(?:com\.google\.gson\.annotations\.)?SerializedName\("((?:[^"\\]|\\.)*)"(?:\s*,\s*alternate\s*=\s*\[([^\]]*)\])?\)',
                  lambda m: '@kotlinx.serialization.SerialName("%s")' % m.group(1)
                  + (' @kotlinx.serialization.json.JsonNames(%s)' % m.group(2) if m.group(2) else ''), body)
    return body


BODY_PROP_RE = re.compile(r'(?m)^([ \t]*)((?:(?:private|internal|public|protected|override|open)\s+)*)(val|var)\s+(\w+)\b([^\n]*)$')


def mark_transient(body):
    """Class-body properties with a backing field are not part of the wire, on the phone or here."""
    out = []
    depth = 0
    lines = body.split('\n')
    for index, line in enumerate(lines):
        clean = strip_comments(line)
        m = BODY_PROP_RE.match(line)
        if m and depth == 1 and ' by ' not in clean and '=' in clean.split('//')[0] and 'get()' not in clean \
                and not (index + 1 < len(lines) and re.match(r'\s*(get|set)\s*\(', lines[index + 1])) \
                and 'const' not in m.group(2):
            line = m.group(1) + '@kotlinx.serialization.Transient ' + line[len(m.group(1)):]
        depth += clean.count('{') - clean.count('}')
        out.append(line)
    return '\n'.join(out)


# ── Retrofit services ────────────────────────────────────────────────────────────────────────────

HTTP_RE = re.compile(r'@(GET|POST|PUT|PATCH|DELETE|HEAD|HTTP)\s*(\((?:[^()]|\([^()]*\))*\))?')
FUN_RE = re.compile(r'((?:@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?\s*)+)(suspend\s+)?fun\s+(\w+)\s*\(')


def service_impl(text, decl, qualified):
    s, e = decl.body_span
    body = text[s + 1:e]
    methods = []
    for m in FUN_RE.finditer(body):
        annotations = m.group(1)
        http = HTTP_RE.search(annotations)
        if not http:
            continue
        open_at = m.end() - 1
        close_at = match(body, open_at)
        tail = body[close_at + 1:]
        rt = re.match(r'\s*:\s*([^\n={]+)', tail)
        return_type = rt.group(1).strip() if rt else 'Unit'
        params = [p for _, _, p in params_of(body, (open_at, close_at))]
        verb = http.group(1)
        args = http.group(2) or '()'
        strings = re.findall(r'"((?:[^"\\]|\\.)*)"', args)
        if verb == 'HTTP':
            mm = re.search(r'method\s*=\s*"(\w+)"', args)
            pm = re.search(r'path\s*=\s*"((?:[^"\\]|\\.)*)"', args)
            verb = mm.group(1) if mm else strings[0]
            path = pm.group(1) if pm else (strings[1] if len(strings) > 1 else '')
        else:
            path = strings[0] if strings else ''
        multipart = '@Multipart' in annotations
        methods.append((m.group(3), params, return_type, verb, path, multipart, bool(m.group(2))))
    if not methods:
        return None
    vis = 'private' if decl.visibility == 'private' else 'internal'
    cls = qualified.replace('.', '') + 'Web'
    lines = ['', '', '// Generated for the browser by web/tools/share_sources.py: the Retrofit service, without reflection.',
             '%s class %s(private val retrofit: retrofit2.Retrofit) : %s {' % (vis, cls, qualified)]
    for name, params, return_type, verb, path, multipart, is_suspend in methods:
        sig = ', '.join('%s: %s' % (p.name, p.type) for p in params)
        url = 'null'
        path_params, query, headers, body_expr, parts = [], [], [], 'null', []
        for p in params:
            kinds = {a[0].split('.')[-1]: a[1] for a in p.annotations}
            if 'Url' in kinds:
                url = '%s.toString()' % p.name
            elif 'Path' in kinds:
                path_params.append('%s to %s' % (kinds['Path'][1:-1].split(',')[0].strip(), p.name))
            elif 'Query' in kinds:
                query.append('%s to %s' % (kinds['Query'][1:-1].split(',')[0].strip(), p.name))
            elif 'Header' in kinds:
                headers.append('%s to %s' % (kinds['Header'][1:-1].strip(), p.name))
            elif 'Body' in kinds:
                t = p.type.strip()
                if t.rstrip('?') in ('RequestBody', 'okhttp3.RequestBody'):
                    body_expr = p.name
                else:
                    body_expr = 'retrofit2.jsonBody(com.coinepro.web.wire.encodeWire<%s>(%s))' % (t, p.name)
            elif 'Part' in kinds:
                parts.append(p.name)
        if parts:
            body_expr = 'okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)%s.build()' % ''.join(
                '.addPart(%s)' % x for x in parts)
        call = ('retrofit.execute(%s, %s, %s, listOf(%s), listOf(%s), listOf(%s), %s)'
                % ('"%s"' % verb, '"%s"' % path.replace('$', '\\$'), url, ', '.join(path_params), ', '.join(query),
                   ', '.join(headers), body_expr))
        rt = return_type
        if rt.startswith('Response<') or rt.startswith('retrofit2.Response<'):
            inner = rt[rt.index('<') + 1:rt.rindex('>')]
            result = 'retrofit.wrapped<%s>(response)' % inner
        else:
            result = 'retrofit.body<%s>(response)' % rt
        lines.append('    override %sfun %s(%s): %s {' % ('suspend ' if is_suspend else '', name, sig, rt))
        lines.append('        val response = %s' % call)
        lines.append('        return %s' % result)
        lines.append('    }')
    lines.append('}')
    return '\n'.join(lines) + '\n'


# ── Browser edits to single files ────────────────────────────────────────────────────────────────
#
# Each is a literal (file, before, after), applied only if `before` is found exactly once; the pass
# fails otherwise, so a phone-side edit that moves the text is noticed at the next build rather than
# silently dropping the browser's version of it. Every entry says why the browser needs it.

PATCHES = [
    # An access-ordered LinkedHashMap is a JVM constructor Kotlin's common map does not have; the
    # browser's map is kept in the same order by re-inserting a series each time it is read.
    ('core/marketdata', 'com/coinepro/core/marketdata/CandleArchive.kt',
     'private val series = LinkedHashMap<String, MutableList<OhlcBar>>(16, 0.75f, true)',
     'private val series = com.coinepro.web.jvm.AccessOrderedMap<String, MutableList<OhlcBar>>()'),
    # A help picture is fetched the first time it is shown; the revision re-reads it when it lands.
    ('core/help', 'com/coinepro/core/help/CoineProHelpSheet.kt',
     'return remember(image.file) {',
     'return remember(image.file, com.coinepro.web.assets.Assets.revision) {'),
]


def apply_patches(files):
    for module, rel, before, after in PATCHES:
        key = (module, rel)
        if key not in files:
            continue
        text = files[key]
        before = before.replace('\\n', '\n')
        count = text.count(before)
        if count != 1:
            sys.exit('share_sources: patch for %s/%s expected once, found %d: %r' % (module, rel, count, before[:80]))
        files[key] = text.replace(before, after)


# ── The dependency graph ─────────────────────────────────────────────────────────────────────────
#
# Hilt builds the phone's object graph at compile time with an annotation processor the browser
# build does not have. The graph is small and fully written out in the source — one `@Module`
# object of `@Provides` functions, a handful of `@Inject` constructors, the `@AssistedInject`
# workers — so it is read from there and written as one Kotlin object, `WebGraph`: a lazy value per
# binding, resolved by type and qualifier exactly as Hilt resolves them, and the fields
# `MainActivity` injects under the same names.

PROVIDES_RE = re.compile(r'((?:@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?\s*)+)fun\s+(\w+)\s*\(')
NOT_QUALIFIERS = {'Provides', 'Singleton', 'JvmStatic', 'Suppress', 'IntoMap', 'IntoSet', 'Binds', 'Reusable'}


def normal_type(t):
    t = strip_comments(t)
    t = re.sub(r'@[\w.]+(\([^)]*\))?', '', t)
    t = re.sub(r'\b(?:[a-z_]\w*\.)+(?=[A-Z])', '', t)
    return re.sub(r'\s+', '', t)


def qualifier_of(annotations):
    for name in re.findall(r'@([\w.]+)', annotations):
        short = name.split('.')[-1]
        if short not in NOT_QUALIFIERS and short not in ('ApplicationContext', 'Assisted', 'JvmSuppressWildcards'):
            return short
    return None


def build_graph(files, decls, packages):
    module_key = None
    for key, text in files.items():
        if re.search(r'@Module\b', text) and re.search(r'(?m)^object\s+\w+', text):
            module_key = key
            break
    if module_key is None:
        return None
    text = files[module_key]
    om = re.search(r'(?m)^object\s+(\w+)\s*\{', text)
    module_name = om.group(1)
    body_start = om.end() - 1
    body_end = match(text, body_start)
    body = text[body_start + 1:body_end]

    bindings = {}   # (normal type, qualifier) -> expression
    providers = []
    for m in PROVIDES_RE.finditer(body):
        annotations = m.group(1)
        if '@Provides' not in annotations:
            continue
        open_at = m.end() - 1
        close_at = match(body, open_at)
        rt = re.match(r'\s*:\s*([^=\n{]+)', body[close_at + 1:])
        if not rt:
            sys.exit('share_sources: @Provides %s has no declared type' % m.group(2))
        params = [p for _, _, p in params_of(body, (open_at, close_at))]
        providers.append((m.group(2), params, rt.group(1).strip(), qualifier_of(annotations)))
        bindings[(normal_type(rt.group(1)), qualifier_of(annotations))] = 'p_' + m.group(2)

    injected = []   # (fq name, params, value name)
    workers = []
    for key, ds in decls.items():
        ftext = files[key]
        for d in ds:
            if d.kind != 'class' or not d.params_span:
                continue
            head = ftext[d.name_end:d.params_span[0]]
            fq = (packages[key] + '.' if packages[key] else '') + d.name
            params = [p for _, _, p in params_of(ftext, d.params_span)]
            if re.search(r'@Inject\s+constructor', head):
                injected.append((fq, params, 'i_' + d.name))
                bindings.setdefault((normal_type(d.name), None), 'i_' + d.name)
            elif re.search(r'@AssistedInject\s+constructor', head):
                workers.append((fq, params))

    def resolve(p, owner):
        qualifier = qualifier_of(' '.join('@' + a[0] for a in p.annotations))
        t = normal_type(p.type)
        if any(a[0].split('.')[-1] == 'ApplicationContext' for a in p.annotations) or t == 'Context':
            return 'android.content.Context.Page'
        hit = bindings.get((t, qualifier)) or (bindings.get((t, None)) if qualifier is None else None)
        if hit is None and p.default is None:
            sys.exit('share_sources: no binding for %s %s in %s' % (qualifier or '', p.type, owner))
        return hit

    def call(params, owner):
        args = []
        for p in params:
            value = resolve(p, owner)
            if value is not None:
                args.append('%s = %s' % (p.name, value))
        return ', '.join(args)

    pkg = packages[module_key]
    imports = '\n'.join(re.findall(r'(?m)^import .*$', text))
    out = ['// Generated by web/tools/share_sources.py from %s. Do not edit.' % module_key[1],
           '@file:Suppress("unused", "DEPRECATION")', '', 'package %s' % pkg, '', imports, '',
           '/** The phone\'s Hilt graph, for the browser: every binding once, built on first use. */',
           'object WebGraph {']
    for name, params, rt, _ in providers:
        out.append('    val p_%s: %s by lazy { %s.%s(%s) }' % (name, rt, module_name, name, call(params, name)))
    for fq, params, value in injected:
        out.append('    val %s: %s by lazy { %s(%s) }' % (value, fq, fq, call(params, fq)))
    # The activity's own fields, under their own names.
    # Read from the phone's file itself: the browser compiles its own twin of the activity.
    activity = os.path.join(REPO, 'app/src/main/kotlin/com/coinepro/app/MainActivity.kt')
    sources = [open(activity, encoding='utf-8').read()] if os.path.isfile(activity) else []
    for t in sources:
        if True:
            for fm in re.finditer(r'@Inject\s+lateinit\s+var\s+(\w+)\s*:\s*([^\n]+)', t):
                p = Param('%s: %s' % (fm.group(1), fm.group(2).strip()))
                out.append('    val %s get() = %s' % (fm.group(1), resolve(p, 'MainActivity')))
    out.append('')
    out.append('    /** The phone\'s `HiltWorkerFactory`: how WorkManager builds each worker. */')
    out.append('    fun registerWorkers() {')
    for fq, params in workers:
        args = []
        for i, p in enumerate(params):
            if any(a[0].split('.')[-1] == 'Assisted' for a in p.annotations):
                args.append('%s = %s' % (p.name, 'context' if normal_type(p.type) == 'Context' else 'parameters'))
            else:
                args.append('%s = %s' % (p.name, resolve(p, fq)))
        out.append('        androidx.work.WebWorkers.register(%s::class) { context, parameters -> %s(%s) }' % (fq, fq, ', '.join(args)))
    out.append('    }')
    out.append('}')
    return (module_key[0], module_key[1].rsplit('/', 1)[0] + '/WebGraph.kt'), '\n'.join(out) + '\n'


# ── The pass ─────────────────────────────────────────────────────────────────────────────────────


def main():
    global REPO
    repo, out, modules, excluded = sys.argv[1], sys.argv[2], sys.argv[3].split(','), [x for x in sys.argv[4].split(',') if x]
    REPO = repo
    files = {}
    for module in modules:
        # `module@sourceSet` names a Kotlin Multiplatform source set (`chart/ui@androidMain`); a plain
        # module is an Android one, whose release variant's own sources come too (`ThirdPartyWires`).
        module, _, source_set = module.partition('@')
        sets = ['src/%s/kotlin' % source_set] if source_set else ['src/main/kotlin', 'src/release/kotlin']
        for root in [os.path.join(repo, module, d) for d in sets]:
            for dirpath, _, names in os.walk(root):
                for n in names:
                    if not n.endswith('.kt'):
                        continue
                    full = os.path.join(dirpath, n)
                    rel = os.path.relpath(full, root)
                    if rel in excluded:
                        continue
                    with open(full, encoding='utf-8') as f:
                        files[(module + ('@' + source_set if source_set else ''), rel)] = f.read()

    apply_patches(files)

    decls = {}
    by_name = {}
    packages = {}
    for key, text in files.items():
        pm = re.search(r'(?m)^package\s+([\w.]+)', text)
        packages[key] = pm.group(1) if pm else ''
        ds = declarations(key, text)
        decls[key] = ds
        for d in ds:
            by_name.setdefault(d.name, []).append(d)

    # Roots: the types services return and take, and what `fromJson` is asked for.
    roots = set()
    services = []
    for key, ds in decls.items():
        text = files[key]
        for d in ds:
            if d.kind == 'interface' and d.body_span and HTTP_RE.search(text[d.body_span[0]:d.body_span[1]]):
                services.append((key, d))
                body = text[d.body_span[0]:d.body_span[1]]
                for m in FUN_RE.finditer(body):
                    if not HTTP_RE.search(m.group(1)):
                        continue
                    close_at = match(body, m.end() - 1)
                    rt = re.match(r'\s*:\s*([^\n={]+)', body[close_at + 1:])
                    if rt:
                        roots |= type_names(rt.group(1))
                    for _, _, p in params_of(body, (m.end() - 1, close_at)):
                        if any(a[0].split('.')[-1] == 'Body' for a in p.annotations):
                            roots |= type_names(p.type)
        for m in re.finditer(r'fromJson\([^;\n]*?(\w+)::class\.java', text):
            roots.add(m.group(1))
        # Room's tables: the browser keeps them as JSON in the page's storage.
        for d in ds:
            if d.kind == 'class' and re.search(r'@(?:androidx\.room\.)?Entity\b', d.annotations):
                roots.add(d.name)
        if 'SerializedName' in text:
            for d in ds:
                if d.params_span and 'SerializedName' in text[d.params_span[0]:d.params_span[1]]:
                    roots.add(d.name)

    wire = set()
    queue = [r for r in roots if r not in BUILTINS]
    while queue:
        name = queue.pop()
        if name in wire or name not in by_name:
            continue
        wire.add(name)
        for d in by_name[name]:
            if d.kind != 'class' or 'sealed' in d.modifiers or 'abstract' in d.modifiers:
                continue
            if d.params_span:
                text = files[d.path]
                for _, _, p in params_of(text, d.params_span):
                    queue.extend(t for t in type_names(p.type) if t not in BUILTINS)

    # Read before any edit, while the declaration spans still point at the phone's own text.
    graph = build_graph(files, decls, packages)

    # Edits, applied from the end of each file backwards so the spans stay valid.
    for key, text in files.items():
        edits = []
        for d in decls[key]:
            if d.name in wire and d.kind == 'class' and 'sealed' not in d.modifiers and 'abstract' not in d.modifiers \
                    and 'inner' not in d.modifiers:
                if d.is_enum:
                    if d.body_span:
                        edits.append((d.body_span[0], d.body_span[1] + 1, transform_enum_body(text, d)))
                else:
                    if d.body_span:
                        s, e = d.body_span
                        edits.append((s, e + 1, mark_transient(text[s:e + 1])))
                    if d.params_span:
                        for s, e, p in params_of(text, d.params_span):
                            if p.name:
                                edits.append((s, e, transform_param(text[s:e], p)))
                at = d.start + len(d.indent) + len(d.annotations)
                edits.append((at, at, '@kotlinx.serialization.Serializable '))
        extra = ''
        for skey, d in services:
            if skey != key:
                continue
            outer = [o for o in decls[key] if o.body_span and o.body_span[0] < d.start < o.body_span[1] and o is not d]
            qualified = '.'.join([o.name for o in sorted(outer, key=lambda o: o.start)] + [d.name])
            impl = service_impl(text, d, qualified)
            if impl:
                extra += impl
        edits.sort(key=lambda x: (x[0], x[1]), reverse=True)
        for s, e, replacement in edits:
            text = text[:s] + replacement + text[e:]
        text = re.sub(r'\.create\(\s*((?:\w+\.)*\w+)::class\.java\s*\)',
                      lambda m: '.let { %sWeb(it) }' % m.group(1).replace('.', ''), text)
        text = re.sub(r'override\s+fun\s+intercept\s*\(', 'override suspend fun intercept(', text)
        # Characters the one typeface has no glyph for, written as ones it has (web/tools/glyphs.json).
        # Not in the community's reactions, which are data the server keeps, not text on a line.
        if not rel_is_data(key):
            text = in_literals(text, GLYPHS)
        # Material's `Text`, through the browser's version that draws an emoji with the browser's
        # own emoji font (web/src/shims/kotlin/com/coinepro/web/text/Text.kt).
        text = text.replace('import androidx.compose.material3.Text\n', 'import com.coinepro.web.text.Text\n')
        # `runBlocking` has no browser form; this one runs the block and returns when it did not wait.
        text = text.replace('import kotlinx.coroutines.runBlocking', 'import com.coinepro.web.jvm.runBlocking')
        # A class's name, which `Class.getSimpleName` never answers null for.
        text = re.sub(r'(\w+(?:\(\))?)::class\.java\.simpleName', r'(\1::class.simpleName ?: "")', text)
        # `java.lang.String.format` spelled out: the same call through the compat `String.format`.
        text = text.replace('java.lang.String.format(', 'String.format(')
        # Compose Multiplatform's `DialogProperties` has no window-insets flag; a page has no system
        # bars for a dialog to fit inside, so the argument is dropped rather than approximated.
        text = re.sub(r'decorFitsSystemWindows\s*=\s*[^,\n)]+,?', '', text)
        # Coil's request builder takes the platform context, which in a browser is a singleton.
        text = re.sub(r'ImageRequest\.Builder\(\s*\w+\s*\)', 'ImageRequest.Builder(coil3.PlatformContext.INSTANCE)', text)
        text = re.sub(r'(newCall\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\))\.execute\(\)', r'\1.await()', text)
        files[key] = text + extra

    if graph:
        files[graph[0]] = graph[1]

    if os.path.isdir(out):
        shutil.rmtree(out)
    for (module, rel), text in files.items():
        dest = os.path.join(out, module.replace('/', '_').replace('@', '_'), rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, 'w', encoding='utf-8') as f:
            f.write(text)
    print('shared %d files, %d wire classes, %d services' % (len(files), len(wire), len(services)))


if __name__ == '__main__':
    main()
