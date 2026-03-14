package com.picflick.app.data

/**
 * Cloudinary Professional Filters - URL-based approach
 * Uses Cloudinary's transformation URLs (no heavy Android SDK needed)
 * 
 * How it works:
 * 1. Upload photo to Cloudinary → get public_id
 * 2. Apply filter by adding transformation to URL:
 *    https://res.cloudinary.com/{cloud}/image/upload/{filter}/{public_id}
 */
object CloudinaryFilters {
    
    // Your Cloudinary cloud name - set this in your local.properties or BuildConfig
    const val CLOUD_NAME = "your-cloud-name"
    
    /**
     * Build filtered image URL
     */
    fun buildFilteredUrl(
        cloudName: String,
        publicId: String,
        transformations: String
    ): String {
        return if (transformations.isEmpty()) {
            "https://res.cloudinary.com/$cloudName/image/upload/$publicId"
        } else {
            "https://res.cloudinary.com/$cloudName/image/upload/$transformations/$publicId"
        }
    }
    
    /**
     * Available filters with their Cloudinary transformation strings
     */
    enum class Filter(
        val displayName: String,
        val icon: String,
        val transformation: String
    ) {
        // BASIC
        ORIGINAL("Original", "📷", ""),
        
        // AUTO ENHANCE
        AUTO("Auto Enhance", "✨", "e_improve"),
        
        // COLOR
        SEPIA("Sepia", "📜", "e_sepia:80"),
        SEPIA_LIGHT("Sepia Light", "📃", "e_sepia:40"),
        BLACK_WHITE("B&W", "⚫", "e_blackwhite:100"),
        GRAYSCALE("Grayscale", "⬜", "e_grayscale"),
        NEGATIVE("Negative", "🔄", "e_negate"),
        
        // ARTISTIC
        VINTAGE("Vintage", "🎞️", "e_sepia:30/e_contrast:-20/e_brightness:5"),
        RETRO("Retro", "📺", "e_contrast:20/e_saturation:30"),
        LOMO("Lomo", "📸", "e_vignette:50/e_contrast:15"),
        POLAROID("Polaroid", "🖼️", "e_brightness:10/e_contrast:-10/b_rgb:F0F0F0"),
        
        // EFFECTS
        VIGNETTE("Vignette", "🔘", "e_vignette:40"),
        VIGNETTE_LIGHT("Vignette Light", "⚪", "e_vignette:20"),
        BLUR("Blur", "💨", "e_blur:600"),
        BLUR_LIGHT("Blur Light", "🌫️", "e_blur:300"),
        SHARPEN("Sharpen", "🔺", "e_sharpen:300"),
        
        // FUN
        PIXELATE("Pixelate", "👾", "e_pixelate:10"),
        OIL_PAINT("Oil Paint", "🎨", "e_oil_paint:40"),
        CARTOON("Cartoon", "🎭", "e_cartoonify:30"),
        SKETCH("Sketch", "✏️", "e_sketch:40");
        
        companion object {
            fun byCategory(category: Category): List<Filter> {
                return when (category) {
                    Category.BASIC -> listOf(ORIGINAL, AUTO)
                    Category.COLOR -> listOf(SEPIA, SEPIA_LIGHT, BLACK_WHITE, GRAYSCALE, NEGATIVE)
                    Category.ARTISTIC -> listOf(VINTAGE, RETRO, LOMO, POLAROID)
                    Category.EFFECTS -> listOf(VIGNETTE, VIGNETTE_LIGHT, BLUR, BLUR_LIGHT, SHARPEN)
                    Category.FUN -> listOf(PIXELATE, OIL_PAINT, CARTOON, SKETCH)
                }
            }
        }
    }
    
    enum class Category {
        BASIC, COLOR, ARTISTIC, EFFECTS, FUN
    }
    
    /**
     * Build custom transformation string
     */
    fun buildTransformation(
        brightness: Int = 0,      // -100 to 100
        contrast: Int = 0,       // -100 to 100
        saturation: Int = 0,     // -100 to 100
        blur: Int = 0,           // 0 to 2000
        sepia: Int = 0,         // 0 to 100
        vignette: Int = 0,       // 0 to 100
        sharpen: Int = 0         // 0 to 2000
    ): String {
        val parts = mutableListOf<String>()
        
        if (brightness != 0) parts.add("e_brightness:$brightness")
        if (contrast != 0) parts.add("e_contrast:$contrast")
        if (saturation != 0) parts.add("e_saturation:$saturation")
        if (blur != 0) parts.add("e_blur:$blur")
        if (sepia != 0) parts.add("e_sepia:$sepia")
        if (vignette != 0) parts.add("e_vignette:$vignette")
        if (sharpen != 0) parts.add("e_sharpen:$sharpen")
        
        return parts.joinToString("/")
    }
    
    /**
     * Instagram-style preset filters
     */
    object Presets {
        val CLARENDON = "e_contrast:15/e_saturation:20/e_brightness:10"
        val GINGHAM = "e_contrast:-15/e_brightness:15/e_saturation:-20"
        val MOON = "e_grayscale/e_contrast:20/e_brightness:10"
        val LARK = "e_saturation:25/e_contrast:-10/e_brightness:15"
        val REYES = "e_sepia:30/e_contrast:-20/e_brightness:15/e_saturation:-20"
        val JUNO = "e_contrast:15/e_saturation:30/e_brightness:10"
        val SLUMBER = "e_saturation:-30/e_brightness:15/e_sepia:20"
        val CREMA = "e_contrast:-15/e_sepia:30/e_saturation:-15"
    }
}
