package android.text.style

open class CharacterStyle
class StyleSpan(val style: Int) : CharacterStyle()
class UnderlineSpan : CharacterStyle()
class StrikethroughSpan : CharacterStyle()
class URLSpan(val url: String) : CharacterStyle()
class BulletSpan(val gapWidth: Int = 2) : CharacterStyle()
class RelativeSizeSpan(val sizeChange: Float) : CharacterStyle()
class ForegroundColorSpan(val foregroundColor: Int) : CharacterStyle()
class SubscriptSpan : CharacterStyle()
class SuperscriptSpan : CharacterStyle()
class QuoteSpan : CharacterStyle()
class TypefaceSpan(val family: String?) : CharacterStyle()
