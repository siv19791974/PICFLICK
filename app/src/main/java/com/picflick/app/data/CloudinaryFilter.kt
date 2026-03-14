package com.picflick.app.data

import android.net.Uri

/**
 * Cloudinary Professional Filters
 * 40+ professional photo filters via Cloudinary's cloud-based image processing
 * 
 * Filters are applied by adding transformation parameters to the image URL
 * Example: https://res.cloudinary.com/demo/image/upload/e_sepia/sample.jpg
 */
enum class CloudinaryFilter(
    val displayName: String,
    val icon: String,
    val category: FilterCategory,
    val transformation: String // Cloudinary transformation string
) {
    // ORIGINAL
    ORIGINAL("Original", "📷", FilterCategory.BASIC, ""),
    
    // BASIC ENHANCEMENTS
    AUTO_ENHANCE("Auto Enhance", "✨", FilterCategory.BASIC, "e_improve"),
    AUTO_COLOR("Auto Color", "🎨", FilterCategory.BASIC, "e_auto_color"),
    AUTO_CONTRAST("Auto Contrast", "◐", FilterCategory.BASIC, "e_auto_contrast"),
    AUTO_BRIGHTNESS("Auto Bright", "☀️", FilterCategory.BASIC, "e_auto_brightness"),
    
    // COLOR FILTERS
    SEPIA("Sepia", "📜", FilterCategory.COLOR, "e_sepia:80"),
    SEPIA_LIGHT("Sepia Light", "📃", FilterCategory.COLOR, "e_sepia:40"),
    SEPIA_DARK("Sepia Dark", "📄", FilterCategory.COLOR, "e_sepia:100"),
    BLACK_WHITE("B&W", "⚫", FilterCategory.COLOR, "e_blackwhite:100"),
    BLACK_WHITE_SOFT("B&W Soft", "⚪", FilterCategory.COLOR, "e_blackwhite:60"),
    GRAYSCALE("Grayscale", "⬜", FilterCategory.COLOR, "e_grayscale"),
    NEGATIVE("Negative", "🔄", FilterCategory.COLOR, "e_negate"),
    
    // ARTISTIC FILTERS
    VINTAGE("Vintage", "🎞️", FilterCategory.ARTISTIC, "e_vignette:30/e_sepia:30/e_contrast:-20"),
    VINTAGE_WARM("Vintage Warm", "🔥", FilterCategory.ARTISTIC, "e_vignette:40/e_sepia:50/e_brightness:10"),
    VINTAGE_COOL("Vintage Cool", "❄️", FilterCategory.ARTISTIC, "e_vignette:30/e_saturation:-20/e_temperature:-20"),
    RETRO("Retro", "📺", FilterCategory.ARTISTIC, "e_contrast:20/e_saturation:30/e_vignette:20"),
    RETRO_POP("Retro Pop", "🌈", FilterCategory.ARTISTIC, "e_contrast:30/e_saturation:50/e_vibrance:30"),
    LOMO("Lomo", "📷", FilterCategory.ARTISTIC, "e_vignette:50/e_contrast:15/e_saturation:20"),
    POLAROID("Polaroid", "🖼️", FilterCategory.ARTISTIC, "e_brightness:10/e_contrast:-10/e_saturation:-10/b_rgb:F0F0F0"),
    INSTAGRAM_1977("1977", "📸", FilterCategory.ARTISTIC, "e_contrast:-15/e_saturation:20/e_brightness:10/e_sepia:20"),
    INSTAGRAM_NASHVILLE("Nashville", "🎸", FilterCategory.ARTISTIC, "e_brightness:15/e_contrast:-10/e_saturation:25/e_sepia:15"),
    INSTAGRAM_WALDEN("Walden", "🌲", FilterCategory.ARTISTIC, "e_brightness:25/e_saturation:-15/e_temperature:-15"),
    
    // EFFECTS
    VIGNETTE_LIGHT("Vignette Light", "🔘", FilterCategory.EFFECTS, "e_vignette:20"),
    VIGNETTE_MEDIUM("Vignette", "🔲", FilterCategory.EFFECTS, "e_vignette:40"),
    VIGNETTE_HEAVY("Vignette Heavy", "⬛", FilterCategory.EFFECTS, "e_vignette:70"),
    BLUR_LIGHT("Blur Light", "💨", FilterCategory.EFFECTS, "e_blur:300"),
    BLUR_MEDIUM("Blur", "💭", FilterCategory.EFFECTS, "e_blur:600"),
    BLUR_HEAVY("Blur Heavy", "🌫️", FilterCategory.EFFECTS, "e_blur:1500"),
    SHARPEN_LIGHT("Sharpen Light", "🔹", FilterCategory.EFFECTS, "e_sharpen:100"),
    SHARPEN_MEDIUM("Sharpen", "🔺", FilterCategory.EFFECTS, "e_sharpen:300"),
    SHARPEN_HEAVY("Sharpen Heavy", "💎", FilterCategory.EFFECTS, "e_sharpen:600"),
    PIXELATE("Pixelate", "👾", FilterCategory.EFFECTS, "e_pixelate:10"),
    PIXELATE_HEAVY("Pixel Art", "🎮", FilterCategory.EFFECTS, "e_pixelate:20"),
    MOSAIC("Mosaic", "🔲", FilterCategory.EFFECTS, "e_mosaic:10"),
    
    // ADVANCED ARTISTIC
    OIL_PAINT_LIGHT("Oil Paint Light", "🖌️", FilterCategory.ADVANCED, "e_oil_paint:20"),
    OIL_PAINT("Oil Paint", "🎨", FilterCategory.ADVANCED, "e_oil_paint:40"),
    OIL_PAINT_HEAVY("Oil Paint Heavy", "🖼️", FilterCategory.ADVANCED, "e_oil_paint:70"),
    CARTOONIFY("Cartoon", "🎭", FilterCategory.ADVANCED, "e_cartoonify:30"),
    CARTOONIFY_HEAVY("Cartoon Bold", "🤡", FilterCategory.ADVANCED, "e_cartoonify:60"),
    SKETCH_LIGHT("Sketch Light", "✏️", FilterCategory.ADVANCED, "e_sketch:20"),
    SKETCH("Sketch", "✍️", FilterCategory.ADVANCED, "e_sketch:40"),
    SKETCH_HEAVY("Sketch Bold", "🖊️", FilterCategory.ADVANCED, "e_sketch:70"),
    HALFTONE("Halftone", "⚫", FilterCategory.ADVANCED, "e_halftone:20"),
    HALFTONE_HEAVY("Halftone Bold", "⬛", FilterCategory.ADVANCED, "e_halftone:40"),
    
    // ADJUSTMENTS
    BRIGHTNESS_UP("Bright +", "☀️", FilterCategory.ADJUSTMENT, "e_brightness:20"),
    BRIGHTNESS_DOWN("Bright -", "🌑", FilterCategory.ADJUSTMENT, "e_brightness:-20"),
    CONTRAST_UP("Contrast +", "◐", FilterCategory.ADJUSTMENT, "e_contrast:30"),
    CONTRAST_DOWN("Contrast -", "◑", FilterCategory.ADJUSTMENT, "e_contrast:-20"),
    SATURATION_UP("Saturation +", "🌈", FilterCategory.ADJUSTMENT, "e_saturation:40"),
    SATURATION_DOWN("Saturation -", "🌫️", FilterCategory.ADJUSTMENT, "e_saturation:-40"),
    WARMTH_UP("Warmth +", "🔥", FilterCategory.ADJUSTMENT, "e_temperature:20"),
    WARMTH_DOWN("Warmth -", "❄️", FilterCategory.ADJUSTMENT, "e_temperature:-20"),
    VIBRANCE_UP("Vibrance +", "💎", FilterCategory.ADJUSTMENT, "e_vibrance:30"),
    HIGHLIGHT_UP("Highlights +", "✨", FilterCategory.ADJUSTMENT, "e_shadow:-20"),
    SHADOW_UP("Shadows +", "🌙", FilterCategory.ADJUSTMENT, "e_highlight:-20");
    
    companion object {
        fun getFiltersByCategory(category: FilterCategory): List<CloudinaryFilter> {
            return entries.filter { it.category == category }
        }
    }
}

enum class FilterCategory {
    BASIC, COLOR, ARTISTIC, EFFECTS, ADJUSTMENT, ADVANCED
}

/**
 * Service for applying Cloudinary filters
 */
class CloudinaryFilterService {
    
    companion object {
        // Cloudinary cloud name - replace with your actual cloud name
        const val CLOUD_NAME = "your-cloud-name"
        
        // Base URL for Cloudinary transformations
        fun getFilteredImageUrl(publicId: String, filter: CloudinaryFilter): String {
            return if (filter.transformation.isEmpty()) {
                "https://res.cloudinary.com/$CLOUD_NAME/image/upload/$publicId"
            } else {
                "https://res.cloudinary.com/$CLOUD_NAME/image/upload/${filter.transformation}/$publicId"
            }
        }
        
        // Get URL with custom transformation
        fun getCustomFilteredUrl(publicId: String, transformation: String): String {
            return "https://res.cloudinary.com/$CLOUD_NAME/image/upload/$transformation/$publicId"
        }
        
        // Build custom transformation from multiple effects
        fun buildTransformation(
            brightness: Int = 0,
            contrast: Int = 0,
            saturation: Int = 0,
            blur: Int = 0,
            sepia: Int = 0,
            vignette: Int = 0,
            sharpen: Int = 0
        ): String {
            val effects = mutableListOf<String>()
            
            if (brightness != 0) effects.add("e_brightness:$brightness")
            if (contrast != 0) effects.add("e_contrast:$contrast")
            if (saturation != 0) effects.add("e_saturation:$saturation")
            if (blur != 0) effects.add("e_blur:$blur")
            if (sepia != 0) effects.add("e_sepia:$sepia")
            if (vignette != 0) effects.add("e_vignette:$vignette")
            if (sharpen != 0) effects.add("e_sharpen:$sharpen")
            
            return effects.joinToString("/")
        }
    }
}

/**
 * Data class for filter preset
 */
data class FilterPreset(
    val name: String,
    val transformations: List<String>,
    val intensity: Float = 1.0f
)

/**
 * Popular filter presets
 */
object FilterPresets {
    val INSTAGRAM_PRESETS = listOf(
        FilterPreset("Clarendon", listOf("e_contrast:15", "e_saturation:20", "e_brightness:10")),
        FilterPreset("Gingham", listOf("e_contrast:-15", "e_brightness:15", "e_saturation:-20")),
        FilterPreset("Moon", listOf("e_grayscale", "e_contrast:20", "e_brightness:10")),
        FilterPreset("Lark", listOf("e_saturation:25", "e_contrast:-10", "e_brightness:15")),
        FilterPreset("Reyes", listOf("e_sepia:30", "e_contrast:-20", "e_brightness:15", "e_saturation:-20")),
        FilterPreset("Juno", listOf("e_contrast:15", "e_saturation:30", "e_brightness:10")),
        FilterPreset("Slumber", listOf("e_saturation:-30", "e_brightness:15", "e_sepia:20")),
        FilterPreset("Crema", listOf("e_contrast:-15", "e_sepia:30", "e_saturation:-15")),
        FilterPreset("Ludwig", listOf("e_contrast:-15", "e_saturation:-15", "e_brightness:10")),
        FilterPreset("Aden", listOf("e_contrast:-20", "e_saturation:-20", "e_brightness:15", "e_sepia:15"))
    )
    
    val PROFESSIONAL_PRESETS = listOf(
        FilterPreset("Cinematic", listOf("e_contrast:25", "e_saturation:-10", "e_vignette:30", "e_brightness:-5")),
        FilterPreset("Portrait", listOf("e_contrast:-10", "e_saturation:-15", "e_brightness:10", "e_blur:100")),
        FilterPreset("Food", listOf("e_saturation:30", "e_contrast:15", "e_brightness:10")),
        FilterPreset("Landscape", listOf("e_saturation:25", "e_contrast:20", "e_vibrance:20")),
        FilterPreset("Night", listOf("e_contrast:30", "e_saturation:-20", "e_brightness:-10")),
        FilterPreset("Vintage Film", listOf("e_sepia:40", "e_contrast:-15", "e_vignette:40", "e_saturation:-20"))
    )
}
