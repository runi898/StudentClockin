package com.familycheckin.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.ui.BackHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeliveryUploadPayload(
    val submissionType: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)

@Composable
fun TaskDeliveryScreen(
    taskTitle: String,
    requirement: DeliveryRequirement,
    statusMessage: String,
    isBusy: Boolean,
    onSubmit: (DeliveryUploadPayload) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localMessage by remember { mutableStateOf("请选择交付方式。") }
    var captureUriValue by rememberSaveable { mutableStateOf<String?>(null) }

    fun handleUri(uri: Uri, submissionType: String, fallbackName: String) {
        scope.launch {
            localMessage = "正在读取文件..."
            val payload = withContext(Dispatchers.IO) {
                readUploadPayload(
                    context = context,
                    uri = uri,
                    submissionType = submissionType,
                    fallbackName = fallbackName
                )
            }
            if (payload != null) {
                localMessage = "正在上传..."
                onSubmit(payload)
            } else {
                localMessage = "无法读取你选择的文件，请重试。"
            }
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleUri(uri, "photo", "photo.jpg")
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            captureUriValue?.let { handleUri(Uri.parse(it), "photo", "photo.jpg") }
        } else {
            localMessage = "拍照已取消。"
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleUri(uri, "video", "video.mp4")
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            captureUriValue?.let { handleUri(Uri.parse(it), "video", "video.mp4") }
        } else {
            localMessage = "录像已取消。"
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleUri(uri, "audio", "audio.m4a")
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            handleUri(uri, "audio", "audio.m4a")
        } else {
            localMessage = "录音已取消。"
        }
    }

    ScreenBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BackHeader(
                        title = "任务交付",
                        subtitle = "$taskTitle\n${requirementHint(requirement)}",
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                    StatusBanner(message = if (isBusy) statusMessage else localMessage)
                }
            }

            item {
                SectionSurface {
                    Text("交付方式", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "上传成功后，系统会自动把这次任务记为完成，并按规则发放积分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )

                    when (requirement) {
                        DeliveryRequirement.PHOTO -> {
                            Button(
                                onClick = {
                                    val captureUri = createCaptureUri(context, "jpg")
                                    captureUriValue = captureUri.toString()
                                    takePhotoLauncher.launch(captureUri)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy
                            ) {
                                Text("拍照上传")
                            }
                            Button(
                                onClick = {
                                    pickPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy
                            ) {
                                Text("从相册选择")
                            }
                        }

                        DeliveryRequirement.VIDEO -> {
                            Button(
                                onClick = {
                                    val captureUri = createCaptureUri(context, "mp4")
                                    captureUriValue = captureUri.toString()
                                    captureVideoLauncher.launch(captureUri)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy
                            ) {
                                Text("录制视频")
                            }
                            Button(
                                onClick = { pickVideoLauncher.launch("video/*") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy
                            ) {
                                Text("选择视频")
                            }
                        }

                        DeliveryRequirement.AUDIO -> {
                            Button(
                                onClick = {
                                    val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                                    recordAudioLauncher.launch(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy
                            ) {
                                Text("录制音频")
                            }
                            Button(
                                onClick = { pickAudioLauncher.launch("audio/*") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusy
                            ) {
                                Text("选择音频")
                            }
                        }

                        DeliveryRequirement.NONE -> {
                            Text(
                                "这个任务不需要额外交付内容，直接完成即可。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun requirementHint(requirement: DeliveryRequirement): String {
    return when (requirement) {
        DeliveryRequirement.PHOTO -> "请上传照片，上传成功后会自动记为完成。"
        DeliveryRequirement.VIDEO -> "请上传视频，上传成功后会自动记为完成。"
        DeliveryRequirement.AUDIO -> "请上传音频，上传成功后会自动记为完成。"
        DeliveryRequirement.NONE -> "这个任务没有额外的交付要求。"
    }
}

private fun createCaptureUri(context: Context, extension: String): Uri {
    val directory = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(directory, "capture-${UUID.randomUUID()}.$extension")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

private fun readUploadPayload(
    context: Context,
    uri: Uri,
    submissionType: String,
    fallbackName: String
): DeliveryUploadPayload? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mimeType = context.contentResolver.getType(uri)
        ?: defaultContentType(submissionType)
    val displayName = queryDisplayName(context, uri) ?: fallbackName
    return DeliveryUploadPayload(
        submissionType = submissionType,
        fileName = displayName,
        contentType = mimeType,
        bytes = bytes
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
}

private fun defaultContentType(submissionType: String): String {
    return when (submissionType) {
        "photo" -> "image/jpeg"
        "video" -> "video/mp4"
        "audio" -> "audio/m4a"
        else -> "application/octet-stream"
    }
}
