package iecxiv.meshfabric.ui.files

import iecxiv.meshfabric.data.model.DevicePlatform
import iecxiv.meshfabric.data.model.platform
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.NumberedTextEditor
import iecxiv.meshfabric.data.model.TransferType
import iecxiv.meshfabric.data.remote.checkDownloadFileExists
import iecxiv.meshfabric.data.remote.getSuggestedFileName
import iecxiv.meshfabric.data.remote.getSuggestedRemoteFileName
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import iecxiv.meshfabric.data.model.FileItem
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun isTextFileCandidate(item: FileItem): Boolean {
    if (item.isDir) return false
    val nameLower = item.name.lowercase()
    val binaryExtensions = setOf(
        ".apk", ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
        ".zip", ".tar", ".gz", ".tgz", ".xz", ".7z", ".rar",
        ".iso", ".bin", ".exe", ".so", ".dll", ".pdf",
        ".mp4", ".mkv", ".avi", ".mp3", ".wav", ".flac"
    )
    for (ext in binaryExtensions) {
        if (nameLower.endsWith(ext)) return false
    }
    return item.size <= 200 * 1024L // <= 200 KB (límite oficial MeshCentral)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerContent(
    nodeId: String,
    nodeName: String,
    viewModel: FileManagerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val canCreateFiles = LocalFileCreationAllowed.current
    val isConnected by viewModel.isConnected(nodeId).collectAsState()
    val currentPath by viewModel.currentPath(nodeId).collectAsState()
    val driveEntries by viewModel.drives(nodeId).collectAsState()
    val windowsFiles = viewModel.isWindowsFiles(nodeId)
    val driveSelector = windowsFiles && iecxiv.meshfabric.data.remote.WindowsDriveSupport.selector(currentPath)

    val fileList by viewModel.fileList(nodeId).collectAsState()
    val statusMessage by viewModel.statusMessage(nodeId).collectAsState()
    val clipboardItem by viewModel.clipboardItem(nodeId).collectAsState()
    val isCutMode by viewModel.isCutMode(nodeId).collectAsState()
    val fileContentState by viewModel.fileContent(nodeId).collectAsState()
    val fileReadingProgress by viewModel.fileReadingProgress(nodeId).collectAsState()
    val activeTransfers by viewModel.activeTransfers(nodeId).collectAsState()

    val activeZipTransfer = activeTransfers.firstOrNull { it.type == TransferType.COMPRESS && !it.isCompleted }
    val isCompressing = activeZipTransfer != null

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var initialFileContent by remember { mutableStateOf("") }

    var showGoToDialog by remember { mutableStateOf(false) }
    var goToPathInput by remember { mutableStateOf("") }

    var selectedItemForAction by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showRenameOverwriteConfirmDialog by remember { mutableStateOf(false) }
    var pendingRenameItem by remember { mutableStateOf<FileItem?>(null) }
    var pendingRenameNewName by remember { mutableStateOf("") }

    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var editingFilePath by remember { mutableStateOf<String?>(null) }
    var editingFileText by remember { mutableStateOf("") }

    var itemToZip by remember { mutableStateOf<FileItem?>(null) }
    var showZipDialog by remember { mutableStateOf(false) }
    var zipBaseNameInput by remember { mutableStateOf("") }
    var showZipOverwriteConfirmDialog by remember { mutableStateOf(false) }
    var pendingZipName by remember { mutableStateOf("") }
    var showZipWarningDialog by remember { mutableStateOf(false) }
    var pendingZipTarget by remember { mutableStateOf<FileItem?>(null) }
    var pendingZipFinalName by remember { mutableStateOf("") }

    var showPasteConflictConfirmDialog by remember { mutableStateOf(false) }
    var showPasteRenameDialog by remember { mutableStateOf(false) }
    var pasteTargetNameInput by remember { mutableStateOf("") }

    var downloadConflictItem by remember { mutableStateOf<FileItem?>(null) }
    var downloadConflictRenameMode by remember { mutableStateOf(false) }
    var downloadConflictCustomName by remember { mutableStateOf("") }

    val handleInitiateDownload = { targetItem: FileItem ->
        if (checkDownloadFileExists(context, targetItem.name)) {
            downloadConflictItem = targetItem
            downloadConflictRenameMode = false
            downloadConflictCustomName = getSuggestedFileName(context, targetItem.name)
        } else {
            viewModel.downloadFileToDownloads(context, nodeId, targetItem)
        }
    }

    var isTransfersPanelExpanded by remember { mutableStateOf(true) }

    var fabMenuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(currentPath) {
        listState.scrollToItem(0)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notificaciones de descarga activadas", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var uploadConflictUri by remember { mutableStateOf<Uri?>(null) }
    var uploadConflictFileName by remember { mutableStateOf("") }
    var uploadConflictRenameMode by remember { mutableStateOf(false) }
    var uploadConflictCustomName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri)
            val fileExists = fileList.any { it.name.equals(fileName, ignoreCase = true) }
            if (fileExists) {
                uploadConflictUri = uri
                uploadConflictFileName = fileName
                uploadConflictRenameMode = false
                uploadConflictCustomName = getSuggestedRemoteFileName(fileList.map { it.name }, fileName)
            } else {
                try {
                    viewModel.uploadUri(nodeId, context, uri)
                    Toast.makeText(context, context.getString(R.string.starting_upload), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.upload_error, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatus(nodeId)
        }
    }

    LaunchedEffect(fileContentState) {
        val pair = fileContentState
        if (pair != null) {
            editingFilePath = pair.first
            editingFileText = pair.second
        }
    }

    DisposableEffect(nodeId) {
        viewModel.startSession(nodeId)
        onDispose { }
    }

    val filteredRows = remember(fileList, searchQuery) {
        fileListRows(fileList).filter { row ->
            searchQuery.isBlank() || row.item.name.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredList = remember(fileList, searchQuery) {
        if (searchQuery.isBlank()) {
            fileList
        } else {
            fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_files_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text(text = stringResource(R.string.files_title, nodeName), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (isConnected) stringResource(R.string.status_connected) else stringResource(R.string.files_connecting),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) Color.Green else Color.Yellow
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isSearchActive) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Buscar"
                        )
                    }
                    IconButton(
                        onClick = {
                            goToPathInput = currentPath
                            showGoToDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Ir a Ruta")
                    }
                    IconButton(onClick = { viewModel.openDirectory(nodeId, "/") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Storage, contentDescription = stringResource(if (windowsFiles) R.string.files_drives else R.string.files_root_shortcut_label), tint = if (if (windowsFiles) driveSelector else currentPath == "/") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { viewModel.refresh(nodeId) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                    IconButton(
                        onClick = {
                            viewModel.stopSession(nodeId)
                            onBack()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Desconectar Archivos",
                            tint = Color.Red
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {},
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                if (activeTransfers.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTransfersPanelExpanded = !isTransfersPanelExpanded }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val activeCount = activeTransfers.size
                            val overallPercentage = if (activeCount > 0) {
                                (activeTransfers.map { it.percentage }.average() * 100).toInt()
                            } else 0

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val summaryLabel = when (activeTransfers[0].type) {
                                    TransferType.DOWNLOAD -> stringResource(R.string.downloading_status)
                                    TransferType.UPLOAD -> stringResource(R.string.uploading_status)
                                    TransferType.COPY -> stringResource(R.string.copy)
                                    TransferType.MOVE -> stringResource(R.string.move)
                                    TransferType.COMPRESS -> stringResource(R.string.compress)
                                }
                                Text(
                                    text = if (activeCount == 1) {
                                        "$summaryLabel: ${activeTransfers[0].fileName} ($overallPercentage%)"
                                    } else {
                                        "$activeCount transferencias activas ($overallPercentage%)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { isTransfersPanelExpanded = !isTransfersPanelExpanded },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTransfersPanelExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(if (isTransfersPanelExpanded) R.string.collapse_transfers else R.string.expand_transfers)
                                    )
                                }

                                Box(modifier = Modifier.heightIn(min = 42.dp)) {
                                    if (!LocalHideRootFileFab.current) FloatingActionButton(
                                        onClick = { fabMenuExpanded = !fabMenuExpanded },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.creation_options), modifier = Modifier.size(22.dp))
                                    }

                                    DropdownMenu(
                                        expanded = fabMenuExpanded && !LocalHideRootFileFab.current,
                                        onDismissRequest = { fabMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.new_folder)) },
                                            enabled = ((!driveSelector) && (!LocalAndroidFiles.current)) && LocalFileCreationAllowed.current,
                                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                            onClick = {
                                                fabMenuExpanded = false
                                                if (canCreateFiles) showCreateFolderDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.new_text_file)) },
                                            enabled = ((!driveSelector) && (!LocalAndroidFiles.current)) && LocalFileCreationAllowed.current,
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                                            onClick = {
                                                fabMenuExpanded = false
                                                if (canCreateFiles) showCreateFileDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.upload_file)) },
                                            enabled = (!driveSelector) && (LocalFileUploads.current),
                                            leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                            onClick = {
                                                fabMenuExpanded = false
                                                filePickerLauncher.launch("*/*")
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (isTransfersPanelExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 210.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    activeTransfers.forEach { transfer ->
                                        val transferIcon = when (transfer.type) {
                                            TransferType.DOWNLOAD -> Icons.Default.Download
                                            TransferType.UPLOAD -> Icons.Default.Upload
                                            TransferType.COPY -> Icons.Default.ContentCopy
                                            TransferType.MOVE -> Icons.Default.ContentCut
                                            TransferType.COMPRESS -> Icons.Default.FolderZip
                                        }
                                        val transferLabel = when (transfer.type) {
                                            TransferType.DOWNLOAD -> stringResource(R.string.downloading_status)
                                            TransferType.UPLOAD -> stringResource(R.string.uploading_status)
                                            TransferType.COPY -> stringResource(R.string.copy)
                                            TransferType.MOVE -> stringResource(R.string.move)
                                            TransferType.COMPRESS -> stringResource(R.string.compress)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = transferIcon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "$transferLabel: ${transfer.fileName}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "${(transfer.percentage * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                LinearProgressIndicator(
                                                    progress = { transfer.percentage },
                                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${formatFileSize(transfer.bytesTransferred)} / ${formatFileSize(transfer.totalBytes)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (transfer.type != TransferType.COMPRESS) {
                                                IconButton(
                                                    onClick = { viewModel.cancelTransfer(nodeId, transfer.filePath) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = stringResource(R.string.cancel_transfer),
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }

                                if (activeTransfers.size > 3) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "... (+${activeTransfers.size - 3} más, desliza para ver)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val dirCount = filteredList.count { it.isDir }
                            val fileCount = filteredList.count { !it.isDir }
                            val statusText = if (!isConnected) {
                                stringResource(R.string.status_connecting)
                            } else if (filteredList.isEmpty()) {
                                stringResource(R.string.empty_folder)
                            } else {
                                stringResource(R.string.folders_and_files_count, dirCount, fileCount)
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(modifier = Modifier.heightIn(min = 42.dp)) {
                            if (!LocalHideRootFileFab.current) FloatingActionButton(
                                onClick = { fabMenuExpanded = !fabMenuExpanded },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Opciones de creación", modifier = Modifier.size(22.dp))
                            }

                            DropdownMenu(
                                expanded = fabMenuExpanded && !LocalHideRootFileFab.current,
                                onDismissRequest = { fabMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
 enabled = LocalFileCreationAllowed.current,
                                    text = { Text("Nueva Carpeta") },
                                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                    onClick = {
                                        fabMenuExpanded = false
                                        if (canCreateFiles) showCreateFolderDialog = true
                                    }
                                )
                                DropdownMenuItem(
 enabled = LocalFileCreationAllowed.current,
                                    text = { Text("Nuevo Archivo de Texto") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                                    onClick = {
                                        fabMenuExpanded = false
                                        if (canCreateFiles) showCreateFileDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.upload_file)) },
                                            enabled = (!driveSelector) && (LocalFileUploads.current),
                                    leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                    onClick = {
                                        fabMenuExpanded = false
                                        filePickerLauncher.launch("*/*")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Navigation & Clipboard Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateUp(nodeId) },
                        enabled = !isCompressing && currentPath.isNotEmpty() && currentPath != "/"
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Subir carpeta")
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = currentPath.ifEmpty { "/" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (clipboardItem != null) {
                        Button(
                            onClick = {
                                val source = clipboardItem
                                if (source != null) {
                                    val defaultTargetName = source.name
                                    val fileExists = fileList.any { it.name.equals(defaultTargetName, ignoreCase = true) }
                                    if (fileExists) {
                                        pasteTargetNameInput = getSuggestedRemoteFileName(fileList.map { it.name }, defaultTargetName)
                                        showPasteConflictConfirmDialog = true
                                    } else {
                                        viewModel.pasteItem(nodeId, context = context)
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isCutMode) stringResource(R.string.move) else stringResource(R.string.paste))
                        }
                        IconButton(onClick = { viewModel.clearClipboard(nodeId) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancelar portapapeles")
                        }
                    }
                }
            }

            // File Reading Inline Progress Banner
            val currentReadingProgress = fileReadingProgress
            var lastNonNullReadingProgress by remember { mutableFloatStateOf(0.05f) }
            if (currentReadingProgress != null) {
                lastNonNullReadingProgress = currentReadingProgress.coerceAtLeast(0.05f)
            }

            AnimatedVisibility(visible = currentReadingProgress != null) {
                val progress = lastNonNullReadingProgress
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.opening_file, (progress * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.cancelFileReading(nodeId) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancelar lectura de archivo",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // File & Folder List
            Box(modifier = Modifier.weight(1f)) {
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isConnected) stringResource(R.string.empty_folder_or_no_match) else stringResource(R.string.status_connecting),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredRows, key = { it.key }) { fileRow ->
                        val item = fileRow.item
                            val drive = if (driveSelector) driveEntries.firstOrNull { it.path == item.path } else null
                            if (drive != null) {
                                WindowsDriveRow(drive) { viewModel.openDirectory(nodeId, drive.path) }
                                return@items
                            }

                            var menuExpanded by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        if (item.isDir) {
                                            viewModel.openDirectory(nodeId, item.path)
                                        } else {
                                            handleInitiateDownload(item)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isDir) {
                                            Icons.Default.Folder
                                        } else if (item.name.endsWith(".zip") || item.name.endsWith(".tar.gz") || item.name.endsWith(".tgz")) {
                                            Icons.Default.FolderZip
                                        } else {
                                            Icons.AutoMirrored.Filled.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = if (item.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(28.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (item.isDir) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (!item.isDir) {
                                            Text(
                                                text = formatFileSize(item.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Box {
                                        IconButton(onClick = { menuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                                        }

                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false }
                                        ) {
                                            if (!item.isDir) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.download_to_android)) },
                                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        handleInitiateDownload(item)
                                                    }
                                                )
                                                if (isTextFileCandidate(item)) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.edit_file)) },
                                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                        onClick = {
                                                            menuExpanded = false
                                                            viewModel.readFileContent(nodeId, item)
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.copy)) },
                                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        viewModel.copyItem(nodeId, item)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.cut)) },
                                                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        viewModel.cutItem(nodeId, item)
                                                    }
                                                )
                                                HorizontalDivider()
                                            }
                                            DropdownMenuItem(
 enabled = !LocalAndroidFiles.current,
                                                text = { Text(stringResource(R.string.compress_zip)) },
                                                leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    itemToZip = item
                                                    zipBaseNameInput = item.name.substringBeforeLast('.')
                                                    showZipDialog = true
                                                }
                                            )
                                            if (item.name.endsWith(".zip") || item.name.endsWith(".tar.gz") || item.name.endsWith(".tgz")) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.decompress_zip)) },
                                                    leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        viewModel.decompressZip(nodeId, item)
                                                    }
                                                )
                                            }

                                            HorizontalDivider()
                                            DropdownMenuItem(
 enabled = !LocalAndroidFiles.current,
                                                text = { Text(stringResource(R.string.rename)) },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    selectedItemForAction = item
                                                    val otherNames = fileList.filter { it.path != item.path }.map { it.name }
                                                    renameInput = getSuggestedRemoteFileName(otherNames, item.name)
                                                    showRenameDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
 enabled = !LocalAndroidFiles.current,
                                                text = { Text(stringResource(R.string.delete)) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                                                onClick = {
                                                    menuExpanded = false
                                                    itemToDelete = item
                                                    showDeleteConfirmDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isCompressing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                            .pointerInput(Unit) {
                                detectTapGestures { }
                            }
                            .clickable(enabled = true, onClick = {}),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(0.9f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (activeZipTransfer.totalBytes > 0L) {
                                    CircularProgressIndicator(
                                        progress = { activeZipTransfer.percentage },
                                        modifier = Modifier.size(52.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(52.dp))
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = stringResource(R.string.compress_in_progress_overlay_msg),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                if (activeZipTransfer.percentage > 0f) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${(activeZipTransfer.percentage * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // New Folder Dialog
        if (showCreateFolderDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showCreateFolderDialog = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.create_new_folder_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text(stringResource(R.string.folder_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showCreateFolderDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newFolderName.isNotBlank()) {
                                        viewModel.createFolder(nodeId, newFolderName.trim())
                                        newFolderName = ""
                                    }
                                    showCreateFolderDialog = false
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(stringResource(R.string.create_folder), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // New File Dialog
        if (showCreateFileDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showCreateFileDialog = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.create_new_file_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newFileName,
                            onValueChange = { newFileName = it },
                            label = { Text(stringResource(R.string.file_name_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = initialFileContent,
                            onValueChange = { initialFileContent = it },
                            label = { Text(stringResource(R.string.file_initial_content_placeholder)) },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp, max = 90.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showCreateFileDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newFileName.isNotBlank()) {
                                        viewModel.createFile(nodeId, newFileName.trim(), initialFileContent)
                                        newFileName = ""
                                        initialFileContent = ""
                                    }
                                    showCreateFileDialog = false
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(stringResource(R.string.create_file), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Go To Path Dialog ("Ir a")
        if (showGoToDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showGoToDialog = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.go_to_path_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = goToPathInput,
                            onValueChange = { goToPathInput = it },
                            label = { Text(stringResource(R.string.remote_path_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showGoToDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (goToPathInput.isNotBlank()) {
                                        viewModel.openDirectory(nodeId, goToPathInput.trim())
                                    }
                                    showGoToDialog = false
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(stringResource(R.string.go), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Text File Editor Modal Dialog
        if (editingFilePath != null) {
            var isWordWrapEnabled by remember { mutableStateOf(false) }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = {
                    editingFilePath = null
                    viewModel.clearFileContent(nodeId)
                },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.85f)
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        // Header with File Name & Word Wrap Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Editando: ${editingFilePath?.substringAfterLast('/')}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = isWordWrapEnabled,
                                onClick = { isWordWrapEnabled = !isWordWrapEnabled },
                                label = { Text(stringResource(R.string.word_wrap), fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.WrapText,
                                        contentDescription = stringResource(R.string.word_wrap),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        NumberedTextEditor(
                            value = editingFileText,
                            onValueChange = { editingFileText = it },
                            wordWrap = isWordWrapEnabled,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    editingFilePath = null
                                    viewModel.clearFileContent(nodeId)
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val path = editingFilePath
                                    if (path != null) {
                                        viewModel.saveFileContent(nodeId, path, editingFileText)
                                    }
                                    editingFilePath = null
                                    viewModel.clearFileContent(nodeId)
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(stringResource(R.string.save_changes), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Rename Dialog
        if (showRenameDialog && selectedItemForAction != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showRenameDialog = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.rename_item_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            label = { Text(stringResource(R.string.new_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showRenameDialog = false },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val item = selectedItemForAction
                                    val newName = renameInput.trim()
                                    if (item != null && newName.isNotBlank()) {
                                        val fileExists = fileList.any {
                                            it.path != item.path && it.name.equals(newName, ignoreCase = true)
                                        }
                                        if (fileExists) {
                                            pendingRenameItem = item
                                            pendingRenameNewName = newName
                                            showRenameOverwriteConfirmDialog = true
                                        } else {
                                            viewModel.renameItem(nodeId, item, newName)
                                            showRenameDialog = false
                                        }
                                    }
                                },
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(stringResource(R.string.save), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Rename Overwrite Confirmation Dialog
        if (showRenameOverwriteConfirmDialog && pendingRenameItem != null) {
            val target = pendingRenameItem
            val newName = pendingRenameNewName
            AlertDialog(
                onDismissRequest = {
                    showRenameOverwriteConfirmDialog = false
                },
                title = { Text(stringResource(R.string.file_exists_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Text(stringResource(R.string.file_exists_overwrite_confirm, newName))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (target != null && newName.isNotBlank()) {
                                viewModel.renameItem(nodeId, target, newName)
                            }
                            showRenameOverwriteConfirmDialog = false
                            showRenameDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.overwrite))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                val otherNames = fileList.filter { it.path != target?.path }.map { it.name }
                                renameInput = getSuggestedRemoteFileName(otherNames, newName)
                                showRenameOverwriteConfirmDialog = false
                                showRenameDialog = true
                            }
                        ) {
                            Text(stringResource(R.string.rename))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                showRenameOverwriteConfirmDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog && itemToDelete != null) {
            val target = itemToDelete
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmDialog = false
                    itemToDelete = null
                },
                title = { Text(stringResource(R.string.confirm_delete_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Text(
                        if (target?.isDir == true) {
                            stringResource(R.string.delete_dir_confirm, target.name)
                        } else {
                            stringResource(R.string.delete_file_confirm, target?.name ?: "")
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (target != null) {
                                viewModel.deleteItem(nodeId, target)
                            }
                            showDeleteConfirmDialog = false
                            itemToDelete = null
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            itemToDelete = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // ZIP Compression Dialog
        if (showZipDialog && itemToZip != null) {
            val target = itemToZip
            AlertDialog(
                onDismissRequest = {
                    showZipDialog = false
                    itemToZip = null
                },
                title = { Text(stringResource(R.string.compress_zip_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column {
                        Text(stringResource(R.string.compressed_file_name_label))
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = zipBaseNameInput,
                            onValueChange = { input ->
                                val cleaned = if (input.endsWith(".zip", ignoreCase = true)) {
                                    input.dropLast(4)
                                } else {
                                    input
                                }
                                zipBaseNameInput = cleaned
                            },
                            trailingIcon = {
                                Text(
                                    text = ".zip",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val rawName = zipBaseNameInput.trim().ifBlank {
                                target?.name?.substringBeforeLast('.') ?: "comprimido"
                            }
                            val cleanBaseName = if (rawName.endsWith(".zip", ignoreCase = true)) {
                                rawName.dropLast(4)
                            } else {
                                rawName
                            }
                            val finalZipName = "$cleanBaseName.zip"

                            if (target != null) {
                                val fileExists = fileList.any { it.name.equals(finalZipName, ignoreCase = true) }
                                if (fileExists) {
                                    pendingZipName = finalZipName
                                    showZipOverwriteConfirmDialog = true
                                } else {
                                    pendingZipTarget = target
                                    pendingZipFinalName = finalZipName
                                    showZipWarningDialog = true
                                    showZipDialog = false
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.compress))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showZipDialog = false
                            itemToZip = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // ZIP Overwrite Confirmation Dialog
        if (showZipOverwriteConfirmDialog && itemToZip != null) {
            val target = itemToZip
            AlertDialog(
                onDismissRequest = {
                    showZipOverwriteConfirmDialog = false
                },
                title = { Text(stringResource(R.string.file_exists_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Text(stringResource(R.string.file_exists_overwrite_confirm, pendingZipName))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (target != null && pendingZipName.isNotEmpty()) {
                                pendingZipTarget = target
                                pendingZipFinalName = pendingZipName
                                showZipOverwriteConfirmDialog = false
                                showZipDialog = false
                                showZipWarningDialog = true
                            }
                        }
                    ) {
                        Text(stringResource(R.string.overwrite))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showZipOverwriteConfirmDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.change_name))
                    }
                }
            )
        }

        // ZIP Warning Confirmation Dialog
        if (showZipWarningDialog && pendingZipTarget != null && pendingZipFinalName.isNotEmpty()) {
            val target = pendingZipTarget
            val finalZipName = pendingZipFinalName

            AlertDialog(
                onDismissRequest = {
                    showZipWarningDialog = false
                    pendingZipTarget = null
                    pendingZipFinalName = ""
                    itemToZip = null
                },
                title = { Text(stringResource(R.string.compress_zip_warning_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Text(stringResource(R.string.compress_zip_warning_msg))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (target != null) {
                                viewModel.compressZip(nodeId, target, finalZipName)
                            }
                            showZipWarningDialog = false
                            pendingZipTarget = null
                            pendingZipFinalName = ""
                            itemToZip = null
                        }
                    ) {
                        Text(stringResource(R.string.action_continue), color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showZipWarningDialog = false
                            pendingZipTarget = null
                            pendingZipFinalName = ""
                            itemToZip = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Paste Conflict Overwrite Confirmation Dialog
        if (showPasteConflictConfirmDialog && clipboardItem != null) {
            val origName = clipboardItem!!.name
            AlertDialog(
                onDismissRequest = {
                    showPasteConflictConfirmDialog = false
                },
                title = { Text(stringResource(R.string.file_exists_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Text(stringResource(R.string.file_exists_overwrite_confirm, origName))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.pasteItem(nodeId, customTargetName = origName, context = context)
                            showPasteConflictConfirmDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.overwrite))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                pasteTargetNameInput = getSuggestedRemoteFileName(fileList.map { it.name }, origName)
                                showPasteConflictConfirmDialog = false
                                showPasteRenameDialog = true
                            }
                        ) {
                            Text(stringResource(R.string.rename))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                showPasteConflictConfirmDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            )
        }

        // Paste Rename Dialog
        if (showPasteRenameDialog && clipboardItem != null) {
            AlertDialog(
                onDismissRequest = {
                    showPasteRenameDialog = false
                },
                title = { Text(if (isCutMode) stringResource(R.string.move_item_title) else stringResource(R.string.copy_item_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column {
                        Text(stringResource(R.string.destination_name_label))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pasteTargetNameInput,
                            onValueChange = { pasteTargetNameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (pasteTargetNameInput.isNotBlank()) {
                                viewModel.pasteItem(nodeId, pasteTargetNameInput.trim(), context = context)
                            }
                            showPasteRenameDialog = false
                        }
                    ) {
                        Text(if (isCutMode) stringResource(R.string.move) else stringResource(R.string.paste))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPasteRenameDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Download Conflict Dialogs
        if (downloadConflictItem != null) {
            val conflictItem = downloadConflictItem!!
            if (!downloadConflictRenameMode) {
                AlertDialog(
                    onDismissRequest = { downloadConflictItem = null },
                    title = { Text(stringResource(R.string.file_exists_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = { Text(stringResource(R.string.file_exists_download_msg, conflictItem.name)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetItem = downloadConflictItem
                                downloadConflictItem = null
                                if (targetItem != null) {
                                    viewModel.downloadFileToDownloads(context, nodeId, targetItem, customName = targetItem.name, overwrite = true)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.overwrite))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    downloadConflictRenameMode = true
                                    downloadConflictCustomName = getSuggestedFileName(context, conflictItem.name)
                                }
                            ) {
                                Text(stringResource(R.string.rename))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { downloadConflictItem = null }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { downloadConflictItem = null },
                    title = { Text(stringResource(R.string.rename_download_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.download_name_label),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = downloadConflictCustomName,
                                onValueChange = { downloadConflictCustomName = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetItem = downloadConflictItem
                                val customName = downloadConflictCustomName.trim()
                                downloadConflictItem = null
                                if (targetItem != null && customName.isNotBlank()) {
                                    viewModel.downloadFileToDownloads(context, nodeId, targetItem, customName = customName, overwrite = true)
                                }
                            },
                            enabled = downloadConflictCustomName.isNotBlank()
                        ) {
                            Text(stringResource(R.string.download))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { downloadConflictItem = null }
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }

        // Upload Conflict Dialogs
        if (uploadConflictUri != null) {
            val fileName = uploadConflictFileName
            if (!uploadConflictRenameMode) {
                AlertDialog(
                    onDismissRequest = { uploadConflictUri = null },
                    title = { Text(stringResource(R.string.file_exists_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = { Text(stringResource(R.string.file_exists_overwrite_confirm, fileName)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetUri = uploadConflictUri
                                uploadConflictUri = null
                                if (targetUri != null) {
                                    viewModel.uploadUri(nodeId, context, targetUri, customFileName = fileName)
                                    Toast.makeText(context, context.getString(R.string.starting_upload), Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.overwrite))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    uploadConflictRenameMode = true
                                    uploadConflictCustomName = getSuggestedRemoteFileName(fileList.map { it.name }, fileName)
                                }
                            ) {
                                Text(stringResource(R.string.rename))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { uploadConflictUri = null }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { uploadConflictUri = null },
                    title = { Text(stringResource(R.string.rename_item_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.new_name),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = uploadConflictCustomName,
                                onValueChange = { uploadConflictCustomName = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetUri = uploadConflictUri
                                val customName = uploadConflictCustomName.trim()
                                uploadConflictUri = null
                                if (targetUri != null && customName.isNotBlank()) {
                                    viewModel.uploadUri(nodeId, context, targetUri, customFileName = customName)
                                    Toast.makeText(context, context.getString(R.string.starting_upload), Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = uploadConflictCustomName.isNotBlank()
                        ) {
                            Text(stringResource(R.string.upload))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { uploadConflictUri = null }
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "archivo_subido"
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
private val LocalAndroidFiles = staticCompositionLocalOf { false }
private val LocalFileUploads = staticCompositionLocalOf { true }

internal fun androidFileUploadAllowed(path: String): Boolean {
    val p = path.replace('\\', '/').trim('/').lowercase(Locale.ROOT)
    if (p.split('/').any { it == "." || it == ".." }) return false
    return listOf("sdcard", "storage/emulated/0", "mnt/sdcard", "storage/self/primary").any {
        p.startsWith("$it/") && p.removePrefix("$it/").isNotBlank()
    }
}

@Composable
fun FileManagerScreen(
    nodeId: String,
    nodeName: String,
    viewModel: FileManagerViewModel,
    onBack: () -> Unit
) {
    val groups by viewModel.meshClient.groupedDevices.collectAsState()
    val path by viewModel.currentPath(nodeId).collectAsState()
    val cleanId = nodeId.removePrefix("node//").removePrefix("node/").trim()
    val node = groups.asSequence().flatMap { it.second.asSequence() }.firstOrNull {
        it.id.removePrefix("node//").removePrefix("node/").trim() == cleanId
    }
    val android = node?.platform == DevicePlatform.ANDROID
    CompositionLocalProvider(
        LocalAndroidFiles provides android,
        LocalHideRootFileFab provides hideRootFileFab(android, viewModel.isWindowsFiles(nodeId), path),
        LocalFileCreationAllowed provides fileCreationAllowed(android, viewModel.isWindowsFiles(nodeId), path),
        LocalFileUploads provides (!android || androidFileUploadAllowed(path))
    ) {
        FileManagerContent(nodeId, nodeName, viewModel, onBack)
    }
}

private val LocalFileCreationAllowed = staticCompositionLocalOf { true }

internal fun fileCreationAllowed(android: Boolean, windows: Boolean, path: String): Boolean =
    !android && !(windows && iecxiv.meshfabric.data.remote.WindowsDriveSupport.selector(path))

private val LocalHideRootFileFab = staticCompositionLocalOf { false }

internal fun hideRootFileFab(android: Boolean, windows: Boolean, path: String): Boolean =
    (android || windows) && iecxiv.meshfabric.data.remote.WindowsDriveSupport.selector(path)
