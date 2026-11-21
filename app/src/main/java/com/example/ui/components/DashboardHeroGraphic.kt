package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.domain.model.CallStateEnum
import com.example.ui.theme.LyraTheme
import com.example.utils.MediaCropTransform
import com.example.utils.ProfileMediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun DashboardHeroGraphic(
    mediaUri: String?,
    mediaType: String,
    mediaCrop: MediaCropTransform,
    callState: CallStateEnum,
    customStatusText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    showLabels: Boolean = true,
    testTagName: String = "dashboard_hero_graphic"
) {
    val hasCustomMedia = !mediaUri.isNullOrBlank() && (mediaType == "IMAGE" || mediaType == "VIDEO")
    val colors = LyraTheme.colors

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTagName),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasCustomMedia) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (showLabels) {
                    LyraOrb(
                        size = size,
                        state = callState,
                        customStatusText = customStatusText
                    )
                } else {
                    LyraOrb(
                        size = size,
                        state = callState,
                        customStatusText = ""
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated.copy(alpha = 0.9f))
                        .border(1.dp, colors.primary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Customize photo or video",
                        tint = colors.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(size * 0.92f)
                        .clip(CircleShape)
                        .background(colors.background)
                        .border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(listOf(colors.primary, colors.secondary)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaType == "VIDEO") {
                        LoopingCroppedVideoView(
                            videoUri = mediaUri!!,
                            crop = mediaCrop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = mediaUri,
                            contentDescription = "Custom profile graphic",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mediaType == "VIDEO") Icons.Default.Videocam else Icons.Default.Edit,
                        contentDescription = "Change or crop graphic",
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (showLabels) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "LYRA AI",
                    color = colors.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = customStatusText,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun LoopingCroppedVideoView(
    videoUri: String,
    crop: MediaCropTransform,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var videoW by remember { mutableIntStateOf(0) }
    var videoH by remember { mutableIntStateOf(0) }

    fun applyTransformMatrix(tv: TextureView?) {
        val view = tv ?: return
        val vw = view.width.toFloat()
        val vh = view.height.toFloat()
        if (vw <= 0f || vh <= 0f || videoW <= 0 || videoH <= 0) return

        val sx = vw / videoW.toFloat()
        val sy = vh / videoH.toFloat()
        val fillScale = max(sx, sy)
        val scaleX = (fillScale / sx) * crop.scale.coerceIn(1f, 3f)
        val scaleY = (fillScale / sy) * crop.scale.coerceIn(1f, 3f)

        val drawnW = vw * scaleX
        val drawnH = vh * scaleY
        val maxShiftX = ((drawnW - vw) / 2f).coerceAtLeast(0f)
        val maxShiftY = ((drawnH - vh) / 2f).coerceAtLeast(0f)
        val tx = (crop.offsetX * vw).coerceIn(-maxShiftX, maxShiftX)
        val ty = (crop.offsetY * vh).coerceIn(-maxShiftY, maxShiftY)

        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, vw / 2f, vh / 2f)
            postTranslate(tx, ty)
        }
        view.setTransform(matrix)
    }

    LaunchedEffect(crop, videoW, videoH) {
        applyTransformMatrix(textureViewRef)
    }

    DisposableEffect(videoUri) {
        onDispose {
            runCatching {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            }
            mediaPlayerRef = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val textureView = TextureView(ctx)
            textureViewRef = textureView
            textureView.apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            mediaPlayerRef?.release()
                            val mp = MediaPlayer().apply {
                                setDataSource(context, Uri.parse(videoUri))
                                setSurface(Surface(surface))
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnVideoSizeChangedListener { _, w, h ->
                                    videoW = w
                                    videoH = h
                                    applyTransformMatrix(textureView)
                                }
                                setOnPreparedListener { player ->
                                    videoW = player.videoWidth
                                    videoH = player.videoHeight
                                    applyTransformMatrix(textureView)
                                    player.start()
                                }
                                prepareAsync()
                            }
                            mediaPlayerRef = mp
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        applyTransformMatrix(textureView)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        runCatching {
                            mediaPlayerRef?.stop()
                            mediaPlayerRef?.release()
                        }
                        mediaPlayerRef = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        }
    )
}

@Composable
fun DashboardMediaCropDialog(
    pickedUri: Uri,
    isVideo: Boolean,
    hasExistingCustomMedia: Boolean,
    onApplyImage: (Bitmap, MediaCropTransform) -> Unit,
    onApplyVideo: (Uri, MediaCropTransform) -> Unit,
    onResetToOrb: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LyraTheme.colors
    var loadedBitmap by remember(pickedUri, isVideo) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pickedUri, isVideo) { mutableStateOf(!isVideo) }
    var isSaving by remember { mutableStateOf(false) }

    var scale by remember(pickedUri) { mutableFloatStateOf(1f) }
    var offsetXFraction by remember(pickedUri) { mutableFloatStateOf(0f) }
    var offsetYFraction by remember(pickedUri) { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(pickedUri, isVideo) {
        if (!isVideo) {
            isLoading = true
            val bmp = withContext(Dispatchers.IO) {
                ProfileMediaStorage.decodeSampledBitmap(context, pickedUri)
            }
            loadedBitmap = bmp
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isVideo) "Crop & Frame Video" else "Crop Profile Photo",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Drag to position and use slider or pinch to zoom",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )

                val currentCrop = MediaCropTransform(scale, offsetXFraction, offsetYFraction)

                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.background)
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(16.dp))
                        .onSizeChanged { viewportPx = it.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 3f)
                                scale = newScale
                                val maxFrac = ((newScale - 1f) / 2f).coerceAtLeast(0.35f)
                                offsetXFraction = (offsetXFraction + pan.x / viewportPx).coerceIn(-maxFrac, maxFrac)
                                offsetYFraction = (offsetYFraction + pan.y / viewportPx).coerceIn(-maxFrac, maxFrac)
                            }
                        }
                        .testTag("media_crop_viewport"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = colors.primary)
                    } else if (isVideo) {
                        LoopingCroppedVideoView(
                            videoUri = pickedUri.toString(),
                            crop = currentCrop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (loadedBitmap != null) {
                        val bmp = loadedBitmap!!
                        val w = bmp.width.toFloat()
                        val h = bmp.height.toFloat()
                        val baseScale = max(viewportPx / w, viewportPx / h)
                        val totalScale = baseScale * scale
                        val maxShiftX = ((w * totalScale - viewportPx) / 2f).coerceAtLeast(0f)
                        val maxShiftY = ((h * totalScale - viewportPx) / 2f).coerceAtLeast(0f)
                        val tx = (offsetXFraction * viewportPx).coerceIn(-maxShiftX, maxShiftX)
                        val ty = (offsetYFraction * viewportPx).coerceIn(-maxShiftY, maxShiftY)

                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Crop preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = tx
                                    translationY = ty
                                }
                        )
                    } else {
                        Text(
                            text = "Unable to load selected media",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }

                    // Circular crop frame guide overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = this.size.minDimension / 2f * 0.94f
                        val center = Offset(this.size.width / 2f, this.size.height / 2f)
                        drawCircle(
                            color = colors.primary.copy(alpha = 0.85f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zoom ${"%.1f".format(scale)}x",
                        color = colors.primaryGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(68.dp)
                    )
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 1f..3f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.glassBorder
                        ),
                        modifier = Modifier.weight(1f).testTag("media_crop_zoom_slider")
                    )
                }

                if (hasExistingCustomMedia) {
                    TextButton(
                        onClick = onResetToOrb,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("reset_default_orb_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = colors.secondaryGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset to Default Lyra Orb",
                            color = colors.secondaryGlow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    val crop = MediaCropTransform(scale, offsetXFraction, offsetYFraction)
                    if (isVideo) {
                        onApplyVideo(pickedUri, crop)
                    } else {
                        val bmp = loadedBitmap
                        if (bmp != null) {
                            onApplyImage(bmp, crop)
                        } else {
                            isSaving = false
                            onDismiss()
                        }
                    }
                },
                enabled = !isLoading && !isSaving && (isVideo || loadedBitmap != null),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("media_crop_apply_btn")
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "Crop & Apply",
                    color = colors.onPrimaryButton,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}
