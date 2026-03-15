package com.picflick.app.data

/**
 * Cloudinary URL-based professional photo filters
 * These use Cloudinary's transformation API via URL parameters
 * No SDK required - just URL construction!
 */
enum class CloudinaryFilter(
    val displayName: String,
    val icon: String,
    val transformation: String,
    val category: Category
) {
    // Basic
    ORIGINAL("Original", "📷", "", Category.BASIC),
    AUTO_ENHANCE("Auto Enhance", "✨", "e_improve", Category.BASIC),

    // Color Adjustments
    SEPIA("Sepia", "📜", "e_sepia:30", Category.COLOR),
    SEPIA_DEEP("Sepia Deep", "📜", "e_sepia:60", Category.COLOR),
    BLACK_WHITE("B&W", "⚫", "e_blackwhite", Category.COLOR),
    GRAYSCALE("Grayscale", "⚪", "e_grayscale", Category.COLOR),
    NEGATIVE("Negative", "🔄", "e_negate", Category.COLOR),

    // Artistic
    VINTAGE("Vintage", "📻", "e_vignette:30/e_sepia:20/e_contrast:-10", Category.ARTISTIC),
    LOMO("Lomo", "📷", "e_vignette:50/e_contrast:20/e_saturation:20", Category.ARTISTIC),
    RETRO("Retro", "🎞️", "e_sepia:40/e_contrast:-15/e_brightness:5", Category.ARTISTIC),
    POLAROID("Polaroid", "📸", "e_sepia:20/e_contrast:-10/e_brightness:10", Category.ARTISTIC),

    // Effects
    VIGNETTE_LIGHT("Vignette Light", "🌑", "e_vignette:20", Category.EFFECTS),
    VIGNETTE_HEAVY("Vignette Heavy", "🌑", "e_vignette:60", Category.EFFECTS),
    BLUR_LIGHT("Blur Light", "💫", "e_blur:500", Category.EFFECTS),
    BLUR_HEAVY("Blur Heavy", "💫", "e_blur:2000", Category.EFFECTS),
    SHARPEN("Sharpen", "⚡", "e_sharpen:100", Category.EFFECTS),
    PIXELATE("Pixelate", "👾", "e_pixelate:10", Category.EFFECTS),

    // Fun/Creative
    OIL_PAINT("Oil Paint", "🎨", "e_oil_paint:30", Category.FUN),
    CARTOONIFY("Cartoon", "🖍️", "e_cartoonify", Category.FUN),
    SKETCH("Sketch", "✏️", "e_sketch:30", Category.FUN),
    HALFTONE("Halftone", "⚫", "e_halftone:20", Category.FUN),

    // Instagram-style
    _1977("1977", "📅", "e_sepia:30/e_contrast:-15/e_brightness:10/e_vignette:20", Category.STYLE),
    NASHVILLE("Nashville", "🎸", "e_sepia:20/e_contrast:-10/e_brightness:15/e_saturation:-20", Category.STYLE),
    WARM("Warm", "☀️", "e_sepia:15/e_contrast:-5/e_brightness:10", Category.STYLE),
    COOL("Cool", "❄️", "e_saturation:-10/e_brightness:5", Category.STYLE),
    FADE("Fade", "🌫️", "e_contrast:-20/e_brightness:10", Category.STYLE),
    CONTRAST("High Contrast", "⚡", "e_contrast:30", Category.STYLE);

    enum class Category {
        BASIC, COLOR, ARTISTIC, EFFECTS, FUN, STYLE
    }

    companion object {
        fun getByCategory(category: Category): List<CloudinaryFilter> {
            return entries.filter { it.category == category }
        }

        fun getCategories(): List<Category> = Category.values().toList()
    }
}