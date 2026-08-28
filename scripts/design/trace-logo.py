"""Trace a flat two-colour logo into Android vector paths.

    python3 scripts/design/trace-logo.py <logo.png>

Written for LBank's mark, and worth keeping for the next one. The problem it solves is a specific
one: a brand mark that a company publishes only as a raster, which somebody then reproduces by
fitting circles and cubics to it by eye. That is what the first two attempts at this logo were, and
both were close and wrong — the neck of the bone too thin, the notch on its right too shallow —
because a shape like this is not made of primitives anybody can name.

The method, in order:

  * classify every pixel as ink, field or transparent, sampling the two colours from the file rather
    than guessing them, and assigning anti-aliased edge pixels to whichever pure colour they are
    nearer;
  * split the ink into connected components — the largest is the frame, anything else is the mark
    inside it;
  * walk each boundary on the pixel grid;
  * round the resulting staircase with three rounds of Chaikin corner-cutting, which turns a
    sampling of a curve back into a curve;
  * simplify with Ramer-Douglas-Peucker.

The tolerance is the only judgement call. At 0.9px on a 294px source, against a mark that ships at
22dp — 66 pixels at xxhdpi — it is more than four times finer than anything that can be seen.

It prints three paths: the silhouette, the field, and the inner mark. The field is emitted as
*field plus inner mark* rather than the field's own pixels, because those form a shape with a hole
in it, and a hole means a second contour and a fill rule to get right for no benefit. Paint the
field whole and put the mark back on top.
"""
import zlib, struct, sys, math

def load(path):
    d = open(path, 'rb').read()
    pos = 8; idat = b''
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]; typ = d[pos+4:pos+8]; data = d[pos+8:pos+8+ln]
        if typ == b'IHDR': w, h, bd, ct = struct.unpack('>IIBB', data[:10])
        elif typ == b'IDAT': idat += data
        pos += 12 + ln
    raw = zlib.decompress(idat); bpp = {0:1,2:3,4:2,6:4}[ct]; stride = w*bpp
    out = bytearray(); prev = bytearray(stride); i = 0
    for y in range(h):
        f = raw[i]; i += 1
        line = bytearray(raw[i:i+stride]); i += stride
        if f == 1:
            for x in range(bpp, stride): line[x] = (line[x]+line[x-bpp]) & 255
        elif f == 2:
            for x in range(stride): line[x] = (line[x]+prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = line[x-bpp] if x >= bpp else 0
                line[x] = (line[x] + ((a+prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x-bpp] if x >= bpp else 0
                b = prev[x]; c = prev[x-bpp] if x >= bpp else 0
                p = a+b-c; pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x]+pr) & 255
        out += line; prev = line
    return w, h, bpp, stride, out

def masks(w, h, bpp, stride, out):
    ink = [[False]*w for _ in range(h)]
    yel = [[False]*w for _ in range(h)]
    any_ = [[False]*w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            o = y*stride + x*bpp
            r, g, b = out[o], out[o+1], out[o+2]
            a = out[o+3] if bpp == 4 else 255
            if a < 128: continue
            any_[y][x] = True
            if r > 190 and g > 150 and b < 90: yel[y][x] = True
            elif r < 90 and g < 90 and b < 90: ink[y][x] = True
            else:
                # Anti-aliased edge: assign by which pure colour it is nearer.
                if (r-255)**2+(g-217)**2+b**2 < (r-22)**2+(g-22)**2+(b-22)**2: yel[y][x] = True
                else: ink[y][x] = True
    return ink, yel, any_

def components(mask, w, h):
    """Connected components of `mask`, largest first."""
    seen = [[False]*w for _ in range(h)]
    found = []
    for sy in range(h):
        for sx in range(w):
            if not mask[sy][sx] or seen[sy][sx]: continue
            cells = []; stack = [(sx, sy)]
            while stack:
                x, y = stack.pop()
                if seen[y][x] or not mask[y][x]: continue
                seen[y][x] = True; cells.append((x, y))
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    nx, ny = x+dx, y+dy
                    if 0 <= nx < w and 0 <= ny < h and mask[ny][nx] and not seen[ny][nx]:
                        stack.append((nx, ny))
            found.append(cells)
    found.sort(key=len, reverse=True)
    return found

def as_mask(cells, w, h):
    m = [[False]*w for _ in range(h)]
    for x, y in cells: m[y][x] = True
    return m

def chaikin(points, rounds):
    """Corner cutting. Turns a pixel staircase into the curve it is a sampling of."""
    pts = points
    for _ in range(rounds):
        out = []
        n = len(pts)
        for i in range(n):
            (x1, y1) = pts[i]
            (x2, y2) = pts[(i+1) % n]
            out.append((0.75*x1 + 0.25*x2, 0.75*y1 + 0.25*y2))
            out.append((0.25*x1 + 0.75*x2, 0.25*y1 + 0.75*y2))
        pts = out
    return pts

def contours(mask, w, h):
    """Every closed boundary of `mask`, walked on the pixel grid (Moore neighbourhood)."""
    seen = set(); paths = []
    def on(x, y): return 0 <= x < w and 0 <= y < h and mask[y][x]
    for y in range(h):
        for x in range(w):
            if not on(x, y) or on(x, y-1) or (x, y) in seen: continue
            # Square tracing from the top edge of this pixel.
            path = []; cx, cy = x, y; dx, dy = 0, -1
            start = (cx, cy, dx, dy); guard = 0
            while True:
                guard += 1
                if guard > 8*w*h: break
                path.append((cx, cy))
                seen.add((cx, cy))
                # Turn left, then probe right until a filled cell is found.
                turned = False
                for _ in range(4):
                    ldx, ldy = dy, -dx            # left turn
                    if on(cx+ldx, cy+ldy):
                        dx, dy = ldx, ldy; turned = True; break
                    dx, dy = -dy, dx              # right turn
                if not turned: break
                cx, cy = cx+dx, cy+dy
                if (cx, cy, dx, dy) == start: break
            if len(path) > 8: paths.append(path)
    return paths

def rdp(points, eps):
    if len(points) < 3: return points
    def d(p, a, b):
        (x, y), (x1, y1), (x2, y2) = p, a, b
        num = abs((y2-y1)*x - (x2-x1)*y + x2*y1 - y2*x1)
        den = math.hypot(y2-y1, x2-x1)
        return num/den if den else math.hypot(x-x1, y-y1)
    keep = [False]*len(points); keep[0] = keep[-1] = True
    stack = [(0, len(points)-1)]
    while stack:
        i, j = stack.pop()
        if j <= i+1: continue
        worst, wi = 0.0, i
        for k in range(i+1, j):
            dd = d(points[k], points[i], points[j])
            if dd > worst: worst, wi = dd, k
        if worst > eps:
            keep[wi] = True; stack.append((i, wi)); stack.append((wi, j))
    return [p for p, k in zip(points, keep) if k]

def to_path(paths, ox, oy, eps, smooth=3):
    out = []
    for p in paths:
        curved = chaikin(p, smooth)
        simple = rdp(curved + [curved[0]], eps)
        if len(simple) < 3: continue
        pts = ["%.1f,%.1f" % (x-ox, y-oy) for x, y in simple]
        out.append("M" + pts[0] + "L" + "L".join(pts[1:]) + "Z")
    return "".join(out)

src = sys.argv[1]
w, h, bpp, stride, out = load(src)
ink, yel, any_ = masks(w, h, bpp, stride, out)
# The ring is the biggest black component; anything else black is the bone.
ink_parts = components(ink, w, h)
ring = as_mask(ink_parts[0], w, h)
bone = as_mask([c for part in ink_parts[1:] for c in part], w, h)
field = [[yel[y][x] or bone[y][x] for x in range(w)] for y in range(h)]

xs = [x for y in range(h) for x in range(w) if any_[y][x]]
ys = [y for y in range(h) for x in range(w) if any_[y][x]]
ox, oy = min(xs), min(ys)
print("bounds", min(xs), min(ys), max(xs), max(ys), "->", max(xs)-min(xs)+1, "x", max(ys)-min(ys)+1)
eps = 0.9
for name, mask in (("SILHOUETTE", any_), ("FIELD", field), ("BONE", bone)):
    d = to_path(contours(mask, w, h), ox, oy, eps)
    print("###", name, "len", len(d))
    print(d)
