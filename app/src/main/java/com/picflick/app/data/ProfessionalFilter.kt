package com.picflick.app.data

import android.graphics.Bitmap
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.*

/**
 * Professional photo filters using GPUImage library
 * GPU-accelerated filters for high performance and quality
 */
enum class ProfessionalFilter(
    val displayName: String,
    val icon: String,
    val category: FilterCategory
) {
    // ORIGINAL
    ORIGINAL("Original", "📷", FilterCategory.BASIC),
    
    // BASIC ADJUSTMENTS
    AUTO_ENHANCE("Auto", "✨", FilterCategory.BASIC),
    BRIGHTNESS("Bright", "☀️", FilterCategory.ADJUSTMENT),
    CONTRAST("Contrast", "◐", FilterCategory.ADJUSTMENT),
    SATURATION("Vibrant", "🎨", FilterCategory.ADJUSTMENT),
    WARMTH("Warmth", "🔥", FilterCategory.ADJUSTMENT),
    COOL("Cool", "❄️", FilterCategory.ADJUSTMENT),
    
    // COLOR FILTERS
    SEPIA("Sepia", "📜", FilterCategory.COLOR),
    GRAYSCALE("B&W", "⚫", FilterCategory.COLOR),
    INVERT("Invert", "🔄", FilterCategory.COLOR),
    MONOCHROME("Mono", "⬛", FilterCategory.COLOR),
    
    // ARTISTIC FILTERS
    VINTAGE("Vintage", "🎞️", FilterCategory.ARTISTIC),
    RETRO("Retro", "📺", FilterCategory.ARTISTIC),
    SKETCH("Sketch", "✏️", FilterCategory.ARTISTIC),
    TOON("Toon", "🎭", FilterCategory.ARTISTIC),
    POSTERIZE("Poster", "🖼️", FilterCategory.ARTISTIC),
    HALFTONE("Halftone", "⚫", FilterCategory.ARTISTIC),
    
    // EFFECTS
    VIGNETTE("Vignette", "🔘", FilterCategory.EFFECTS),
    GAUSSIAN_BLUR("Blur", "💨", FilterCategory.EFFECTS),
    SHARPEN("Sharp", "🔺", FilterCategory.EFFECTS),
    EDGE_DETECT("Edges", "📐", FilterCategory.EFFECTS),
    EMBOSS("Emboss", "🔲", FilterCategory.EFFECTS),
    CROSSHATCH("Crosshatch", "➕", FilterCategory.EFFECTS),
    
    // BLEND MODES
    OVERLAY("Overlay", "🔝", FilterCategory.BLEND),
    HARD_LIGHT("Hard Light", "💡", FilterCategory.BLEND),
    SOFT_LIGHT("Soft Light", "🕯️", FilterCategory.BLEND),
    DARKEN("Darken", "🌑", FilterCategory.BLEND),
    LIGHTEN("Lighten", "🌕", FilterCategory.BLEND);
    
    companion object {
        fun getFiltersByCategory(category: FilterCategory): List<ProfessionalFilter> {
            return values().filter { it.category == category }
        }
    }
}

enum class FilterCategory {
    BASIC, ADJUSTMENT, COLOR, ARTISTIC, EFFECTS, BLEND
}

/**
 * Apply professional GPUImage filter to bitmap
 */
fun applyProfessionalFilter(bitmap: Bitmap, filter: ProfessionalFilter): Bitmap {
    if (filter == ProfessionalFilter.ORIGINAL) return bitmap
    
    val gpuImage = GPUImage(null) // Context will be set by caller
    gpuImage.setImage(bitmap)
    
    val gpuFilter = when (filter) {
        ProfessionalFilter.ORIGINAL -> GPUImageFilter()
        
        // Basic Adjustments
        ProfessionalFilter.AUTO_ENHANCE -> createAutoEnhanceFilter()
        ProfessionalFilter.BRIGHTNESS -> GPUImageBrightnessFilter(0.15f)
        ProfessionalFilter.CONTRAST -> GPUImageContrastFilter(1.4f)
        ProfessionalFilter.SATURATION -> GPUImageSaturationFilter(1.5f)
        ProfessionalFilter.WARMTH -> createWarmthFilter()
        ProfessionalFilter.COOL -> createCoolFilter()
        
        // Color Filters
        ProfessionalFilter.SEPIA -> GPUImageSepiaFilter()
        ProfessionalFilter.GRAYSCALE -> GPUImageGrayscaleFilter()
        ProfessionalFilter.INVERT -> GPUImageColorInvertFilter()
        ProfessionalFilter.MONOCHROME -> GPUImageMonochromeFilter()
        
        // Artistic
        ProfessionalFilter.VINTAGE -> createVintageFilter()
        ProfessionalFilter.RETRO -> createRetroFilter()
        ProfessionalFilter.SKETCH -> GPUImageSketchFilter()
        ProfessionalFilter.TOON -> GPUImageSmoothToonFilter()
        ProfessionalFilter.POSTERIZE -> GPUImagePosterizeFilter(4)
        ProfessionalFilter.HALFTONE -> GPUImageHalftoneFilter(0.01f, 0.2f)
        
        // Effects
        ProfessionalFilter.VIGNETTE -> createVignetteFilter(bitmap)
        ProfessionalFilter.GAUSSIAN_BLUR -> GPUImageGaussianBlurFilter(2.0f)
        ProfessionalFilter.SHARPEN -> GPUImageSharpenFilter(1.0f)
        ProfessionalFilter.EDGE_DETECT -> GPUImageSobelEdgeDetectionFilter()
        ProfessionalFilter.EMBOSS -> GPUImageEmbossFilter(1.0f)
        ProfessionalFilter.CROSSHATCH -> GPUImageCrosshatchFilter(0.03f, 0.004f)
        
        // Blend modes (simplified - just use overlay as default)
        ProfessionalFilter.OVERLAY -> GPUImageOverlayBlendFilter()
        ProfessionalFilter.HARD_LIGHT -> GPUImageHardLightBlendFilter()
        ProfessionalFilter.SOFT_LIGHT -> GPUImageSoftLightBlendFilter()
        ProfessionalFilter.DARKEN -> GPUImageDarkenBlendFilter()
        ProfessionalFilter.LIGHTEN -> GPUImageLightenBlendFilter()
    }
    
    gpuImage.setFilter(gpuFilter)
    return gpuImage.bitmapWithFilterApplied
}

// Filter creation helpers
private fun createAutoEnhanceFilter(): GPUImageFilterGroup {
    return GPUImageFilterGroup(listOf(
        GPUImageBrightnessFilter(0.1f),
        GPUImageContrastFilter(1.2f),
        GPUImageSaturationFilter(1.15f),
        GPUImageSharpenFilter(0.3f)
    ))
}

private fun createWarmthFilter(): GPUImageFilterGroup {
    // Warm filter using color matrix
    val colorMatrix = floatArrayOf(
        1.3f, 0.1f, 0f, 0f, 0.15f,
        0.1f, 1.05f, 0.05f, 0f, 0.08f,
        0f, 0.1f, 0.85f, 0f, -0.08f,
        0f, 0f, 0f, 1f, 0f
    )
    return GPUImageFilterGroup(listOf(
        GPUImageColorMatrixFilter(colorMatrix),
        GPUImageSaturationFilter(1.1f)
    ))
}

private fun createCoolFilter(): GPUImageFilterGroup {
    // Cool filter using color matrix
    val colorMatrix = floatArrayOf(
        0.8f, 0.05f, 0.1f, 0f, -0.08f,
        0.1f, 1.0f, 0.1f, 0f, 0.03f,
        0.05f, 0.15f, 1.15f, 0f, 0.12f,
        0f, 0f, 0f, 1f, 0f
    )
    return GPUImageFilterGroup(listOf(
        GPUImageColorMatrixFilter(colorMatrix),
        GPUImageSaturationFilter(1.1f)
    ))
}

private fun createVintageFilter(): GPUImageFilterGroup {
    return GPUImageFilterGroup(listOf(
        GPUImageSepiaFilter(),
        GPUImageSaturationFilter(0.7f),
        createVignetteFilter(null),
        GPUImageBrightnessFilter(-0.05f)
    ))
}

private fun createRetroFilter(): GPUImageFilterGroup {
    val colorMatrix = floatArrayOf(
        1.2f, 0.1f, -0.05f, 0f, 0.1f,
        0.05f, 1.05f, 0.05f, 0f, 0.05f,
        -0.05f, 0.1f, 1.1f, 0f, 0.1f,
        0f, 0f, 0f, 1f, 0f
    )
    return GPUImageFilterGroup(listOf(
        GPUImageColorMatrixFilter(colorMatrix),
        GPUImageContrastFilter(1.25f),
        GPUImageSaturationFilter(1.3f),
        GPUImageVignetteFilter(
            floatArrayOf(0.5f, 0.5f),
            floatArrayOf(0.3f, 0.3f),
            0.3f,
            floatArrayOf(0.95f, 0.85f, 0.7f)
        )
    ))
}

private fun createVignetteFilter(bitmap: Bitmap?): GPUImageVignetteFilter {
    return GPUImageVignetteFilter(
        floatArrayOf(0.5f, 0.5f), // vignette center
        floatArrayOf(0.4f, 0.4f), // vignette color (RGBA)
        0.35f, // vignette start
        floatArrayOf(0.85f, 0.75f, 0.6f) // vignette end color
    )
}

/**
 * Data class representing a filter with adjustable parameters
 */
data class AdjustableFilter(
    val filter: ProfessionalFilter,
    var intensity: Float = 1.0f, // 0.0 to 2.0
    var brightness: Float = 0.0f, // -1.0 to 1.0
    var contrast: Float = 1.0f, // 0.0 to 2.0
    var saturation: Float = 1.0f // 0.0 to 2.0
)

/**
 * Apply filter with custom parameters
 */
fun applyAdjustableFilter(
    bitmap: Bitmap,
    adjustable: AdjustableFilter
): Bitmap {
    val filters = mutableListOf<GPUImageFilter>()
    
    // Apply brightness
    if (adjustable.brightness != 0.0f) {
        filters.add(GPUImageBrightnessFilter(adjustable.brightness))
    }
    
    // Apply contrast
    if (adjustable.contrast != 1.0f) {
        filters.add(GPUImageContrastFilter(adjustable.contrast))
    }
    
    // Apply saturation
    if (adjustable.saturation != 1.0f) {
        filters.add(GPUImageSaturationFilter(adjustable.saturation))
    }
    
    // Apply main filter with intensity
    val mainFilter = when (adjustable.filter) {
        ProfessionalFilter.BRIGHTNESS -> GPUImageBrightnessFilter(adjustable.intensity * 0.5f)
        ProfessionalFilter.CONTRAST -> GPUImageContrastFilter(1.0f + (adjustable.intensity - 1.0f) * 0.5f)
        ProfessionalFilter.SATURATION -> GPUImageSaturationFilter(adjustable.intensity)
        ProfessionalFilter.GAUSSIAN_BLUR -> GPUImageGaussianBlurFilter(adjustable.intensity * 3.0f)
        ProfessionalFilter.SHARPEN -> GPUImageSharpenFilter(adjustable.intensity * 2.0f)
        else -> null
    }
    
    mainFilter?.let { filters.add(it) }
    
    return if (filters.isEmpty()) {
        bitmap
    } else {
        val gpuImage = GPUImage(null)
        gpuImage.setImage(bitmap)
        gpuImage.setFilter(GPUImageFilterGroup(filters))
        gpuImage.bitmapWithFilterApplied
    }
}
