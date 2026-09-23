package androidx.compose.ui.graphics

import android.graphics.Bitmap

fun Bitmap.asImageBitmap(): ImageBitmap = image

fun ImageBitmap.asAndroidBitmap(): Bitmap = Bitmap(this)

fun toComposeImageBitmapCompat(image: org.jetbrains.skia.Image): ImageBitmap = image.toComposeImageBitmap()

fun ImageBitmap.asSkiaBitmapCompat(): org.jetbrains.skia.Bitmap = this.asSkiaBitmap()
